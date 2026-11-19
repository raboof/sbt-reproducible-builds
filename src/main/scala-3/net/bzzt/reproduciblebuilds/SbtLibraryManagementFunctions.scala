package net.bzzt.reproduciblebuilds

/** These functions where copied from sbt (see https://github.com/sbt/sbt)
  */
private[reproduciblebuilds] object SbtLibraryManagementFunctions {
  def resolvePattern(base: String, pattern: String): String =
    val normBase = base.replace('\\', '/')
    if normBase.endsWith("/") || pattern.startsWith("/") then normBase + pattern
    else normBase + "/" + pattern
} 
