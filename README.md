Experimental plugin to make sbt builds more reproducible.

See also: https://reproducible-builds.org/

Overrides the `packageBin` task to post-process the result and
apply the strippers from https://github.com/Zlika/reproducible-build-maven-plugin/

Usage
=====

Then add to your `project/plugins.sbt`:

![](https://pi.bzzt.net/test.svg)

And to `build.sbt`:

```
enablePlugins(ReproducibleBuildsPlugin)
```

Drinking our own champagne
==========================

From version 0.3 onwards, `sbt-reproducible-builds` should itself be
reproducible in the sense that building the same git tag should produce the
exact same binary.

When this is not the case, this is to be considered a bug and a bug report with
the binary and information about any peculiarities of the build system would be
greatly appreciated!
