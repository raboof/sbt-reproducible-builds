import sbt.Keys.{semanticdbEnabled, semanticdbVersion}

sbtPlugin := true

organization := "net.bzzt"
name := "sbt-reproducible-builds"
startYear := Some(2017)
homepage := Some(url("https://github.com/raboof/sbt-reproducible-builds"))
licenses := List(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.txt")))
developers := List(
  Developer(
    "raboof",
    "Arnout Engelen",
    "arnout@bzzt.net",
    url("https://arnout.engelen.eu")
  )
)

val sbtPgpVersion = "1.1.2"

lazy val scala212 = "2.12.21"
// sbt 2.1.0 is published against 3.9.0, so we need to align with that
lazy val scala3 = "3.9.0"
ThisBuild / crossScalaVersions := Seq(scala212, scala3)
ThisBuild / scalaVersion := crossScalaVersions.value.head

enablePlugins(ReproducibleBuildsPlugin)

enablePlugins(SbtPlugin)
enablePlugins(ScriptedPlugin)
organization := "net.bzzt"
name := "sbt-reproducible-builds"
(pluginCrossBuild / sbtVersion) := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.12.9"
    case _      => "2.1.0"
  }
}
scriptedSbt := (scalaBinaryVersion.value match {
  case "2.12" => "1.13.1"
  case _      => "2.1.0"
})
addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.2.0")
// Optional integration:
libraryDependencies += Defaults.sbtPluginExtra("com.github.sbt" %% "sbt-native-packager" % "1.11.7" % Provided,
                                               (pluginCrossBuild / sbtBinaryVersion).value,
                                               scalaBinaryVersion.value
)
libraryDependencies += Defaults.sbtPluginExtra("com.eed3si9n" %% "sbt-assembly" % "2.5.0" % Provided,
                                               (pluginCrossBuild / sbtBinaryVersion).value,
                                               scalaBinaryVersion.value
)
// addSbtPlugin("com.jsuereth" % "sbt-pgp" % sbtPgpVersion % Provided)
libraryDependencies += "net.bzzt" % "reproducible-builds-jvm-stripper" % "0.10"
libraryDependencies += "io.spray" %% "spray-json" % "1.3.6"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % "test"

scriptedLaunchOpts :=
  scriptedLaunchOpts.value ++
    Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
scriptedBufferLog := false

// scalafix specific settings
inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions ++= Seq(
      "-Ywarn-unused"
    )
  )
)

addCommandAlias("applyCodeStyle", ";+clean ;scalafixAll ;scalafmtAll; scalafmtSbt")
addCommandAlias("checkCodeStyle", ";+clean ;scalafixAll --check ;scalafmtCheckAll; scalafmtSbtCheck")

//scriptedLaunchOpts += "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
