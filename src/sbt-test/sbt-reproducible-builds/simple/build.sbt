import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.disambiguation

enablePlugins(ReproducibleBuildsPlugin)

// Make the filename static for easier validation:
disambiguation in Compile := (_ => Some("STATIC"))
