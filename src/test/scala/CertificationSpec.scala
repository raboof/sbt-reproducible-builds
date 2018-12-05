package net.bzzt.reproduciblebuilds

import java.math.BigInteger

import org.scalatest._

import scala.collection.mutable

class CertificationSpec extends WordSpec with Matchers {
  "The certification model" should {
    "Roundtrip through Properties format" in {
      val cert = Certification(
        groupId="net.bzzt",
        artifactId="simple_2.12",
        version = "0.12.7",
        classifier = None,
        architecture = "all",
        scalaVersion = "0.9",
        scalaBinaryVersion = "0.12",
        sbtVersion = "1.2.7",
        checksums = List(
           Checksum("foo.jar", 42, Array[Byte](0x31, 0x23).toList),
           Checksum("bar-with-weird-characters.xml" , 42, Array[Byte](0x31, 0x33).toList)
        )
      )
      val asString = cert.asPropertyString
      val roundtripped = Certification(asString)
      roundtripped should be(cert)
    }

  }
}
