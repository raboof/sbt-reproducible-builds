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
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.2" % "test"

// Optional integration:
addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager" % "1.7.1" % Provided)
addSbtPlugin("io.crashbox" %% "sbt-gpg" % "0.2.1" % Provided)
// addSbtPlugin("com.jsuereth" % "sbt-pgp" % sbtPgpVersion % Provided)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false
