package net.bzzt.reproduciblebuilds

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

import sbt.Defaults._
import sbt.{Artifact, AutoPlugin, Compile, File, Plugins, ScalaVersion, taskKey}
import sbt.Keys.{scalaVersion, _}
import sbt.plugins.JvmPlugin
import io.github.zlika.reproducible._

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  override def requires: Plugins = JvmPlugin

  val reproducibleBuildsCertification = taskKey[File]("Create a Reproducible Builds certification")
  val reproducibleBuildsUploadCertification = taskKey[Unit]("Upload the Reproducible Builds certification")

  override lazy val projectSettings = Seq(
    packageBin in Compile := {
      val bin = (packageBin in Compile).value
      val out = new File(bin.getCanonicalPath + "_")
      new ZipStripper()
        .addFileStripper("META-INF/MANIFEST.MF", new ManifestStripper())
        .addFileStripper("META-INF/maven/\\S*/pom.properties", new PomPropertiesStripper())
        .strip(bin, out)
      out
    },
    artifactPath in reproducibleBuildsCertification := artifactPathSetting(artifact in reproducibleBuildsCertification).value,
    reproducibleBuildsCertification := {
      val groupId = organization.value
      val packagedFile = (packageBin in Compile).value
      val packageName = moduleName.value

      val targetDirPath = crossTarget.value
      val packageVersion = version.value
      val targetFilePath = targetDirPath.toPath.resolve(targetFilename(packageName, packageVersion, "TODO"))

      val bytes = Files.readAllBytes(packagedFile.toPath)

      val digest = MessageDigest.getInstance("SHA-256")
      val checksum = digest.digest(bytes).map("%02x" format _).mkString

      val content = Map(
        "Format" -> "1.8",
        "Build-Architecture" -> "all",
        "Source" -> packageName,
        "Binary" -> packageName,
        "Package" -> packageName,
        // Strictly spoken not allowed by https://wiki.debian.org/ReproducibleBuilds/BuildinfoFiles#Field_descriptions,
        // but jars should typically be architecture-independent...
        "Architecture" -> "all",
        "Version" -> packageVersion,
        "Checksums-Sha256" -> s"\n $checksum ${bytes.length} ${packagedFile.getName}",
        // Extra 'custom' fields:
        "ScalaVersion" -> (scalaVersion in artifactName).value,
        "ScalaBinaryVersion" -> (scalaBinaryVersion in artifactName).value,
      )

      import collection.JavaConverters._
      Files.write(targetFilePath, formatControlFile(content).asJava, Charset.forName("UTF-8"))

      targetFilePath.toFile
    },
    reproducibleBuildsUploadCertification := {
      println(reproducibleBuildsCertification.value)
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
    *
    * @param source
    * @param version
    * @param suffix
    * @return
    */
  def targetFilename(source: String, version: String, suffix: String) =
    source + "_" + version + "_" + suffix + ".buildinfo"
}
