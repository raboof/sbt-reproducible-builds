/*
 * Copyright 2017-2026 Arnout Engelen
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

import org.apache.ivy.core.IvyPatternHelper

object UrlUtils {
  private def substitute(pattern: String,
                         info: SubstituteInfo,
                         `type`: String,
                         ext: String,
                         conf: String,
                         extraModuleAttributes: Map[String, String]
  ): String = {
    import scala.collection.JavaConverters._
    val extraArtifactAttributes = null
    IvyPatternHelper.substitute(
      pattern,
      info.organization,
      info.packageName,
      info.version,
      info.packageName,
      `type`,
      ext,
      conf,
      extraModuleAttributes.asJava,
      extraArtifactAttributes
    )
  }

  private def substitutePattern(pattern: String, ext: String, classifier: Option[String], info: SubstituteInfo) = {
    val extraModuleAttributes =
      (if (info.crossPaths) Map("scalaVersion" -> info.scalaBinaryVersion) else Map.empty) ++
        (if (info.sbtPlugin) Map("sbtVersion" -> info.sbtVersion) else Map.empty) ++
        (classifier.map(c => ("classifier", c)).toMap)

    substitute(
      pattern,
      info,
      ext,
      ext,
      Compile.name,
      extraModuleAttributes
    )
  }

  def artifactUrl(resolver: Resolver,
                  `type`: String,
                  ext: String,
                  classifier: Option[String],
                  info: SubstituteInfo
  ): String = {
    def resolvePattern(base: String, pattern: String): String = {
      val normBase = base.replace('\\', '/')
      if (normBase.endsWith("/") || pattern.startsWith("/")) normBase + pattern
      else normBase + "/" + pattern
    }

    resolver match {
      case repository: MavenRepository =>
        val pattern = resolvePattern(
          repository.root,
          "[organisation]/[module](_[scalaVersion])(_[sbtVersion])/[revision]/[artifact](_[scalaVersion])(_[sbtVersion])-[revision](-[classifier]).[ext]"
        )
        substitutePattern(pattern, ext, classifier, info)
      case repository: PatternsBasedRepository =>
        val pattern = repository.patterns.artifactPatterns.headOption
          .orElse(
            repository.patterns.ivyPatterns.headOption
          )
          .getOrElse(
            throw new IllegalArgumentException("Expected at least a single artifact pattern")
          )
        substitutePattern(pattern, ext, classifier, info)
      case _: ChainedResolver =>
        throw new IllegalArgumentException("Not yet implemented")

      // TODO: we don't have support for `RawRepository`, that would
      // make things more difficult
    }
  }

}
