sbtPlugin := true

organization := "net.bzzt"

scalaVersion := "2.10.6"

version := "0.3-SNAPSHOT"

crossSbtVersions := Vector("0.13.16", "1.0.1")

// Based on https://github.com/raboof/reproducible-build-maven-plugin
libraryDependencies += "net.bzzt" % "reproducible-build" % "0.1"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
