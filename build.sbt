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
// sbt 2.0.2 is published against 3.8.4, so we need to align with that
lazy val scala3 = "3.8.4"
ThisBuild / crossScalaVersions := Seq(scala212, scala3)
publish / skip := true // don't publish the root project

enablePlugins(ReproducibleBuildsPlugin)

lazy val plugin = project
  .enablePlugins(SbtPlugin)
  .enablePlugins(ScriptedPlugin)
  .settings(
    Seq(
      organization := "net.bzzt",
      name := "sbt-reproducible-builds",
      (pluginCrossBuild / sbtVersion) := {
        scalaBinaryVersion.value match {
          case "2.12" => "1.12.9"
          case _      => "2.0.2"
        }
      },
      // https://github.com/sbt/sbt2-compat/pull/15 would unlock
      // some optimizations, but let's do without for now
      addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0"),
      // Optional integration:
      libraryDependencies += Defaults.sbtPluginExtra("com.github.sbt" %% "sbt-native-packager" % "1.11.7" % Provided,
                                                     (pluginCrossBuild / sbtBinaryVersion).value,
                                                     scalaBinaryVersion.value
      ),
      // TODO
      // addSbtPlugin("io.crashbox" %% "sbt-gpg" % "0.2.1" % Provided),
      libraryDependencies += Defaults.sbtPluginExtra("com.eed3si9n" %% "sbt-assembly" % "2.3.1" % Provided,
                                                     (pluginCrossBuild / sbtBinaryVersion).value,
                                                     scalaBinaryVersion.value
      ),
      // addSbtPlugin("com.jsuereth" % "sbt-pgp" % sbtPgpVersion % Provided)
      libraryDependencies += "net.bzzt" % "reproducible-builds-jvm-stripper" % "0.10",
      libraryDependencies += "io.spray" %% "spray-json" % "1.3.6",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % "test",
      scriptedLaunchOpts :=
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
      scriptedBufferLog := false
    )
  )

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
