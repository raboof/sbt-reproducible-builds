scalaVersion := "2.12.7"

name := "native-packager"
organization := "default"

enablePlugins(ReproducibleBuildsPlugin)
enablePlugins(JavaAppPackaging)

// Universal plugin settings:
maintainer := "arnout@bzzt.net"
