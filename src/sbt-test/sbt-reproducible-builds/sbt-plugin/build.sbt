import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.ReproducibleBuilds

organization := "net.bzzt"
name := "my-sbt-plugin"

scalaVersion := "2.12.21"

enablePlugins(SbtPlugin)
enablePlugins(ReproducibleBuildsPlugin)
