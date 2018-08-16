package net.bzzt.reproduciblebuilds

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

import gigahorse.GigahorseSupport
import sbt.Defaults._
import sbt.{Artifact, AutoPlugin, Compile, File, Plugins, ScalaVersion, taskKey}
import sbt.Keys.{scalaVersion, _}
import sbt.plugins.JvmPlugin
import io.github.zlika.reproducible._
import sbt.io.syntax.{URI, uri}
import sbt.librarymanagement.Http.http

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  override def requires: Plugins = JvmPlugin

  val reproducibleBuildsPackageName = taskKey[String]("Package name of this build, including version but excluding disambiguation string")
  val reproducibleBuildsCertification = taskKey[File]("Create a Reproducible Builds certification")
  val reproducibleBuildsUploadPrefix = taskKey[URI]("Base URL to send uploads to")
  val reproducibleBuildsUploadCertification = taskKey[Unit]("Upload the Reproducible Builds certification")

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
    reproducibleBuildsUploadCertification := {
      val file = reproducibleBuildsCertification.value
      val groupId = organization.value
      val uri = reproducibleBuildsUploadPrefix.value.resolve(groupId + "/" + reproducibleBuildsPackageName.value + "/").resolve(file.getName)

      import gigahorse.HttpWrite._
      http.run(
        GigahorseSupport.url(uri.toASCIIString)
          .withMethod("PUT")
          // TODO content-type
          .withBody(new String(Files.readAllBytes(file.toPath), Charset.forName("UTF-8"))))
    }
  )

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
