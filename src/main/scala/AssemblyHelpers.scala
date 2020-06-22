package net.bzzt.reproduciblebuilds

import sbtassembly.{Assembly, AssemblyPlugin}
import sbtassembly.AssemblyPlugin.autoImport.{Assembly => _, baseAssemblySettings => _, _}
import sbt._
import sbt.Keys._

object AssemblyHelpers {
  val plugin: Plugins.Basic = AssemblyPlugin

  val settings: Seq[Setting[_]] =
    Seq(
      assembly := {
        val log = streams.value.log
        val jar = assembly.value
        val options = (assemblyOption in assembly).value

        if (options.prependShellScript.isDefined) {
          log.warn("Cannot make assembly reproducible when prependShellScript is set")
          jar
        }
        else {
          ReproducibleBuildsPlugin.postProcessJar(jar)
        }
      }
    )
}
