package net.bzzt.reproduciblebuilds

import sbtassembly.{Assembly, AssemblyPlugin}
import sbtassembly.AssemblyPlugin.autoImport.{Assembly => _, baseAssemblySettings => _, _}
import sbt._
import sbt.Keys._

object AssemblyHelpers {
  val plugin: Plugins.Basic = AssemblyPlugin

  val settings: Seq[Setting[_]] =
    Seq(
      assembly := ReproducibleBuildsPlugin.postProcessJar(assembly.value)
    )

}
