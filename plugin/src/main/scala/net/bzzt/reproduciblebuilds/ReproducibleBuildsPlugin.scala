/*
 * Copyright 2017 Arnout Engelen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bzzt.reproduciblebuilds

import gigahorse.{GigahorseSupport, StatusError}
import io.github.zlika.reproducible._
import org.apache.ivy.core.IvyPatternHelper
import sbt.Classpaths._
import sbt.Keys._
import sbt.io.syntax.{URI, uri}
import sbt.librarymanagement.Artifact
import sbt.librarymanagement.Http.http
import sbt.plugins.JvmPlugin
import sbt.util.Logger
import sbt.{io => _, _}
import sbtcompat.PluginCompat.{toFile, toFileRef, DefOps, FileRef}
import spray.json._

import java.net.InetAddress
import java.nio.charset.Charset
import java.nio.file.Files

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  val universalPluginOnClasspath =
    Try(getClass.getClassLoader.loadClass("com.typesafe.sbt.packager.universal.UniversalPlugin")).isSuccess

  val gpgPluginOnClasspath =
    Try(getClass.getClassLoader.loadClass("io.crashbox.gpg.SbtGpg")).isSuccess

  override def requires: Plugins = JvmPlugin

  val ReproducibleBuilds = config("reproducibleBuilds")

  val reproducibleBuildsPackageName = settingKey[String]("Module name of this build")
  val publishCertification = settingKey[Boolean]("Include the certification when publishing")
  val hostname = settingKey[String]("The hostname to include when publishing 3rd-party attestations")

  val reproducibleBuildsCertification = taskKey[FileRef]("Create a Reproducible Builds certification")
  val reproducibleBuildsCheckCertification = taskKey[Unit]("Download and compare Reproducible Builds certifications")
  @deprecated(
    "Use reproducibleBuildsCheck along with reproducibleBuildsCheckResolver := Resolver.DefaultMavenRepository"
  )
  val reproducibleBuildsCheckMavenCentral =
    taskKey[FileRef]("Compare Reproducible Build certifications against those published on Maven Central")
  val reproducibleBuildsCheck =
    taskKey[FileRef]("Compare Reproducible Build certifications against those published on Maven Central")
  val reproducibleBuildsCheckResolver = taskKey[Resolver](
    "Which repository to check build certifications against. Defaults to publishTo or Maven if its not defined"
  )

  @transient
  lazy val ourCertification = Def.task[Certification] {
    Certification(
      organization.value,
      reproducibleBuildsPackageName.value,
      version.value,
      scmInfo.value,
      (Compile / packagedArtifacts).value,
      (Compile / libraryDependencies).value,
      (artifactName / scalaVersion).value,
      (artifactName / scalaBinaryVersion).value,
      sbtVersion.value
    )
  }

  lazy val ourCertificationFile = Def.task[FileRef] {
    val certification = ourCertification.value

    val targetDirPath = crossTarget.value
    val targetFilePath = targetDirPath.toPath.resolve(
      targetFilename(certification.artifactId, certification.version, certification.classifier)
    )

    Files.write(targetFilePath, certification.asPropertyString.getBytes(Charset.forName("UTF-8")))

    implicit val conv: xsbti.FileConverter = fileConverter.value
    toFileRef(targetFilePath.toFile)
  }

  case class SubstituteInfo(
      organization: String,
      packageName: String,
      version: String,
      crossPaths: Boolean,
      sbtPlugin: Boolean,
      scalaBinaryVersion: String,
      sbtVersion: String
  )
  lazy val substituteInfo = Def.task[SubstituteInfo] {
    SubstituteInfo(
      organization.value.replace('.', '/'),
      reproducibleBuildsPackageName.value,
      version.value,
      crossPaths.value,
      sbtPlugin.value,
      scalaBinaryVersion.value,
      sbtBinaryVersion.value
    )
  }

  def substitutePattern(pattern: String, ext: String, classifier: Option[String], info: SubstituteInfo) = {
    import scala.collection.JavaConverters._
    val extraModuleAttributes = {
      (if (info.crossPaths) Map("scalaVersion" -> info.scalaBinaryVersion) else Map.empty) ++
        (if (info.sbtPlugin) Map("sbtVersion" -> info.sbtVersion) else Map.empty) ++
        (classifier.map(c => ("classifier", c)).toMap)
    }.asJava

    IvyPatternHelper.substitute(
      pattern,
      info.organization,
      info.packageName,
      info.version,
      info.packageName,
      ext,
      ext,
      Compile.name,
      extraModuleAttributes,
      null
    )
  }

  def artifactUrl(resolver: Resolver, ext: String, classifier: Option[String], info: SubstituteInfo): String = {
    import SbtLibraryManagementFunctions._

    resolver match {
      case repository: MavenRepository =>
        val pattern = resolvePattern(
          repository.root,
          "[organisation]/[module](_[scalaVersion])(_[sbtVersion])/[revision]/[artifact](_[scalaVersion])(_[sbtVersion])-[revision](-[classifier]).[ext]"
        )
        substitutePattern(pattern, ext, classifier, info)
      case repository: PatternsBasedRepository =>
        val pattern = repository.patterns.artifactPatterns.headOption
          .orElse(
            repository.patterns.ivyPatterns.headOption
          )
          .getOrElse(
            throw new IllegalArgumentException("Expected at least a single artifact pattern")
          )
        substitutePattern(pattern, ext, classifier, info)
      case _: ChainedResolver =>
        throw new IllegalArgumentException("Not yet implemented")

      // TODO: we don't have support for `RawRepository`, that would
      // make things more difficult
    }
  }

  override lazy val buildSettings = Seq(
    reproducibleBuildsCheckResolver := Def.uncached {
      publishTo.value.getOrElse(Resolver.DefaultMavenRepository)
    }
  )

  override lazy val projectSettings = Seq(
    publishCertification := true,
    hostname := InetAddress.getLocalHost.getHostName,
    Compile / packageBin := Def.uncached {
      implicit val conv: xsbti.FileConverter = fileConverter.value
      toFileRef(postProcessJar(toFile((Compile / packageBin).value)))
    },
    reproducibleBuildsPackageName := moduleName.value,
    reproducibleBuildsCertification := Def.uncached(ourCertificationFile.value),
    ReproducibleBuilds / artifact := {
      val name =
        if (sbtPlugin.value)
          s"${reproducibleBuildsPackageName.value}_${scalaBinaryVersion.value}_${sbtBinaryVersion.value}"
        else
          reproducibleBuildsPackageName.value
      Artifact(name, "buildinfo", "buildinfo")
    },
    packagedArtifacts ++= Def.uncached {
      val generatedArtifact = Map(
        (ReproducibleBuilds / artifact).value -> ourCertificationFile.value
      )

      if (publishCertification.value) generatedArtifact else Map.empty[Artifact, FileRef]
    },
    reproducibleBuildsCheck := Def.uncached {
      Def
        .taskDyn(
          reproducibleBuildsCheckImpl(
            reproducibleBuildsCheckResolver.value,
            substituteInfo.value
          )
        )
        .value
    },
    reproducibleBuildsCheckMavenCentral := Def.uncached {
      Def
        .taskDyn(
          reproducibleBuildsCheckImpl(
            Resolver.DefaultMavenRepository,
            substituteInfo.value
          )
        )
        .value
    },
    reproducibleBuildsCheckCertification := Def.uncached {
      val ours = ourCertification.value
      val pTo = (ReproducibleBuilds / publishTo).value.getOrElse(DefaultMavenRepository)
      val info = substituteInfo.value
      Def.task {
        val prefix = artifactUrl(pTo, "buildinfo", None, info)
        val log = streams.value.log
        log.info(s"Discovering certifications at [$prefix]")
        // TODO add Accept header to request JSON-formatted
        val done = http
          .run(GigahorseSupport.url(prefix))
          .flatMap { entity =>
            val results = entity.bodyAsString.parseJson
              .asInstanceOf[JsArray]
              .elements
              .map(_.asInstanceOf[JsObject])
              .map(_.fields.get("name"))
              .collect { case Some(JsString(objectname)) if objectname.endsWith(".buildinfo") => objectname }
              .map(name => checkVerification(ours, uri(prefix).resolve(name)))
            Future.sequence(results)
          }
          .map { resultList =>
            log.info(s"Processed ${resultList.size} results. ${resultList
                .count(_.ok)} matching attestations, ${resultList.filterNot(_.ok).size} mismatches");
            resultList.foreach(result => showResult(log, result))
          }
          .recover {
            case e: StatusError if e.status == 404 =>
              showResult(log, VerificationResult(uri(prefix), ours.checksums, Seq.empty))
          }
        Await.result(done, 6.minutes)
      }

    },
    ivyConfigurations += ReproducibleBuilds
    // ) ++ (
    //  if (universalPluginOnClasspath) SbtNativePackagerHelpers.settings
    //  else Seq.empty
  ) ++ inConfig(ReproducibleBuilds)(
    Seq(
      packagedArtifacts := Def.uncached {
        val compiledArtifacts = (Compile / packagedArtifacts).value
        val generatedArtifact = Map((ReproducibleBuilds / artifact).value -> reproducibleBuildsCertification.value)

        val artifacts =
          if (publishCertification.value)
            compiledArtifacts.filter { case (artifact, _) => artifact.`type` == "buildinfo" }
          else
            generatedArtifact
        artifacts.map { case (key, value) =>
          (key.withExtraAttributes(
             key.extraAttributes ++ Map("host" -> hostname.value,
                                        "timestamp" -> (System.currentTimeMillis() / 1000L).toString
             )
           ),
           value
          )
        }
      }
      // publishTo := Some(bzztNetResolver)
    ) ++ gpgPluginSettings ++ Seq(
      publishConfiguration := Def.uncached {
        implicit val converter = fileConverter.value
        val artifacts = packagedArtifacts.value.toVector.map { case (a, vf) =>
          a -> toFile(vf)
        };
        publishConfig(
          // avoid uploading an ivy-[version].xml
          publishMavenStyle = true,
          deliverPattern(crossTarget.value),
          if (isSnapshot.value) "integration" else "release",
          ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
          artifacts,
          checksums.value.toVector, { // resolvername: not required if publishTo is false
            val publishToOption = publishTo.value
            if (publishArtifact.value) getPublishTo(publishToOption).name else "local"
          },
          ivyLoggingLevel.value,
          isSnapshot.value
        )
      },
      publishLocalConfiguration := Def.uncached {
        implicit val conv = fileConverter.value
        val artifacts = packagedArtifacts.value.toVector.map { case (a, vf) =>
          a -> toFile(vf)
        }
        publishConfig(
          // avoid overwriting an ivy-[version].xml
          publishMavenStyle = true,
          deliverPattern(crossTarget.value),
          if (isSnapshot.value) "integration" else "release",
          ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
          artifacts,
          checksums.value.toVector,
          logging = ivyLoggingLevel.value,
          overwrite = isSnapshot.value
        )
      },
      publishM2Configuration := Def.uncached {
        implicit val conv = fileConverter.value
        val artifacts = packagedArtifacts.value.toVector.map { case (a, vf) =>
          a -> toFile(vf)
        }
        publishConfig(
          publishMavenStyle = true,
          deliverPattern(crossTarget.value),
          if (isSnapshot.value) "integration" else "release",
          ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
          artifacts,
          checksums = checksums.value.toVector,
          resolverName = Resolver.publishMavenLocal.name,
          logging = ivyLoggingLevel.value,
          overwrite = isSnapshot.value
        )
      },
      publish := publishOrSkip(publishConfiguration, publish / skip).value,
      publishLocal := publishOrSkip(publishLocalConfiguration, publishLocal / skip).value,
      publishM2 := publishOrSkip(publishM2Configuration, publishM2 / skip).value
    )
  )

  private def showResult(log: Logger, result: VerificationResult): Unit =
    log.info(result.asMarkdown)

  private def gpgPluginSettings =
    // if (gpgPluginOnClasspath) GpgHelpers.settings
    // else Seq.empty
    Seq.empty

  def postProcessJar(jar: File): File = postProcessWith(
    jar,
    new ZipStripper()
      .addFileStripper("META-INF/MANIFEST.MF", new ManifestStripper())
      .addFileStripper("META-INF/maven/\\S*/pom.properties", new PropertiesFileStripper())
  )

  def postProcessZip(zip: File): File = postProcessWith(zip, new ZipStripper())

  private def postProcessWith(in: File, stripper: Stripper): File = {
    val dir = in.getParentFile.toPath.resolve("stripped")
    dir.toFile.mkdir()
    val out = dir.resolve(in.getName).toFile
    stripper.strip(in, out)
    // Allowed since stripping is idempotent. This way sbt can cache the result better.
    out.setLastModified(in.lastModified);
    out
  }

  private def reproducibleBuildsCheckImpl(resolver: Resolver, info: SubstituteInfo) = Def.task {
    val url = artifactUrl(resolver, "buildinfo", None, info)

    val log = streams.value.log
    log.info(s"Downloading certification from [$url]")
    val targetDirPath = crossTarget.value
    implicit val conv: xsbti.FileConverter = fileConverter.value

    val ourArtifacts = (Compile / packagedArtifacts).value
    val ourArtifactFilesByFilename = ourArtifacts.map { case (_, file) => (file.name, file) }.toMap
    val publishedUrlByFilename = (Compile / packagedArtifacts)
      .map(m => m.keys.map(a => (m(a).name, artifactUrl(resolver, a.extension, a.classifier, info))))
      .value
      .toMap

    val report: Future[String] =
      checkArtifactChecksums(ourCertification.value, uri(url), publishedUrlByFilename)
        .flatMap { result =>
          showResult(log, result)
          Future
            .sequence {
              result.verdicts
                .collect { case (filename: String, mismatch: Mismatch) =>
                  val mavenArtifactUrl = publishedUrlByFilename(filename)

                  val artifactName = mavenArtifactUrl.substring(mavenArtifactUrl.lastIndexOf('/') + 1)

                  val ourArtifact = ourArtifactFilesByFilename.get(filename) match {
                    case None =>
                      throw new IllegalStateException(
                        s"Did not find local artifact for $artifactName ($mavenArtifactUrl)"
                      )
                    case Some(artifactFile) => toFile(artifactFile)
                  }

                  val downloadedArtifactsPath = targetDirPath.toPath.resolve("downloadedArtifact")
                  downloadedArtifactsPath.toFile.mkdirs()
                  val downloadedArtifact = downloadedArtifactsPath.resolve(artifactName)

                  (mismatch.theirBytes match {
                    case Some(savedBytes) =>
                      Future.successful(savedBytes)
                    case None =>
                      http
                        .run(GigahorseSupport.url(mavenArtifactUrl))
                        .map(_.bodyAsByteBuffer.array())
                  }).map { bytes =>
                    Files.write(
                      downloadedArtifact,
                      bytes
                    )

                    val diffoscopeOutputDir =
                      targetDirPath.toPath.resolve(s"reproducible-builds-diffoscope-output-$artifactName")
                    val cmd = s"diffoscope --html-dir $diffoscopeOutputDir $ourArtifact $downloadedArtifact"
                    new ProcessBuilder(
                      "diffoscope",
                      "--html-dir",
                      diffoscopeOutputDir.toFile.getAbsolutePath,
                      ourArtifact.getAbsolutePath,
                      downloadedArtifact.toFile.getAbsolutePath
                    ).start().waitFor()
                    log.info(s"Running '$cmd' for a detailed report on the differences")
                    s"See the [diffoscope report](reproducible-builds-diffoscope-output-$artifactName/index.html) for a detailed explanation " +
                      " of the differences between the freshly built artifact and the one published to Maven Central"
                  }.recover {
                    case s: StatusError if s.status == 404 =>
                      s"Unfortunately no artifact was found at $mavenArtifactUrl to diff against."
                  }
                }
            }
            .map(verdicts => result.asMarkdown + "\n\n" + verdicts.mkString("", "\n\n", "\n\n"))
        }

    val targetFilePath = targetDirPath.toPath.resolve("reproducible-builds-report.md")

    Files.write(targetFilePath, Await.result(report, 50.minutes).getBytes(Charset.forName("UTF-8")))

    toFileRef(targetFilePath.toFile)
  }

  private def fetchFileOrUrl(filenameOrUrl: String): Future[Array[Byte]] =
    if (filenameOrUrl.head == '/') {
      Future.successful(Files.readAllBytes(java.nio.file.Paths.get(filenameOrUrl)))
    } else if (filenameOrUrl.startsWith("file:/")) {
      Future.successful(Files.readAllBytes(java.nio.file.Paths.get(filenameOrUrl.drop(5))))
    } else {
      http
        .run(GigahorseSupport.url(filenameOrUrl))
        .map { entity =>
          val buffer = entity.bodyAsByteBuffer
          val bytes = new Array[Byte](buffer.remaining())
          buffer.get(bytes)
          bytes
        }
    }

  private def checkArtifactChecksums(ours: Certification,
                                     uri: URI,
                                     publishedArtifactUrlByFilename: Map[String, String]
  ): Future[VerificationResult] = {
    val theirSums: Seq[Future[Option[(Checksum, Option[Array[Byte]])]]] = ours.checksums.map { ourSum =>
      val filename = ourSum.filename
      val publishedArtifactUrl = publishedArtifactUrlByFilename(filename)
      fetchFileOrUrl(publishedArtifactUrl)
        .map { bytes =>
          import java.security.MessageDigest
          val digest = MessageDigest.getInstance("SHA-512")
          Some((Checksum(filename, bytes.length, digest.digest(bytes)), Some(bytes)))
        }
        .recover {
          case s: StatusError if s.status == 404 =>
            None
        }
    }
    Future.sequence(theirSums).map(s => VerificationResult(uri, ours.checksums, s.flatten))
  }

  private def checkVerification(ours: Certification, uri: URI): Future[VerificationResult] = {
    val ourSums = ours.checksums

    http
      .run(GigahorseSupport.url(uri.toASCIIString))
      .map { entity =>
        val theirs = Certification(entity.bodyAsString)
        VerificationResult(uri, ourSums, theirs.checksums.map(c => (c, None)))
      }
      .recover {
        case e: StatusError if e.status == 404 => VerificationResult(uri, ourSums, Seq.empty)
      }
  }

  /** Determine the target filename.
    *
    * See https://wiki.debian.org/ReproducibleBuilds/BuildinfoFiles#File_name_and_encoding
    * @return
    */
  def targetFilename(source: String, version: String, architecture: Option[String], suffix: Option[String] = None) =
    source + "-" + version + architecture.map("_" + _).getOrElse("") + suffix.map("_" + _).getOrElse("") + ".buildinfo"
}
