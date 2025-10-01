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
import org.apache.ivy.plugins.resolver.DependencyResolver
import sbt.Classpaths._
import sbt.Keys._
import sbt.io.syntax.{URI, uri}
import sbt.librarymanagement.Artifact
import sbt.librarymanagement.Http.http
import sbt.plugins.JvmPlugin
import sbt.util.Logger
import sbt.{io => _, _}
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

  val reproducibleBuildsCertification = taskKey[File]("Create a Reproducible Builds certification")
  val reproducibleBuildsCheckCertification = taskKey[Unit]("Download and compare Reproducible Builds certifications")
  @deprecated(
    "Use reproducibleBuildsCheck along with reproducibleBuildsCheckResolver := Resolver.DefaultMavenRepository"
  )
  val reproducibleBuildsCheckMavenCentral =
    taskKey[File]("Compare Reproducible Build certifications against those published on Maven Central")
  val reproducibleBuildsCheck =
    taskKey[File]("Compare Reproducible Build certifications against those published on Maven Central")
  val reproducibleBuildsCheckResolver = taskKey[Resolver](
    "Which repository to check build certifications against. Defaults to publishTo or Maven if its not defined"
  )

  val bzztNetResolver = Resolver.url("repo.bzzt.net", url("https://repo.bzzt.net"))(
    Patterns().withArtifactPatterns(
      Vector(
        // We default to a Maven-style pattern with host and timestamp to reduce naming collisions, and branch if populated
        "[organisation]/[module](_[scalaVersion])(_[sbtVersion])/([branch]/)[revision]/[artifact]-[revision](-[classifier])(-[host])(-[timestamp]).[ext]"
      )
    )
  )

  lazy val ourCertification = Def.task[Certification] {
    Certification(
      organization.value,
      reproducibleBuildsPackageName.value,
      version.value,
      scmInfo.value,
      (packagedArtifacts in Compile).value,
      (libraryDependencies in Compile).value,
      (scalaVersion in artifactName).value,
      (scalaBinaryVersion in artifactName).value,
      sbtVersion.value
    )
  }

  lazy val ourCertificationFile = Def.task[File] {
    val certification = ourCertification.value

    val targetDirPath = crossTarget.value
    val targetFilePath = targetDirPath.toPath.resolve(
      targetFilename(certification.artifactId, certification.version, certification.classifier)
    )

    Files.write(targetFilePath, certification.asPropertyString.getBytes(Charset.forName("UTF-8")))

    targetFilePath.toFile
  }

  def substitutePattern(pattern: String, ext: String) = Def.task {
    import scala.collection.JavaConverters._
    val extraModuleAttributes = {
      (if (crossPaths.value) Map("scalaVersion" -> scalaBinaryVersion.value) else Map.empty) ++
        (if (sbtPlugin.value) Map("sbtVersion" -> sbtBinaryVersion.value) else Map.empty)
    }.asJava

    IvyPatternHelper.substitute(
      pattern,
      organization.value.replace('.', '/'),
      reproducibleBuildsPackageName.value,
      version.value,
      reproducibleBuildsPackageName.value,
      ext,
      ext,
      Compile.name,
      extraModuleAttributes,
      null
    )
  }

  def locationFromResolver(r: DependencyResolver, ext: String) = Def.task {
    import SbtLibraryManagementFunctions._
    val moduleInfo = (moduleSettings.value match {
      case ic: InlineConfiguration =>
        val icWithCross = substituteCross(ic)
        if (sbtPlugin.value) appendSbtCrossVersion(icWithCross)
        else icWithCross
    }).moduleInfo

    val artifactOrigin = r.locate(
      toIvyArtifact(
        newConfiguredModuleID(moduleID.value, moduleInfo, Vector(Compile)),
        artifact.value.withExtension(ext),
        Vector(Compile)
      )
    )
    artifactOrigin.getLocation
  }

  def artifactUrl(resolver: Resolver, ext: String) = Def.taskDyn {
    import SbtLibraryManagementFunctions._

    resolver match {
      case repository: MavenRepository =>
        val pattern = resolvePattern(
          repository.root,
          "[organisation]/[module](_[scalaVersion])(_[sbtVersion])/[revision]/[artifact](_[scalaVersion])(_[sbtVersion])-[revision](-[classifier]).[ext]"
        )
        substitutePattern(pattern, ext)
      case repository: PatternsBasedRepository =>
        val pattern = repository.patterns.artifactPatterns.headOption
          .orElse(
            repository.patterns.ivyPatterns.headOption
          )
          .getOrElse(
            throw new IllegalArgumentException("Expected at least a single artifact pattern")
          )
        substitutePattern(pattern, ext)
      case _: ChainedResolver =>
        throw new IllegalArgumentException("Not yet implemented")

      // TODO: The case of having a RawRepository needs to be tested
      case repository: RawRepository =>
        repository.resolver match {
          case r: DependencyResolver => locationFromResolver(r, ext)
        }
    }
  }

  override lazy val buildSettings = Seq(
    reproducibleBuildsCheckResolver := publishTo.value.getOrElse(Resolver.DefaultMavenRepository)
  )

  override lazy val projectSettings = Seq(
    publishCertification := true,
    hostname := InetAddress.getLocalHost.getHostName,
    packageBin in Compile := postProcessJar((packageBin in Compile).value),
    reproducibleBuildsPackageName := moduleName.value,
    reproducibleBuildsCertification := ourCertificationFile.value,
    artifact in ReproducibleBuilds := {
      val name =
        if (sbtPlugin.value)
          s"${reproducibleBuildsPackageName.value}_${scalaBinaryVersion.value}_${sbtBinaryVersion.value}"
        else
          reproducibleBuildsPackageName.value
      Artifact(name, "buildinfo", "buildinfo")
    },
    packagedArtifacts ++= {
      val generatedArtifact = Map(
        (artifact in ReproducibleBuilds).value -> ourCertificationFile.value
      )

      if (publishCertification.value) generatedArtifact else Map.empty[Artifact, File]
    },
    reproducibleBuildsCheck := Def
      .taskDyn(
        reproducibleBuildsCheckImpl(
          reproducibleBuildsCheckResolver.value
        )
      )
      .value,
    reproducibleBuildsCheckMavenCentral := Def
      .taskDyn(
        reproducibleBuildsCheckImpl(
          Resolver.DefaultMavenRepository
        )
      )
      .value,
    reproducibleBuildsCheckCertification := {
      val ours = ourCertification.value
      organization.value
      val pTo = (ReproducibleBuilds / publishTo).value.getOrElse(bzztNetResolver)
      Def.task {
        val prefix = artifactUrl(pTo, "buildinfo").value
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
  ) ++ (
    if (universalPluginOnClasspath) SbtNativePackagerHelpers.settings
    else Seq.empty
  ) ++ inConfig(ReproducibleBuilds)(
    Seq(
      packagedArtifacts := {
        val compiledArtifacts = (packagedArtifacts in Compile).value
        val generatedArtifact = Map((artifact in ReproducibleBuilds).value -> reproducibleBuildsCertification.value)

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
      },
      publishTo := Some(bzztNetResolver)
    ) ++ gpgPluginSettings ++ Seq(
      publishConfiguration :=
        publishConfig(
          // avoid uploading an ivy-[version].xml
          publishMavenStyle = true,
          deliverPattern(crossTarget.value),
          if (isSnapshot.value) "integration" else "release",
          ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
          packagedArtifacts.value.toVector,
          checksums.value.toVector, { // resolvername: not required if publishTo is false
            val publishToOption = publishTo.value
            if (publishArtifact.value) getPublishTo(publishToOption).name else "local"
          },
          ivyLoggingLevel.value,
          isSnapshot.value
        ),
      publishLocalConfiguration := publishConfig(
        // avoid overwriting an ivy-[version].xml
        publishMavenStyle = true,
        deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        packagedArtifacts.value.toVector,
        checksums.value.toVector,
        logging = ivyLoggingLevel.value,
        overwrite = isSnapshot.value
      ),
      publishM2Configuration := publishConfig(
        publishMavenStyle = true,
        deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        packagedArtifacts.value.toVector,
        checksums = checksums.value.toVector,
        resolverName = Resolver.publishMavenLocal.name,
        logging = ivyLoggingLevel.value,
        overwrite = isSnapshot.value
      ),
      publish := publishTask(publishConfiguration).value,
      publishLocal := publishTask(publishLocalConfiguration).value,
      publishM2 := publishTask(publishM2Configuration).value
    )
  )

  private def showResult(log: Logger, result: VerificationResult): Unit =
    log.info(result.asMarkdown)

  private def gpgPluginSettings =
    if (gpgPluginOnClasspath) GpgHelpers.settings
    else Seq.empty

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

  private def reproducibleBuildsCheckImpl(resolver: Resolver) = Def.task {
    val ourArtifacts = (packagedArtifacts in Compile).value
    val url = artifactUrl(resolver, "buildinfo").value

    val log = streams.value.log
    log.info(s"Downloading certification from [$url]")
    val targetDirPath = crossTarget.value

    val report: Future[String] =
      checkArtifactChecksums(ourCertification.value, uri(url), artifactUrl(resolver, "").value)
        .flatMap { result =>
          showResult(log, result)
          Future
            .sequence {
              result.verdicts
                .collect { case (filename: String, _: Mismatch) =>
                  val ext = filename.substring(filename.lastIndexOf('.') + 1)
                  val mavenArtifactUrl = artifactUrl(resolver, "").value + ext

                  val artifactName = mavenArtifactUrl.substring(mavenArtifactUrl.lastIndexOf('/') + 1)

                  val ourArtifact = ourArtifacts.collect {
                    case (art, file) if art.`type` == ext => file
                  }.toList match {
                    case List() =>
                      throw new IllegalStateException(s"Did not find local artifact for $artifactName ($ext)")
                    case List(artifact) => artifact
                    case rest           =>
                      throw new IllegalStateException(
                        s"Found multiple artifacts for $ext with filenames ${rest.map(_.getName).mkString(", ")}"
                      )
                  }

                  http
                    .run(GigahorseSupport.url(mavenArtifactUrl))
                    .map { entity =>
                      val downloadedArtifactsPath = targetDirPath.toPath.resolve("downloadedArtifact")
                      downloadedArtifactsPath.toFile.mkdirs()
                      val downloadedArtifact = downloadedArtifactsPath.resolve(artifactName)
                      Files.write(
                        downloadedArtifact,
                        entity.bodyAsByteBuffer.array()
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
                    }
                    .recover {
                      case s: StatusError if s.status == 404 =>
                        s"Unfortunately no artifact was found at $mavenArtifactUrl to diff against."
                    }
                }
            }
            .map(verdicts => result.asMarkdown + "\n\n" + verdicts.mkString("", "\n\n", "\n\n"))
        }

    val targetFilePath = targetDirPath.toPath.resolve("reproducible-builds-report.md")

    Files.write(targetFilePath, Await.result(report, 50.minutes).getBytes(Charset.forName("UTF-8")))

    targetFilePath.toFile
  }

  private def checkArtifactChecksums(ours: Certification,
                                     uri: URI,
                                     mavenArtifactPrefix: String
  ): Future[VerificationResult] = {
    val theirSums: Seq[Future[Option[Checksum]]] = ours.checksums.map { ourSum =>
      val filename = ourSum.filename
      val ext = filename.substring(filename.lastIndexOf('.') + 1)
      val mavenArtifactUrl = mavenArtifactPrefix + ext
      http
        .run(GigahorseSupport.url(mavenArtifactUrl))
        .map { entity =>
          import java.security.MessageDigest
          val buffer = entity.bodyAsByteBuffer
          val bytes = new Array[Byte](buffer.remaining())
          buffer.get(bytes)
          val digest = MessageDigest.getInstance("SHA-512")
          Some(new Checksum(filename, bytes.length, digest.digest(bytes).toList))
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
        VerificationResult(uri, ourSums, theirs.checksums)
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
