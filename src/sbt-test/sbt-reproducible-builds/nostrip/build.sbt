import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.ReproducibleBuilds
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.reproducibleBuildsStrip

name := "nostrip"
organization := "net.bzzt"

scalaVersion := "3.10.1"

enablePlugins(ReproducibleBuildsPlugin)

reproducibleBuildsStrip := false
