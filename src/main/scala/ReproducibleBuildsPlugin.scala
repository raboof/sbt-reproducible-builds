package net.bzzt.reproduciblebuilds

import sbt.{ AutoPlugin, Compile, Plugins, File}
import sbt.Keys._
import sbt.plugins.JvmPlugin

import io.github.zlika.reproducible._

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  override def requires: Plugins = JvmPlugin

  override lazy val projectSettings = Seq(
    packageBin in (Compile, packageBin) ~= { bin =>
      val out = new File(bin.getCanonicalPath + "_")
      new ZipStripper()
        .addFileStripper("META-INF/MANIFEST.MF", new ManifestStripper())
        .addFileStripper("META-INF/maven/\\S*/pom.properties", new PomPropertiesStripper())
        .strip(bin, out)
      out
    }
  )
}
