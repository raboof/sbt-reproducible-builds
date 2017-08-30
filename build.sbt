sbtPlugin := true

organization := "net.bzzt"

scalaVersion := "2.10.6"

version := "0.2-SNAPSHOT"

// Based on https://github.com/raboof/reproducible-build-maven-plugin
libraryDependencies += "net.bzzt" % "reproducible-build" % "0.1"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
