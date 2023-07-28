package net.bzzt.reproduciblebuilds

import java.io.StringReader
import java.math.BigInteger
import java.nio.file.Files
import java.security.MessageDigest

import sbt.{Artifact, File, ModuleID}
import sbt.librarymanagement.ScmInfo

import scala.collection.mutable
import scala.collection.immutable

case class Checksum(filename: String, length: Int, checksum: List[Byte]) {
  def hexChecksum = checksum.map("%02x" format _).mkString
}
object Checksum {
  def apply(file: File): Checksum = {
    val bytes = Files.readAllBytes(file.toPath)
    val digest = MessageDigest.getInstance("SHA-512")
    new Checksum(file.getName, bytes.length, digest.digest(bytes).toList)
  }
}

case class Certification(
    name: String,
    groupId: String,
    artifactId: String,
    version: String,
    scmUri: Option[String],
    classifier: Option[String],
    scalacPlugins: immutable.Seq[String],
    scalaVersion: String,
    scalaBinaryVersion: String,
    sbtVersion: String,
    checksums: immutable.Seq[Checksum],
    date: Long
) {
  require(
    checksums.map(_.filename).toSet.size == checksums.length,
    "Checksum filenames should be unique"
  )

  def asPropertyString: String = {
    val packageName = groupId + ":" + artifactId
    val content = mutable.LinkedHashMap(
      "buildinfo.version" -> "0.1-SNAPSHOT",
      "name" -> name,
      "group-id" -> groupId,
      "artifact-id" -> artifactId,
      "version" -> version,
      // Extra 'custom' fields:
      "java.version" -> System.getProperty("java.version"),
      "os.name" -> System.getProperty("os.name"),
      "build-tool" -> "sbt",
      "sbt.version" -> sbtVersion,
      "scala.version" -> scalaVersion,
      "scala.binary-version" -> scalaBinaryVersion,
      "date" -> date
    ) ++ scalacPlugins.zipWithIndex.map { case (plugin, idx) =>
      s"scala.compiler.plugins.$idx" -> plugin
    } ++ checksums.zipWithIndex.flatMap { case (checksum @ Checksum(filename, length, _), idx) =>
      Seq(
        s"outputs.$idx.filename" -> filename,
        s"outputs.$idx.length" -> length.toString,
        s"outputs.$idx.checksums.sha512" -> checksum.hexChecksum
      )
    } ++
      classifier.map("classifier" -> _) ++
      scmUri.map("source.scm.uri" -> _)

    content.map { case (key, value) => key + "=" + value }.mkString("\n")
  }

}
object Certification {
  def apply(
      organization: String,
      packageName: String,
      packageVersion: String,
      scmInfo: Option[ScmInfo],
      packagedArtifacts: Map[Artifact, File],
      libraryDependencies: Seq[ModuleID],
      scalaVersion: String,
      scalaBinaryVersion: String,
      sbtVersion: String
  ): Certification = {

    val artifacts = packagedArtifacts
      .filter { case (artifact, _) => artifact.`type` == "pom" || artifact.`type` == "jar" }

    val classifier = packagedArtifacts.collectFirst {
      case (artifact, _) if artifact.`type` == "jar" => artifact.classifier
    }.flatten

    val checksums: List[Checksum] = artifacts.map { case (_, packagedFile) => Checksum(packagedFile) }.toList

    val scalacPlugins = libraryDependencies
      .filter(_.configurations.contains("plugin->default(compile)"))
      .map(mid => mid.organization + ":" + mid.name)
      .toIndexedSeq

    Certification(
      packageName,
      organization,
      packageName + "_" + scalaBinaryVersion,
      packageVersion,
      scmInfo.map(info => info.devConnection.getOrElse(info.connection)),
      classifier,
      scalacPlugins,
      scalaVersion,
      scalaBinaryVersion,
      sbtVersion,
      checksums,
      artifacts.values.map(_.lastModified()).max
    )
  }

  def apply(propertyString: String): Certification = {
    val properties = new java.util.Properties()
    properties.load(new StringReader(propertyString))

    import scala.collection.JavaConverters._
    val checksums = properties
      .stringPropertyNames()
      .asScala
      .filter(_.startsWith("outputs"))
      .groupBy(key => key.split("\\.")(1))
      .toList
      .sortBy(_._1)
      .map(_._2)
      .map { keys =>
        val filename = properties.getProperty(keys.find(_.endsWith(".filename")).get)
        val length = Integer.parseInt(properties.getProperty(keys.find(_.endsWith(".length")).get))
        val checksum = properties.getProperty(keys.find(_.endsWith(".checksums.sha512")).get)
        val bs = new BigInteger(checksum, 16).toByteArray.toList.reverse
          .padTo[Byte, List[Byte]](64, Byte.box(0x00))
          .take(64)
          .reverse
        Checksum(filename, length, bs)
      }

    val ScalacPluginLine = "scala\\.compiler\\.plugins\\.(\\d+)".r
    val scalacPlugins = properties
      .stringPropertyNames()
      .asScala
      .filter(_.startsWith("scala.compiler.plugins"))
      .toList
      .map { case ScalacPluginLine(idx) => idx }
      .sorted
      .map { case idx => properties.getProperty("scala.compiler.plugins." + idx) }

    new Certification(
      properties.getProperty("name"),
      properties.getProperty("group-id"),
      properties.getProperty("artifact-id"),
      properties.getProperty("version"),
      Option(properties.getProperty("source.scm.uri")),
      Option(properties.getProperty("classifier")),
      scalacPlugins,
      properties.getProperty("scala.version"),
      properties.getProperty("scala.binary-version"),
      properties.getProperty("sbt.version"),
      checksums,
      properties.getProperty("date").toLong
    )
  }
}
