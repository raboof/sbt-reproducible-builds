import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.ReproducibleBuilds
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.reproducibleBuildsStrip

name := "nostrip"
organization := "net.bzzt"

scalaVersion := "3.10.1"

ThisBuild / publishTo := Some(MavenCache("local-maven", file("/tmp")))

enablePlugins(ReproducibleBuildsPlugin)

reproducibleBuildsStrip := false
