sbtPlugin := true

organization := "net.bzzt"
homepage := Some(url("https://github.com/raboof/sbt-reproducible-builds"))
licenses := List(("MIT", url("https://opensource.org/licenses/MIT")))
developers := List(
  Developer(
    "raboof",
    "Arnout Engelen",
    "arnout@bzzt.net",
    url("https://arnout.engelen.eu")
  )
)

scalaVersion := "2.12.18"

/** should work with later sbt versions as well (tested at least with 1.4.x)
  */
sbtVersion := "1.2.7"

val sbtPgpVersion = "1.1.2"

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(SbtPlugin)
enablePlugins(ScriptedPlugin)

libraryDependencies += "net.bzzt" % "reproducible-builds-jvm-stripper" % "0.9"
libraryDependencies += "io.spray" %% "spray-json" % "1.3.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % "test"

// Optional integration:
addSbtPlugin("com.github.sbt" %% "sbt-native-packager" % "1.9.16" % Provided)
addSbtPlugin("io.crashbox" %% "sbt-gpg" % "0.2.1" % Provided)
addSbtPlugin("com.eed3si9n" %% "sbt-assembly" % "2.1.1" % Provided)
// addSbtPlugin("com.jsuereth" % "sbt-pgp" % sbtPgpVersion % Provided)

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++
    Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false
