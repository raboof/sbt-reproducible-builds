package net.bzzt.reproduciblebuilds

import io.crashbox.gpg.SbtGpg.autoImport.{gpg, gpgWarnOnFailure}
import io.crashbox.gpg.SbtGpg.packagedArtifactsImpl
import sbt.Keys.{packagedArtifacts, streams}
import sbt.Setting

object GpgHelpers {
  val settings: Seq[Setting[_]] =
    Seq(
      packagedArtifacts := {
        packagedArtifactsImpl(packagedArtifacts.value, gpg.value, gpgWarnOnFailure.value)(streams.value.log.warn(_))
      }
    )
}
