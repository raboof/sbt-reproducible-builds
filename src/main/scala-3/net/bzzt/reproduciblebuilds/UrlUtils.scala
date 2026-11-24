/*
 * Copyright 2017-2026 Arnout Engelen
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
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

import java.io.File
import java.util.regex.Matcher
import sbt.io.syntax.*
import sbt.librarymanagement.{FileRepository, MavenCache, MavenRepo, PatternsBasedRepository, Resolver, URLRepository}

import java.net.URI

object UrlUtils {
  private def isFilePatterned(repo: PatternsBasedRepository): Boolean =
    repo.patterns.artifactPatterns.headOption.exists: pat =>
      pat.contains("[organisation]") && !pat.trim.startsWith("http")

  private def mavenModuleArtifactId(
                             moduleName: String,
                             info: SubstituteInfo,
                           ): String =
    val suffix = Vector(
      Option.when(info.crossPaths)(info.scalaBinaryVersion),
      Option.when(info.sbtPlugin)(info.sbtVersion)
    ).flatten.map("_" + _).mkString
    if suffix.isEmpty || moduleName.endsWith(suffix) then moduleName else moduleName + suffix

  // loosely based on sbt's GenericPublisher.publish 89
  def artifactUrl(target: Resolver, `type`: String, ext: String, classifier: Option[String], info: SubstituteInfo): String = {
    target match {
      case urlRepo: URLRepository
        if urlRepo.patterns.isMavenCompatible && !isFilePatterned(urlRepo) =>
        val pat = urlRepo.patterns.artifactPatterns.headOption.getOrElse(
          sys.error("URLRepository has no artifact pattern")
        )
        val idx = pat.indexOf("[organisation]")
        if idx < 0 then sys.error(s"Maven-compatible pattern '$pat' has no [organisation] token")
        ivylessMaven(
          info,
          pat.substring(0, idx),
          ext,
          classifier,
        )
      case urlRepo: URLRepository if !isFilePatterned(urlRepo) =>
        ivyless(
          info,
          `type`,
          ext,
          classifier,
          urlRepo,
        )
      case fileRepo: FileRepository =>
        ivylessFile(
          info,
          fileRepo,
          `type`,
          ext,
          classifier,
        )
      case pbr: PatternsBasedRepository if isFilePatterned(pbr) =>
        // File repo detected by pattern (e.g. scripted classloader makes type match fail)
        val pat = pbr.patterns.artifactPatterns.head
        val baseStr =
          pat.substring(0, pat.indexOf("[organisation]")).replace('\\', '/').stripSuffix("/")
        val repoDir =
          (if baseStr.startsWith("file:") then new File(new java.net.URI(baseStr))
          else new File(baseStr)).getAbsoluteFile
        if pbr.patterns.isMavenCompatible then
          ivylessMavenFile(
            info,
            repoDir,
            ext,
            classifier,
          )
        else
          ivylessLocal(
            info,
            repoDir,
            `type`,
            ext,
            classifier,
          )
      case mavenCache: MavenCache =>
        ivylessMavenFile(
          info,
          mavenCache.rootFile,
          ext,
          classifier,
        )
      case mavenRepo: MavenRepo =>
        val root = mavenRepo.root.stripSuffix("/")
        if root.startsWith("http://") || root.startsWith("https://") then
          ivylessMaven(
            info,
            root,
            ext,
            classifier
          )
        else if root.startsWith("file:") then
          val repoBase = new File(URI.create(root))
          ivylessMavenFile(
            info,
            repoBase,
            ext,
            classifier
          )
        else
          sys.error(
            s"ivyless Maven publish: unsupported root '$root'; use a supported repository (http/https/file)."
          )
      case other =>
        throw new IllegalArgumentException(s"Not yet implemented: $other")
    }
  }

  // from sbt's typeToFolder 210
  private def typeToFolder(tpe: String): String = tpe match
    case "jar" => "jars"
    case "src" | "source" | "sources" => "srcs"
    case "doc" | "docs" | "javadoc" | "javadocs" => "docs"
    case "pom" => "poms"
    case "ivy" => "ivys"
    case other => other + "s"

  // from sbt's ivylessPublishLocal 222
  private def ivylessLocal(info: SubstituteInfo, localRepoBase: File, `type`: String, extension: String, classifier: Option[String]) =
    val org = info.organization
    val moduleName = info.packageName
    val version = info.version

    // TODO possibly add scala/sbt versions
    val moduleDir = localRepoBase / org / version

    val folder = typeToFolder(`type`)
    val targetDir = moduleDir / folder
    val classifierStr = classifier.getOrElse("")
    // TODO pubNameLookup
    val artName = info.packageName
    val fileName = s"$artName$classifier.$extension"
    val targetFile = targetDir / fileName
    s"file:${targetFile.absolutePath}"

  // from sbt 272
  private def substituteIvyArtifactPattern(
                                            pattern: String,
                                            org: String,
                                            moduleName: String,
                                            version: String,
                                            typeFolder: String,
                                            artifactName: String,
                                            classifier: String,
                                            ext: String,
                                            scalaV: Option[String],
                                            sbtV: Option[String]
                                          ): String =
    var s = pattern
    s = s.replace("[organisation]", org)
    s = s.replace("[module]", moduleName)
    s = s.replace("[revision]", version)
    s = s.replace("[type]s", typeFolder)
    s = s.replace("[artifact]", artifactName)
    s = s.replace("[ext]", ext)
    if classifier.nonEmpty then s = s.replace("(-[classifier])", s"-$classifier")
    else s = s.replace("(-[classifier])", "")
    // Substitute or drop optional Ivy pattern parts (scala/sbt version), remove branch for ivyless layout
    s = s.replaceAll(
      "\\(scala_[^)]+/\\)",
      scalaV.map(v => Matcher.quoteReplacement(s"scala_$v/")).getOrElse("")
    )
    s = s.replaceAll(
      "\\(sbt_[^)]+/\\)",
      sbtV.map(v => Matcher.quoteReplacement(s"sbt_$v/")).getOrElse("")
    )
    s = s.replaceAll("\\(\\[branch\\]/\\)", "")
    s
  end substituteIvyArtifactPattern

  // loosely based on sbt's ivylessPublish 392
  private def ivyless(info: SubstituteInfo, `type`: String, extension: String, classifier: Option[String], urlRepo: URLRepository) =
    val folder = typeToFolder(`type`)
    val c = classifier.map("-" + _).getOrElse("")
    val artifactPattern = urlRepo.patterns.artifactPatterns.head
    substituteIvyArtifactPattern(
      artifactPattern,
      info.organization,
      info.packageName,
      info.version,
      folder,
      info.packageName,
      c,
      extension,
      Some(info.scalaBinaryVersion),
      Some(info.sbtVersion)
    )

  // based on sbt's ivylessPublishToFile 466
  private def ivylessFile(info: SubstituteInfo, fileRepo: FileRepository, `type`: String, extension: String, classifier: Option[String]) =
    val pattern = fileRepo.patterns.artifactPatterns.head
    val baseStr =
      if pattern.contains("[organisation]") then
        pattern.substring(0, pattern.indexOf("[organisation]"))
      else pattern
    val normalized = baseStr.replace('\\', '/').stripSuffix("/")
    val localRepoBase =
      if normalized.startsWith("file:") then new File(new java.net.URI(normalized))
      else new File(normalized)
    val repoDir = localRepoBase.getAbsoluteFile
    val isMavenLayout = fileRepo.patterns.isMavenCompatible
    if isMavenLayout then
      ivylessMavenFile(info, repoDir, extension, classifier)
    else
      ivylessLocal(info, repoDir, `type`, extension, classifier)

  // based on sbt's ivylessPublishMavenToFile 585
  private def ivylessMavenFile(info: SubstituteInfo, repoBase: File, extension: String, classifier: Option[String]) =
    if repoBase == null then throw new IllegalArgumentException("repoBase must not be null")
    val groupId = info.organization
    val artifactId = mavenModuleArtifactId(info.packageName, info)
    val version = info.version
    val groupPath = groupId.replace('.', '/')
    val versionDir = new File(repoBase, s"$groupPath/$artifactId/$version")
    val path = mavenArtifact(
      info,
      artifactId,
      version,
      extension,
      classifier,
      Some(p => mavenModuleArtifactId(p, info)),
    )
    val targetFile = new File(repoBase, path.replace('/', File.separatorChar))
    s"file:${targetFile.absolutePath}"

  // loosely based on sbt's ivylessPublishMavenToUrl 649
  private def ivylessMaven(info: SubstituteInfo, baseUrl: String, extension: String, classifier: Option[String]) = {
    val base = baseUrl.stripSuffix("/") + "/"
    val artifactId = mavenModuleArtifactId(info.packageName, info)
    val fileVersion = Option.when(info.version.endsWith("-SNAPSHOT"))(classifier).flatten.getOrElse(info.version)
    val path = mavenArtifact(info, artifactId, fileVersion, extension, classifier, Some(p => mavenModuleArtifactId(p, info)))
    base + path
  }

  // 821
  private def mavenLayoutPath(
                               groupId: String,
                               moduleArtifactId: String,
                               artifactName: String,
                               version: String,
                               fileVersion: String,
                               extension: String,
                               classifier: Option[String]
                             ): String =
    val groupPath = groupId.replace('.', '/')
    val classifierPart = classifier.map("-" + _).getOrElse("")
    val fileName = s"$artifactName-$fileVersion$classifierPart.$extension"
    s"$groupPath/$moduleArtifactId/$version/$fileName"

  // loosely based on sbt's mavenArtifacts 834
  private def mavenArtifact(info: SubstituteInfo,
                            moduleArtifactId: String,
                            fileVersion: String,
                            extension: String,
                            classifier: Option[String],
                            artifactNameCross: Option[String => String]
                           ): String =
    val artifactName = artifactNameCross.fold(info.packageName)(_(info.packageName))
    mavenLayoutPath(
      info.organization,
      moduleArtifactId,
      artifactName,
      info.version,
      fileVersion,
      extension,
      classifier,
    )

}