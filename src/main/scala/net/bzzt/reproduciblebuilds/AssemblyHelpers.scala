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

import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport.{Assembly => _, baseAssemblySettings => _, _}

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
        } else
          ReproducibleBuildsPlugin.postProcessJar(jar)
      }
    )
}
