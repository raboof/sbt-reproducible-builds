package net.bzzt.reproduciblebuilds

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

import gigahorse.GigahorseSupport
import sbt.Defaults._
import sbt.{AutoPlugin, Compile, File, IO, Plugins, taskKey}
import sbt.Keys.{scalaVersion, _}
import sbt.plugins.JvmPlugin
import com.typesafe.sbt.pgp
import com.typesafe.sbt.pgp.PgpSigner
import com.typesafe.sbt.pgp.PgpKeys._
import com.typesafe.sbt.pgp.PgpSettings.{pgpPassphrase => _, pgpSecretRing => _, pgpSigningKey => _, useGpgAgent => _, _}
import io.github.zlika.reproducible._
import sbt.io.syntax.{URI, uri}
import sbt.librarymanagement.Http.http

import scala.util.Success
import spray.json._
import scala.concurrent.ExecutionContext.Implicits.global

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  override def requires: Plugins = JvmPlugin

  val reproducibleBuildsPackageName = taskKey[String]("Package name of this build, including version but excluding disambiguation string")
  val reproducibleBuildsCertification = taskKey[File]("Create a Reproducible Builds certification")
  val reproducibleBuildsUploadPrefix = taskKey[URI]("Base URL to send uploads to")
  val signedReproducibleBuildsCertification = taskKey[File]("Create a signed Reproducible Builds certification")
  val reproducibleBuildsUploadCertification = taskKey[Unit]("Upload the Reproducible Builds certification")
  val reproducibleBuildsCheckCertification = taskKey[Unit]("Download and compare Reproducible Builds certifications")

  val disambiguation = taskKey[Iterable[File] => Option[String]]("Generator for optional discriminator string")

  override lazy val projectSettings = Seq(
    reproducibleBuildsUploadPrefix := uri("http://localhost:8000/"),
    disambiguation in Compile := ((packagedFiles: Iterable[File]) =>
      Some(sys.env.get("USER").orElse(sys.env.get("USERNAME")).map(_ + "-").getOrElse("") + packagedFiles.map(_.lastModified()).max)
    ),
    packageBin in Compile := {
      val bin = (packageBin in Compile).value
      val dir = bin.getParentFile.toPath.resolve("stripped")
      dir.toFile.mkdir()
      val out = dir.resolve(bin.getName).toFile
      new ZipStripper()
        .addFileStripper("META-INF/MANIFEST.MF", new ManifestStripper())
        .addFileStripper("META-INF/maven/\\S*/pom.properties", new PomPropertiesStripper())
        .strip(bin, out)
      out
    },
    artifactPath in reproducibleBuildsCertification := artifactPathSetting(artifact in reproducibleBuildsCertification).value,
    reproducibleBuildsPackageName := moduleName.value + "_" + scalaBinaryVersion.value,
    reproducibleBuildsCertification := {
      val packageName = reproducibleBuildsPackageName.value

      val targetDirPath = crossTarget.value
      val packageVersion = version.value
      val architecture = "all"

      val artifacts = (packagedArtifacts in Compile).value
        .filter { case (artifact, _) => artifact.`type` == "pom" || artifact.`type` == "jar" }

      val targetFilePath = targetDirPath.toPath.resolve(targetFilename(packageName, packageVersion, architecture, (disambiguation in Compile).value(artifacts.map(_._2))))

      val checksums = artifacts
        .map { case (_, packagedFile) =>
          val bytes = Files.readAllBytes(packagedFile.toPath)
          val digest = MessageDigest.getInstance("SHA-256")
          val checksum = digest.digest(bytes).map("%02x" format _).mkString

          s"$checksum ${bytes.length} ${packagedFile.getName}"
        }.mkString("\n  ", "\n  ", "")

      val content = Map(
        "Format" -> "1.8",
        "Build-Architecture" -> architecture,
        "Source" -> packageName,
        "Binary" -> packageName,
        "Package" -> packageName,
        // Strictly spoken not allowed by https://wiki.debian.org/ReproducibleBuilds/BuildinfoFiles#Field_descriptions,
        // but jars should typically be architecture-independent...
        "Architecture" -> architecture,
        "Version" -> packageVersion,
        "Checksums-Sha256" -> checksums,
        // Extra 'custom' fields:
        "ScalaVersion" -> (scalaVersion in artifactName).value,
        "ScalaBinaryVersion" -> (scalaBinaryVersion in artifactName).value
      )

      import collection.JavaConverters._
      Files.write(targetFilePath, formatControlFile(content).asJava, Charset.forName("UTF-8"))

      targetFilePath.toFile
    },
    signedReproducibleBuildsCertification := {
      val file = reproducibleBuildsCertification.value
      val signer = new CleartextCommandLineGpgSigner(gpgCommand.value, useGpgAgent.value, pgpSigningKey.value, pgpPassphrase.value)
      signer.sign(file, new File(file.getAbsolutePath + pgp.gpgExtension), streams.value)
    },
    reproducibleBuildsUploadCertification := {
      val file = signedReproducibleBuildsCertification.value
      val groupId = organization.value
      val uri = reproducibleBuildsUploadPrefix.value.resolve(groupId + "/" + reproducibleBuildsPackageName.value + "/").resolve(file.getName)

      import gigahorse.HttpWrite._
      http.run(
        GigahorseSupport.url(uri.toASCIIString)
          .withMethod("PUT")
          // TODO content-type
          .withBody(new String(Files.readAllBytes(file.toPath), Charset.forName("UTF-8"))))
    },
    reproducibleBuildsCheckCertification := {
      val ours = reproducibleBuildsCertification.value
      val groupId = organization.value
      val uploadPrefix = reproducibleBuildsUploadPrefix.value
      val uri = uploadPrefix.resolve(groupId + "/" + reproducibleBuildsPackageName.value + "/")
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
    }
  )

  private def checkVerification(ours: File, uri: URI): Unit = {
      import scala.collection.JavaConverters._
      val ourSums = parseChecksums(Files.readAllLines(ours.toPath, Charset.forName("UTF-8")).asScala.toList)

      println("Checking remote builds:")

      http.run(GigahorseSupport.url(uri.toASCIIString)).onComplete {
        case Success(v) =>
          println(s"Comparing against $uri (warning: signature not checked):")
          val remoteSums = parseChecksums(v.bodyAsString.split("\n").toList)
          ourSums.foreach { ourSum =>
            if (remoteSums.contains(ourSum)) {
              println(s"Match: $ourSum")
            } else {
              println(s"Mismatch: our $ourSum not found in $remoteSums")
            }
          }
      }
    }

  private def parseChecksums(lines: List[String]) = {
    lines
      .dropWhile(!_.startsWith("Checksums-Sha256"))
      .drop(1)
      .takeWhile(_.startsWith("  "))
      .map(_.drop(2))
  }

  /**
    *  A GpgSigner that uses the command-line to run gpg.
    *
    * Taken from sbt-pgp, but:
    * * changed '--detach-sign' to '--clear-sign'
    * * removed 'secRing' (see https://github.com/sbt/sbt-pgp/issues/126)
    */
  private class CleartextCommandLineGpgSigner(command: String, agent: Boolean, optKey: Option[Long], optPassphrase: Option[Array[Char]]) extends PgpSigner {
    def sign(file: File, signatureFile: File, s: TaskStreams): File = {
      if (signatureFile.exists) IO.delete(signatureFile)
      val passargs: Seq[String] = (optPassphrase map { passArray => passArray mkString "" } map { pass => Seq("--passphrase", pass) }) getOrElse Seq.empty
      val keyargs: Seq[String] = optKey map (k => Seq("--default-key", "0x%x" format (k))) getOrElse Seq.empty
      val args = passargs ++ Seq("--clear-sign", "--armor") ++ (if (agent) Seq("--use-agent") else Seq.empty) ++ keyargs
      sys.process.Process(command, args ++ Seq("--output", signatureFile.getAbsolutePath, file.getAbsolutePath)) !< match {
        case 0 => ()
        case n => sys.error("Failure running gpg --clear-sign.  Exit code: " + n)
      }
      signatureFile
    }
    override val toString: String = "RB GPG-Command(" + command + ")"
  }

  /**
    * https://www.debian.org/doc/debian-policy/ch-controlfields.html#syntax-of-control-files
    *
    * @param content key->value pairs
    * @return formatted lines
    */
  private def formatControlFile(content: Map[String, String]): Iterable[String] =
    // Dummy implementation ;)
    content.map { case (key, value) => s"$key: $value" }

  /**
    * Determine the target filename.
    *
    * See https://wiki.debian.org/ReproducibleBuilds/BuildinfoFiles#File_name_and_encoding
    * @return
    */
  def targetFilename(source: String, version: String, architecture: String, suffix: Option[String]) =
    source + "_" + version + "_" + architecture + suffix.map("_" + _).getOrElse("") + ".buildinfo"
}
