sbtPlugin := true

organization := "net.bzzt"

scalaVersion := "2.12.12"

/**
 * should work with later sbt versions as well (tested at least with 1.4.x)
 */
sbtVersion := "1.2.7"

val sbtPgpVersion = "1.1.2"

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(SbtPlugin)
enablePlugins(ScriptedPlugin)

libraryDependencies += "net.bzzt" % "reproducible-builds-jvm-stripper" % "0.9"
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5" % "test"

// Optional integration:
addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager" % "1.8.0" % Provided)
addSbtPlugin("io.crashbox" %% "sbt-gpg" % "0.2.1" % Provided)
addSbtPlugin("com.eed3si9n" %% "sbt-assembly" % "0.14.10" % Provided)
// addSbtPlugin("com.jsuereth" % "sbt-pgp" % sbtPgpVersion % Provided)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false
