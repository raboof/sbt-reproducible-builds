import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.disambiguation

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(SbtOsgi)

OsgiKeys.exportPackage := Seq("net.bzzt")

// When replacing packageBin with the OSGi bundle,
// we need to explicitly post-process it:
Compile / packageBin := ReproducibleBuildsPlugin.postProcessJar(OsgiKeys.bundle.value)

// Make the filename static for easier validation:
disambiguation in Compile := (_ => Some("STATIC"))
