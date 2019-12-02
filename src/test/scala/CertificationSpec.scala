package net.bzzt.reproduciblebuilds

import java.math.BigInteger

import org.scalatest._

import scala.collection.mutable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CertificationSpec extends AnyWordSpec with Matchers {
  "The certification model" should {
    "Roundtrip through Properties format" in {
      val cert = Certification(
        name = "simple",
        groupId = "net.bzzt",
        artifactId = "simple_2.12",
        version = "0.12.7",
        classifier = None,
        scalacPlugins = List("com.typesafe:genjavadoc"),
        scalaVersion = "0.9",
        scalaBinaryVersion = "0.12",
        sbtVersion = "1.2.7",
        checksums = List(
           Checksum("foo.jar", 42, List(-94, 69, 0, -18, -102, 68, -59, 90, -69, 11, 68, 97, -44, -12, 5, -28, -62, -39, -120, -28, 52, 121, -96, 56, 89, 67, 34, 109, -46, 72, 127, -81, 101, -94, -114, 18, 27, 127, 83, -101, 118, 77, -14, 26, -46, 125, -21, -19, 91, -65, 127, -48, 125, -13, 77, 65, 58, -127, -34, -14, -81, 88, -97, 27)),
           Checksum("bar-with-weird-characters.xml" , 42, Array[Byte](0x31, 0x33).toList.padTo[Byte, List[Byte]](64, 0x00))
        ),
        scmUri = Some("scm:svn:https://127.0.0.1/svn/my-project"),
        date = 42
      )
      val asString = cert.asPropertyString
      val roundtripped = Certification(asString)
      roundtripped should be(cert)
    }

  }
}
