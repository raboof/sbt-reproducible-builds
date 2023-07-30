/*
 * Copyright 2017 Arnout Engelen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bzzt.reproduciblebuilds

import sbt._
import sbt.plugins.JvmPlugin

import scala.util.Try

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
