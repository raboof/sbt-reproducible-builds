import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.disambiguation

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(SbtOsgi)

OsgiKeys.exportPackage := Seq("net.bzzt")

// When replacing packageBin with the OSGi bundle...
Compile / packageBin := OsgiKeys.bundle.value

// We need to explicitly load the rb settings again to
// make sure the OSGi package is post-processed:
ReproducibleBuildsPlugin.projectSettings

// Make the filename static for easier validation:
disambiguation in Compile := (_ => Some("STATIC"))
