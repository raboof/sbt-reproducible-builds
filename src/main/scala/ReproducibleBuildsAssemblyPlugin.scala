package net.bzzt.reproduciblebuilds

import scala.util.Try

import sbt._
import sbt.plugins.JvmPlugin

object ReproducibleBuildsAssemblyPlugin extends AutoPlugin {
  val assemblyPluginOnClasspath =
    Try(getClass.getClassLoader.loadClass("sbtassembly.AssemblyPlugin")).isSuccess

  override def requires: Plugins = 
    if (assemblyPluginOnClasspath) AssemblyHelpers.plugin
    else JvmPlugin
  
  override def trigger = allRequirements

  override def projectSettings = 
    if (assemblyPluginOnClasspath) AssemblyHelpers.settings
    else Seq.empty
}
