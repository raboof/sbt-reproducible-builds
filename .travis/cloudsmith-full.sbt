/*
 * Example of some local configuration you can use as a rebuilder
 * (either 'official' or 3rd-party) to publish just the buildinfo
 * to a place you control, such as (in this case) my Cloudsmith
 * repo at https://cloudsmith.io/~raboof/repos/buildinfos/packages/
 */

import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.ReproducibleBuilds

val repo = "Cloudsmith API" at "https://maven.cloudsmith.io/raboof/sbt-reproducible-builds/"

resolvers += repo

publishTo := Some(repo)
pomIncludeRepository := { x => false }

credentials += Credentials(
  realm = "Cloudsmith API",
  host = "maven.cloudsmith.io",
  userName = "raboof-owner",
  passwd = sys.env("CLOUDSMITH_API_KEY")
)
