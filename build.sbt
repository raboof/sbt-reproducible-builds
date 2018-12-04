sbtPlugin := true

organization := "net.bzzt"

scalaVersion := "2.12.8"

/**
 * Don't build for 0.13, since that does not include
 * gigahorse to perform uploads.
 */
crossSbtVersions := Vector(/*"0.13.16",*/ "1.2.7")

val sbtPgpVersion = "1.1.2"

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(SbtPlugin)
enablePlugins(ScriptedPlugin)

libraryDependencies += "net.bzzt" % "reproducible-builds-jvm-stripper" % "0.9"
libraryDependencies += "com.jsuereth" % "sbt-pgp" % sbtPgpVersion
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"

// Optional integration:
addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager" % "1.3.14" % Provided)

// Dogfood^WChampagne time!
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin._
reproducibleBuildsUploadPrefix := uri("http://pi.bzzt.net:8081/")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

// Transitive plugin dependency:
addSbtPlugin("com.jsuereth" % "sbt-pgp" % sbtPgpVersion)
