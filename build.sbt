sbtPlugin := true

organization := "net.bzzt"

scalaVersion := "2.12.4"

crossSbtVersions := Vector("0.13.16", "1.0.4")

enablePlugins(ReproducibleBuildsPlugin)

// Based on https://github.com/raboof/reproducible-build-maven-plugin
libraryDependencies += "net.bzzt" % "reproducible-build" % "0.2"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
