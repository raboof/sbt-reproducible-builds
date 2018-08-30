# sbt-reproducible-builds

[sbt](https://www.scala-sbt.org) plugin to make sbt builds more reproducible.

See also: https://reproducible-builds.org/

This plugin includes a couple of features:

 * Strip 'accidental' sources of nondeterminism (e.g. timestamps) from the packaged jar
 * Produce a (signed) ['buildinfo'](https://reproducible-builds.org/docs/recording/) file describing the build environment used and containing a cryptographic hash of the resulting artifacts.
 * Uploading this certification to a [reproducible-builds-certification-repository](http://github.com/raboof/reproducible-builds-certification-repository) instance
 * Checking your local build against already-uploaded certifications

## Usage

### Stripping nondeterminism from artifacts

Then add to your `project/plugins.sbt`:

```
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.15")
```

And to `build.sbt`:

```
enablePlugins(ReproducibleBuildsPlugin)
```

### Describe your build as a 'buildinfo' certification

You can now generate a signed description of the build environment with the
sbt task `signedReproducibleBuildsCertification`.

### Sharing certifications

Upload your certification to a
[reproducible-builds-certification-repository](http://github.com/raboof/reproducible-builds-certification-repository) instance
with `reproducibleBuildsUploadCertification`.

#### Uploading certifications from Travis

Especially if you're already deploying from Travis, it can be a great start to
publish certifications from Travis as well. For this, you should give Travis
its own gpg key pair to sign those certifications.

Start by generating a keypair with `gpg --gen-key`, naming it something like
"Arnout Engelen (via Travis) <arnout@bzzt.net>". Then export public and private
key with `gpg --export -a "Arnout Engelen (via Travis)" > public.key` and
`gpg --export-secret-key -a "Arnout Engelen (via Travis)" > private.key`.
Now encrypt the private key so only Travis can read it with
`travis encrypt-file .travis/private.key` and follow the instructions to
unencrypt. Finally, `gpg --import private.key public.key` and
`sbt reproducibleBuildsUploadCertification` to sign and upload the
certification from Travis.



### Checking certifications

Check your certification with other uploaded
certifications with `reproducibleBuildsCheckCertification`

## Drinking our own champagne

From version 0.3 onwards, `sbt-reproducible-builds` should itself be
reproducible in the sense that building the same git tag should produce the
exact same binary.

When this is not the case, this is to be considered a bug and a bug report with
the binary and information about any peculiarities of the build system would be
greatly appreciated!
