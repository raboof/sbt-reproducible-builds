For now releasing is a matter of:
* checkout clean master
* update version in build.sbt
* set BINTRAY_USER and BINTRAY_PASS (to the bintray token, not password)
* 'sbt ^publish'
* update version in build.sbt to next snapshot
* commit, tag and push

We consciously don't commit a version with a non-SNAPSHOT version, as you might
forget to update that when forking from a tag. That's ironic, as that makes
sbt-reproducible-builds less reproducible :). In the future we should:
* infer the version from the tag with something like sbt-dynver
* publish from Travis.
* make sbt-reproducible-builds use sbt-reproducible-builds :)
