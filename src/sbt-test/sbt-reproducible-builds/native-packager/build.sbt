import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.disambiguation

scalaVersion := "2.12.7"

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(JavaAppPackaging)

// Universal plugin settings:
maintainer := "arnout@bzzt.net"

// Make the filename static for easier validation:
disambiguation in Compile := (_ => Some("STATIC"))
