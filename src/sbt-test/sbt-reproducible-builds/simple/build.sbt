import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.disambiguation

organization := "net.bzzt"

scalaVersion := "2.12.7"

enablePlugins(ReproducibleBuildsPlugin)

// Make the filename static for easier validation:
disambiguation in Compile := (_ => Some("STATIC"))
