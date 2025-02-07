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

import io.crashbox.gpg.SbtGpg.autoImport.{gpg, gpgWarnOnFailure}
import io.crashbox.gpg.SbtGpg.packagedArtifactsImpl
import sbt.Keys.{packagedArtifacts, streams}
import sbt.Setting

object GpgHelpers {
  val settings: Seq[Setting[_]] =
    Seq(
      packagedArtifacts :=
        packagedArtifactsImpl(packagedArtifacts.value, gpg.value, gpgWarnOnFailure.value)(streams.value.log.warn(_))
    )
}
