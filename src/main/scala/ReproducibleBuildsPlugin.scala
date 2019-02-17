package net.bzzt.reproduciblebuilds

import java.net.InetAddress
import java.nio.charset.Charset
import java.nio.file.Files

import scala.concurrent.duration._
import gigahorse.GigahorseSupport
import sbt.{io => _, _}
import sbt.Keys._
import sbt.Classpaths._
import sbt.plugins.JvmPlugin
import io.github.zlika.reproducible._
import org.apache.ivy.core.IvyPatternHelper
import sbt.io.syntax.{URI, uri}
import sbt.librarymanagement.{Artifact, URLRepository}
import sbt.librarymanagement.Http.http

import scala.util.{Success, Try}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  val universalPluginOnClasspath =
    Try(getClass.getClassLoader.loadClass("com.typesafe.sbt.packager.universal.UniversalPlugin")).isSuccess

  val gpgPluginOnClasspath =
    Try(getClass.getClassLoader.loadClass("io.crashbox.gpg.SbtGpg")).isSuccess

  override def requires: Plugins = JvmPlugin

  val ReproducibleBuilds = config("reproducible-builds")

  val reproducibleBuildsPackageName = settingKey[String]("Module name of this build")
  val publishCertification = settingKey[Boolean]("Include the certification when publishing")
  val hostname = settingKey[String]("The hostname to include when publishing 3rd-party attestations")

  val reproducibleBuildsCertification = taskKey[File]("Create a Reproducible Builds certification")
  val reproducibleBuildsCheckCertification = taskKey[Unit]("Download and compare Reproducible Builds certifications")

  val bzztNetResolver = Resolver.url("repo.bzzt.net", url("http://repo.bzzt.net:8000"))(Patterns().withArtifactPatterns(Vector(
    // We default to a Maven-style pattern with host and timestamp to reduce naming collisions, and branch if populated
    "[organisation]/[module](_[scalaVersion])(_[sbtVersion])/([branch]/)[revision]/[artifact]-[revision](-[classifier])(-[host])(-[timestamp]).[ext]"
  )))

  override lazy val projectSettings = Seq(
    publishCertification := true,
    hostname := InetAddress.getLocalHost.getHostName,
    resolvers += bzztNetResolver,
    packageBin in Compile := postProcessJar((packageBin in Compile).value),
    reproducibleBuildsPackageName := moduleName.value,
    reproducibleBuildsCertification := {
      val certification = Certification(
        organization.value,
        reproducibleBuildsPackageName.value,
        version.value,
        scmInfo.value,
        (packagedArtifacts in Compile).value,
        (scalaVersion in artifactName).value,
        (scalaBinaryVersion in artifactName).value,
        sbtVersion.value
      )

      val targetDirPath = crossTarget.value
      val targetFilePath = targetDirPath.toPath.resolve(targetFilename(certification.artifactId, certification.version, certification.classifier))

      Files.write(targetFilePath, certification.asPropertyString.getBytes(Charset.forName("UTF-8")))

      targetFilePath.toFile
    },
    artifact in ReproducibleBuilds := Artifact(reproducibleBuildsPackageName.value, "buildinfo", "buildinfo"),
    packagedArtifacts ++= {
      val generatedArtifact = Map(
        (artifact in ReproducibleBuilds).value ->
        {
          val certification = Certification(
            organization.value,
            reproducibleBuildsPackageName.value,
            version.value,
            scmInfo.value,
            (packagedArtifacts in Compile).value,
            (scalaVersion in artifactName).value,
            (scalaBinaryVersion in artifactName).value,
            sbtVersion.value
          )

          val targetDirPath = crossTarget.value
          val targetFilePath = targetDirPath.toPath.resolve(targetFilename(certification.artifactId, certification.version, certification.classifier))

          Files.write(targetFilePath, certification.asPropertyString.getBytes(Charset.forName("UTF-8")))

          targetFilePath.toFile
        }
      )

      if (publishCertification.value) generatedArtifact else Map.empty[Artifact, File]
    },
    reproducibleBuildsCheckCertification := {
      val ours = Certification(
        organization.value,
        reproducibleBuildsPackageName.value,
        version.value,
        scmInfo.value,
        (packagedArtifacts in Compile).value,
        (scalaVersion in artifactName).value,
        (scalaBinaryVersion in artifactName).value,
        sbtVersion.value
      )
      val groupId = organization.value
      // TODO also check against the 'official' published buildinfo
      val pattern = (publishTo in ReproducibleBuilds).value.getOrElse(bzztNetResolver).asInstanceOf[URLRepository].patterns.artifactPatterns.head
      val prefixPattern = pattern.substring(0, pattern.lastIndexOf("/") + 1)
      import scala.collection.JavaConverters._
      val extraModuleAttributes = {
        val scalaVer = Map("scalaVersion" -> scalaBinaryVersion.value)
        if (sbtPlugin.value) scalaVer + ("sbtVersion" -> sbtBinaryVersion.value)
        else scalaVer
      }.asJava

      val prefix = IvyPatternHelper.substitute(
        prefixPattern,
        organization.value.replace('.', '/'),
        reproducibleBuildsPackageName.value,
        version.value,
        reproducibleBuildsPackageName.value,
        "buildinfo",
        "buildinfo",
        "compile",
        extraModuleAttributes,
        null
      )
      val log = streams.value.log
      log.info(s"Discovering certifications at [$prefix]")
      // TODO add Accept header to request JSON-formatted
      val done = http.run(GigahorseSupport.url(prefix)).flatMap { entity =>
          val results = entity.bodyAsString
            .parseJson
            .asInstanceOf[JsArray]
            .elements
            .map(_.asInstanceOf[JsObject])
            .map(_.fields.get("name"))
            .collect { case Some(JsString(objectname)) if objectname.endsWith(".buildinfo") => objectname }
            .map(name => checkVerification(ours, uri(prefix).resolve(name)))
          Future.sequence(results)
      }.map { resultList =>
        log.info(s"Processed ${resultList.size} results. ${resultList.count(_.ok)} matching attestations, ${resultList.filterNot(_.ok).size} mismatches");
        resultList.foreach { result =>
          log.info(s"${result.uri}:")
          log.info("- " + (if (result.ok) "OK" else "NOT OK"))
          result.verdicts.foreach {
            case (filename, verdict) => log.info(s"- $filename: $verdict")
          }
        }
      }
      Await.result(done, 30.seconds)
    },
    ivyConfigurations += ReproducibleBuilds,
  ) ++ (
    if (universalPluginOnClasspath) SbtNativePackagerHelpers.settings
    else Seq.empty
  ) ++ inConfig(ReproducibleBuilds)(Seq(
    packagedArtifacts := {
      val compiledArtifacts = (packagedArtifacts in Compile).value
      val generatedArtifact = Map((artifact in ReproducibleBuilds).value -> reproducibleBuildsCertification.value)

      val artifacts =
        if (publishCertification.value)
          compiledArtifacts.filter { case (artifact, _) => artifact.`type` == "buildinfo" }
        else
          generatedArtifact
      artifacts.map { case (key, value) => (key.withExtraAttributes(key.extraAttributes ++ Map("host"->hostname.value, "timestamp" -> (System.currentTimeMillis() / 1000l).toString)), value) }
    },
    publishTo := Some(bzztNetResolver)
  ) ++ gpgPluginSettings ++ Seq(
    publishConfiguration := {
      publishConfig(
        // avoid uploading an ivy-[version].xml
        publishMavenStyle = true,
        deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        packagedArtifacts.value.toVector,
        checksums.value.toVector, { //resolvername: not required if publishTo is false
          val publishToOption = publishTo.value
          if (publishArtifact.value) getPublishTo(publishToOption).name else "local"
        },
        ivyLoggingLevel.value,
        isSnapshot.value
      )
    },
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
  ))

  private def gpgPluginSettings =
    if (gpgPluginOnClasspath) GpgHelpers.settings
    else Seq.empty

  def postProcessJar(jar: File): File = postProcessWith(jar, new ZipStripper()
      .addFileStripper("META-INF/MANIFEST.MF", new ManifestStripper())
      .addFileStripper("META-INF/maven/\\S*/pom.properties", new PomPropertiesStripper()))

  def postProcessZip(zip: File): File = postProcessWith(zip, new ZipStripper())

  private def postProcessWith(file: File, stripper: Stripper): File = {
    val dir = file.getParentFile.toPath.resolve("stripped")
    dir.toFile.mkdir()
    val out = dir.resolve(file.getName).toFile
    stripper.strip(file, out)
    out
  }

  private def checkVerification(ours: Certification, uri: URI): Future[VerificationResult] = {
      val ourSums = ours.checksums

      http.run(GigahorseSupport.url(uri.toASCIIString)).map { entity =>
        val theirs = Certification(entity.bodyAsString)
        VerificationResult(uri, ourSums, theirs.checksums)
      }
    }

  /**
    * Determine the target filename.
    *
    * See https://wiki.debian.org/ReproducibleBuilds/BuildinfoFiles#File_name_and_encoding
    * @return
    */
  def targetFilename(source: String, version: String, architecture: Option[String], suffix: Option[String] = None) =
    source + "-" + version + architecture.map("_" + _).getOrElse("") + suffix.map("_" + _).getOrElse("") + ".buildinfo"
}
