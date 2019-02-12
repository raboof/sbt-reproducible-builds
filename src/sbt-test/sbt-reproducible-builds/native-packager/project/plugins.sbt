sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % sys.props("plugin.version"))
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.18")
