scalaVersion := "2.12.7"

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(SbtOsgi)

OsgiKeys.exportPackage := Seq("net.bzzt")

// When replacing packageBin with the OSGi bundle,
// we need to explicitly post-process it:
Compile / packageBin := ReproducibleBuildsPlugin.postProcessJar(OsgiKeys.bundle.value)
