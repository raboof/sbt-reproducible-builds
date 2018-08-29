sbtPlugin := true

organization := "net.bzzt"

scalaVersion := "2.12.4"

/**
 * Don't build for 0.13, since that does not include
 * gigahorse to perform uploads.
 */
crossSbtVersions := Vector(/*"0.13.16",*/ "1.0.4")

val sbtPgpVersion = "1.1.1"

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(SbtPlugin)
enablePlugins(ScriptedPlugin)

// Based on https://github.com/raboof/reproducible-build-maven-plugin
libraryDependencies += "net.bzzt" % "reproducible-build" % "0.2"
libraryDependencies += "com.jsuereth" % "sbt-pgp" % sbtPgpVersion
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.4"

// Dogfood^WChampagne time!
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin._
reproducibleBuildsUploadPrefix := uri("http://pi.bzzt.net:8081")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

// Transitive plugin dependency:
addSbtPlugin("com.jsuereth" % "sbt-pgp" % sbtPgpVersion)
