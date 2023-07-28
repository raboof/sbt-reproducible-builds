package net.bzzt.reproduciblebuilds

import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import sbt._
import sbt.Keys._

/** Helper code for sbt-native-packager integration.
  *
  * The main ReproducibleBuildsPlugin code should not rely on any sbt-native-packager code, since this plugin should be
  * usable without sbt-native-packager and not introduce it to projects not already using it.
  *
  * Any code dependent on sbt-native-packager classes should go here, and only be called in case it is indeed available.
  *
  * To avoid 'leaking' references to sbt-native-packager classes into other classes, care should be taken that none
  * appear in parameter or return value types either.
  */
object SbtNativePackagerHelpers {
  val plugin: Plugins.Basic = UniversalPlugin

  val settings: Seq[Setting[_]] = Seq(
    packageBin in Universal := {
      (packageBin in Universal).?.value match {
        case Some(zip) =>
          ReproducibleBuildsPlugin.postProcessZip(zip)
        case None =>
          throw new IllegalStateException(
            "Reference to undefined setting: packageBin in Universal. " +
              "Have you enabled a sbt-native-packager plugin?"
          )
      }
    }
  )
}
