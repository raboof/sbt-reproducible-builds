package net.bzzt.reproduciblebuilds

import org.apache.ivy.core.module.descriptor.{
  Artifact => IArtifact,
  DefaultModuleDescriptor,
  License,
  MDArtifact,
  ModuleDescriptor
}
import org.apache.ivy.core.module.id.ModuleRevisionId
import sbt.librarymanagement.ScalaModuleInfo
import sbt.{Artifact, ConfigRef, Configuration, CrossVersion, ExclusionRule, InlineConfiguration, ModuleID, ModuleInfo}

/** These functions where copied from sbt-library-management (see https://github.com/sbt/librarymanagement) since they
  * are package private. I
  */
private[reproduciblebuilds] object SbtLibraryManagementFunctions {
  def javaMap(m: Map[String, String], unqualify: Boolean = false) = {
    import scala.collection.JavaConverters._
    val map = if (unqualify) m map { case (k, v) => (k.stripPrefix("e:"), v) }
    else m
    if (map.isEmpty) null else map.asJava
  }

  def extra(artifact: Artifact, unqualify: Boolean = false): java.util.Map[String, String] = {
    val ea = artifact.classifier match {
      case Some(c) => artifact.extra("e:classifier" -> c);
      case None    => artifact
    }
    javaMap(ea.extraAttributes, unqualify)
  }

  def copyConfigurations(artifact: Artifact,
                         addConfiguration: ConfigRef => Unit,
                         allConfigurations: Vector[ConfigRef]
  ): Unit = {
    val confs =
      if (artifact.configurations.isEmpty) allConfigurations
      else artifact.configurations
    confs foreach addConfiguration
  }

  def toIvyArtifact(moduleID: ModuleDescriptor, a: Artifact, allConfigurations: Vector[ConfigRef]): MDArtifact = {
    val artifact = new MDArtifact(moduleID, a.name, a.`type`, a.extension, null, extra(a, false))
    copyConfigurations(
      a,
      (ref: ConfigRef) => artifact.addConfiguration(ref.name),
      allConfigurations
    )
    artifact
  }

  /** Converts the given sbt module id into an Ivy ModuleRevisionId. */
  def toID(m: ModuleID) = {
    import m._
    ModuleRevisionId.newInstance(
      organization,
      name,
      branchName.orNull,
      revision,
      javaMap(extraAttributes)
    )
  }

  def mapArtifacts(moduleID: ModuleDescriptor, artifacts: Seq[Artifact]): Seq[IArtifact] = {
    lazy val allConfigurations = moduleID.getPublicConfigurationsNames.toVector map ConfigRef.apply
    for (artifact <- artifacts) yield toIvyArtifact(moduleID, artifact, allConfigurations)
  }

  /** This method is used to add inline artifacts to the provided module. */
  def addArtifacts(moduleID: DefaultModuleDescriptor, artifacts: Iterable[Artifact]): Unit =
    for (art <- mapArtifacts(moduleID, artifacts.toSeq); c <- art.getConfigurations)
      moduleID.addArtifact(c, art)

  def addConfigurations(mod: DefaultModuleDescriptor, configurations: Iterable[Configuration]): Unit =
    configurations.foreach(config => mod.addConfiguration(toIvyConfiguration(config)))

  def toIvyConfiguration(configuration: Configuration) = {
    import org.apache.ivy.core.module.descriptor.{Configuration => IvyConfig}
    import IvyConfig.Visibility._
    import configuration._
    new IvyConfig(
      name,
      if (isPublic) PUBLIC else PRIVATE,
      description,
      extendsConfigs.map(_.name).toArray,
      transitive,
      null
    )
  }

  def substituteCross(ic: InlineConfiguration): InlineConfiguration =
    ic.scalaModuleInfo match {
      case None     => ic
      case Some(is) => substituteCross(ic, is.scalaFullVersion, is.scalaBinaryVersion)
    }

  def applyCross(s: String, fopt: Option[String => String]): String =
    fopt match {
      case None       => s
      case Some(fopt) => fopt(s)
    }

  def substituteCross(exclude: ExclusionRule, is: Option[ScalaModuleInfo]): ExclusionRule = {
    val fopt: Option[String => String] =
      is flatMap { i =>
        CrossVersion(exclude.crossVersion, i.scalaFullVersion, i.scalaBinaryVersion)
      }
    exclude.withName(applyCross(exclude.name, fopt))
  }

  private def substituteCross(ic: InlineConfiguration,
                              scalaFullVersion: String,
                              scalaBinaryVersion: String
  ): InlineConfiguration = {
    val applyCross = CrossVersion(scalaFullVersion, scalaBinaryVersion)

    def propagateCrossVersion(moduleID: ModuleID): ModuleID = {
      val crossExclusions: Vector[ExclusionRule] =
        moduleID.exclusions.map(substituteCross(_, ic.scalaModuleInfo))
      applyCross(moduleID)
        .withExclusions(crossExclusions)
    }

    ic.withModule(applyCross(ic.module))
      .withDependencies(ic.dependencies.map(propagateCrossVersion))
      .withOverrides(ic.overrides map applyCross)
  }
  def newConfiguredModuleID(module: ModuleID, moduleInfo: ModuleInfo, configurations: Iterable[Configuration]) = {
    val mod = new DefaultModuleDescriptor(toID(module), "release", null, false)
    mod.setLastModified(System.currentTimeMillis)
    mod.setDescription(moduleInfo.description)
    moduleInfo.homepage foreach { h =>
      mod.setHomePage(h.toString)
    }
    moduleInfo.licenses foreach { l =>
      mod.addLicense(new License(l._1, l._2.toString))
    }
    addConfigurations(mod, configurations)
    addArtifacts(mod, module.explicitArtifacts)
    mod
  }

  def appendSbtCrossVersion(ic: InlineConfiguration): InlineConfiguration =
    ic.withModule(appendSbtCrossVersion(ic.module))
      .withDependencies(ic.dependencies.map(appendSbtCrossVersion))
      .withOverrides(ic.overrides.map(appendSbtCrossVersion))

  def appendSbtCrossVersion(mid: ModuleID): ModuleID = {
    val crossVersion = for {
      scalaVersion <- mid.extraAttributes.get("e:scalaVersion")
      sbtVersion <- mid.extraAttributes.get("e:sbtVersion")
    } yield s"_${scalaVersion}_$sbtVersion"
    crossVersion
      .filter(!mid.name.endsWith(_))
      .map(cv => mid.withName(mid.name + cv))
      .getOrElse(mid)
  }

  def resolvePattern(base: String, pattern: String): String = {
    val normBase = base.replace('\\', '/')
    if (normBase.endsWith("/") || pattern.startsWith("/")) normBase + pattern
    else normBase + "/" + pattern
  }

}
