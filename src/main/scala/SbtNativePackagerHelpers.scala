package net.bzzt.reproduciblebuilds

import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import sbt._
import sbt.Keys._

/**
  * Helper code for sbt-native-packager integration.
  *
  * The main ReproducibleBuildsPlugin code should not rely on any
  * sbt-native-packager code, since this plugin should be usable without
  * sbt-native-packager and not introduce it to projects not already using it.
  *
  * Any code dependent on sbt-native-packager classes
  * should go here, and only be called in case it is indeed available.
  *
  * To avoid 'leaking' references to sbt-native-packager classes into other
  * classes, care should be taken that none appear in parameter or return
  * value types either.
  */
object SbtNativePackagerHelpers {
  val plugin: Plugins.Basic = UniversalPlugin

  val none: File = new File("none")

  val settings: Seq[Setting[_]] = Seq(
    // Make sure there's always a value defined, even when the Universal plugin isn't loaded:
    packageBin in Global := none,
    packageBin in Universal := {
      val upstream = (packageBin in Universal).value
      // If the value is still `none`, the Universal plugin isn't loaded.
      if (upstream == none) none
      else ReproducibleBuildsPlugin.postProcessZip(upstream)
    }
  )
}
