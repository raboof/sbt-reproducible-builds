sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % sys.props("plugin.version"))
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

// Included but not used, to catch problems with that combination:
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.24")

// to easily test sbt-gpg integration manually
addSbtPlugin("io.crashbox" % "sbt-gpg" % "0.2.0")
