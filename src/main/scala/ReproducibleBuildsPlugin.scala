package net.bzzt.reproduciblebuilds

import java.net.InetAddress
import java.nio.charset.Charset
import java.nio.file.Files

import gigahorse.GigahorseSupport
import sbt.{io => _, _}
import sbt.Keys._
import sbt.Classpaths._
import sbt.plugins.JvmPlugin
import com.typesafe.sbt.pgp
import com.typesafe.sbt.pgp.PgpSigner
import com.typesafe.sbt.pgp.PgpKeys._
import com.typesafe.sbt.pgp.PgpSettings.{pgpPassphrase => _, pgpSecretRing => _, pgpSigningKey => _, useGpgAgent => _, _}
import io.github.zlika.reproducible._
import sbt.io.syntax.{URI, uri}
import sbt.librarymanagement.{Artifact, URLRepository}
import sbt.librarymanagement.Http.http

import scala.util.{Success, Try}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  val universalPluginOnClasspath =
    Try(getClass.getClassLoader.loadClass("com.typesafe.sbt.packager.universal.UniversalPlugin")).isSuccess

  override def requires: Plugins = JvmPlugin

  val ReproducibleBuilds = config("reproducible-builds")

  val reproducibleBuildsPackageName = settingKey[String]("Package name of this build, including version")
  val publishCertification = settingKey[Boolean]("Include the certification when publishing")
  val hostname = settingKey[String]("The hostname to include when publishing 3rd-party attestations")

  val reproducibleBuildsCertification = taskKey[File]("Create a Reproducible Builds certification")
  val signedReproducibleBuildsCertification = taskKey[File]("Create a signed Reproducible Builds certification")
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
    signedReproducibleBuildsCertification := {
      val file = reproducibleBuildsCertification.value
      val signer = new CleartextCommandLineGpgSigner(gpgCommand.value, useGpgAgent.value, pgpSigningKey.value, pgpPassphrase.value)
      signer.sign(file, new File(file.getAbsolutePath + pgp.gpgExtension), streams.value)
    },
    reproducibleBuildsCheckCertification := {
      val ours = Certification(
        organization.value,
        reproducibleBuildsPackageName.value,
        version.value,
        (packagedArtifacts in Compile).value,
        (scalaVersion in artifactName).value,
        (scalaBinaryVersion in artifactName).value,
        sbtVersion.value
      )
      val groupId = organization.value
      // TODO resolve placeholders and trim file segment
      val uploadPrefix = url((publishTo in ReproducibleBuilds).value.asInstanceOf[URLRepository].patterns.artifactPatterns.head).toURI
      val uri = uploadPrefix.resolve(groupId + "/" + reproducibleBuildsPackageName.value + "/" + version.value + "/")
      http.run(GigahorseSupport.url(uri.toASCIIString)).onComplete {
        case Success(v) =>
          v.bodyAsString
            .parseJson
            .asInstanceOf[JsArray]
            .elements
            .map(_.asInstanceOf[JsObject])
            .filter(_.fields("type").asInstanceOf[JsString].value == "file")
            .map(_.fields("name").asInstanceOf[JsString].value)
            .foreach(name => {
              checkVerification(ours, uploadPrefix.resolve(name))
            })
      }
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
    publishTo := Some(bzztNetResolver),
    publishConfiguration := {
      publishConfig(
        publishMavenStyle.value,
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
      false, //publishMavenStyle.value,
      deliverPattern(crossTarget.value),
      if (isSnapshot.value) "integration" else "release",
      ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
      packagedArtifacts.value.toVector,
      checksums.value.toVector,
      logging = ivyLoggingLevel.value,
      overwrite = isSnapshot.value
    ),
    publishM2Configuration := publishConfig(
      true,
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

  private def checkVerification(ours: Certification, uri: URI): Unit = {
      import scala.collection.JavaConverters._

      val ourSums = ours.checksums

      println("Checking remote builds:")

      http.run(GigahorseSupport.url(uri.toASCIIString)).onComplete {
        case Success(v) =>
          println(s"Comparing against $uri (warning: signature not checked):")
          val theirPropertyString = v.bodyAsString
          val theirs = Certification(theirPropertyString)
          val remoteSums = theirs.checksums
          ourSums.foreach { ourSum =>
            if (remoteSums.contains(ourSum)) {
              println(s"Match: $ourSum")
            } else {
              println(s"Mismatch: our $ourSum not found in $remoteSums")
            }
          }
      }
    }

  /**
    *  A GpgSigner that uses the command-line to run gpg.
    *
    * Taken from sbt-pgp, but:
    * * changed '--detach-sign' to '--clearsign'
    *   (needs to be '--clearsign' rather than '--clear-sign' to support gnupg 1.4.x)
    * * removed 'secRing' (see https://github.com/sbt/sbt-pgp/issues/126)
    */
  private class CleartextCommandLineGpgSigner(command: String, agent: Boolean, optKey: Option[Long], optPassphrase: Option[Array[Char]]) extends PgpSigner {
    def sign(file: File, signatureFile: File, s: TaskStreams): File = {
      if (signatureFile.exists) IO.delete(signatureFile)
      val passargs: Seq[String] = (optPassphrase map { passArray => passArray mkString "" } map { pass => Seq("--passphrase", pass) }) getOrElse Seq.empty
      val keyargs: Seq[String] = optKey map (k => Seq("--default-key", "0x%x" format (k))) getOrElse Seq.empty
      val args = passargs ++ Seq("--clearsign", "--armor") ++ (if (agent) Seq("--use-agent") else Seq.empty) ++ keyargs
      sys.process.Process(command, args ++ Seq("--output", signatureFile.getAbsolutePath, file.getAbsolutePath)) !< match {
        case 0 => ()
        case n => sys.error("Failure running gpg --clearsign.  Exit code: " + n)
      }
      signatureFile
    }
    override val toString: String = "RB GPG-Command(" + command + ")"
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
