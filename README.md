Experimental plugin to make sbt builds more reproducible.

See also: https://reproducible-builds.org/

Overrides the `packageBin` task to post-process the result and
apply the strippers from https://github.com/Zlika/reproducible-build-maven-plugin/

Usage
=====

* check out https://github.com/raboof/reproducible-build-maven-plugin and `mvn install`.
* Check out this repository and do `sbt publishLocal`
* Then add to your `project/plugins.sbt`:

    classpathTypes += "maven-plugin"
    addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.1-SNAPSHOT")

* And to build.sbt:

    lazy val root = (project in file("."))
        .enablePlugins(ReproducibleBuildsPlugin)

