import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.ReproducibleBuilds
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.reproducibleBuildsStrip

name := "nostrip"
organization := "net.bzzt"

scalaVersion := "2.13.18"

enablePlugins(ReproducibleBuildsPlugin)

reproducibleBuildsStrip := false
