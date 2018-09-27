import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.disambiguation

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(JavaAppPackaging)

// Universal plugin settings:
maintainer := "arnout@bzzt.net"

// Make the filename static for easier validation:
disambiguation in Compile := (_ => Some("STATIC"))
