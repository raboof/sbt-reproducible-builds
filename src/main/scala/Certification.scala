package net.bzzt.reproduciblebuilds

import java.io.StringReader
import java.math.BigInteger
import java.nio.file.Files
import java.security.MessageDigest

import sbt.io.syntax.File

import scala.collection.mutable

case class Checksum(filename: String, length: Int, checksum: List[Byte])
object Checksum {
  def apply(file: File): Checksum = {
    val bytes = Files.readAllBytes(file.toPath)
    val digest = MessageDigest.getInstance("SHA-256")
    new Checksum(file.getName, bytes.length, digest.digest(bytes).toList)
  }
}

case class Certification(
                          groupId: String,
                          artifactId: String,
                          version: String,
                          classifier: Option[String],
                          architecture: String,
                          scalaVersion: String,
                          scalaBinaryVersion: String,
                          sbtVersion: String,
                          checksums: List[Checksum]
                        ) {
  def asPropertyString: String = {
    val packageName = groupId + ":" + artifactId
    val content = mutable.LinkedHashMap(
      "group_id" -> groupId,
      "artifact_id" -> artifactId,
      "version" -> version,
      "build_architecture" -> architecture,
      "source" -> packageName,
      "binary" -> packageName,
      "package" -> packageName,
      // Extra 'custom' fields:
      "java.version" -> System.getProperty("java.version"),
      "sbt.version" -> sbtVersion,
      "scala.version" -> scalaVersion,
      "scala.binary-version" -> scalaBinaryVersion,
    ) ++ checksums.zipWithIndex.flatMap {
      case (Checksum(filename, length, checksum), idx) =>
        Seq(
          s"checksums_sha256.$idx.filename" -> filename,
          s"checksums_sha256.$idx.length" -> length.toString,
          s"checksums_sha256.$idx.checksum" -> checksum.map("%02x" format _).mkString,
        )
    } ++ classifier.map("classifier" -> _)

    content.map { case (key, value) => key + "=" + value }.mkString("\n")
  }

}
object Certification {
  def apply(propertyString: String): Certification = {
    val properties = new java.util.Properties()
    properties.load(new StringReader(propertyString))

    import scala.collection.JavaConverters._
    val checksums = properties
      .stringPropertyNames()
      .asScala
      .filter(_.startsWith("checksums_sha256"))
      .groupBy(key => key.split("\\.")(1))
      .toList
      .sortBy(_._1)
      .map(_._2)
      .map { keys =>
          val filename = properties.getProperty(keys.find(_.endsWith(".filename")).get)
          val length = Integer.parseInt(properties.getProperty(keys.find(_.endsWith(".length")).get))
          val checksum = properties.getProperty(keys.find(_.endsWith(".checksum")).get)
          val bs = new BigInteger(checksum, 16)
          Checksum(filename, length, bs.toByteArray.toList)
      }

    new Certification(
      properties.getProperty("group_id"),
      properties.getProperty("artifact_id"),
      properties.getProperty("version"),
      Option(properties.getProperty("classifier")),
      properties.getProperty("build_architecture"),
      properties.getProperty("scala.version"),
      properties.getProperty("scala.binary-version"),
      properties.getProperty("sbt.version"),
      checksums
    )
  }
}