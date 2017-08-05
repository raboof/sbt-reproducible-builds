package net.bzzt.reproduciblebuilds

import sbt._
import Keys._
import plugins.JvmPlugin

import io.github.zlika.reproducible._

object ReproducibleBuildsPlugin extends AutoPlugin {
  // To make sure we're loaded after the defaults
  override def requires: Plugins = JvmPlugin

  override lazy val projectSettings = Seq(
    packageBin in Compile := {
      val bin = (packageBin in Compile).value
      val out = new File(bin.getCanonicalPath + "_")
      new ZipStripper()
        .addFileStripper("META-INF/MANIFEST.MF", new ManifestStripper())
        .addFileStripper("META-INF/maven/\\S*/pom.properties", new PomPropertiesStripper())
        .strip(bin, out)
      out
    },
    commands += helloCommand
  )
  lazy val helloCommand =
    Command.command("hello") { (state: State) =>
      println("Hi!")
      state
    }
}
