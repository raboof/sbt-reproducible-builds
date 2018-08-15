import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.disambiguation

enablePlugins(ReproducibleBuildsPlugin)

disambiguation in Compile := (_ => Some("STATIC"))