# Contributing to sbt-reproducible-builds

Contributions to sbt-reproducible-builds are much appreciated!

You're welcome to just open a PR. When you're planning larger changes you might
want to open an issue and discuss the idea first, to avoid disappointment
later.

## Applying code style to the project

The project uses both [scalafix](https://scalacenter.github.io/scalafix/) and
[scalafmt](https://scalameta.org/scalafmt/) to ensure code quality which is automatically checked on every
PR. If you would like to check for any potential code style problems locally you can run `sbt checkCodeStyle` and if
you want to apply the code style then you can run `sbt applyCodeStyle`.

## Ignoring formatting commits in git blame

Throughout the history of the codebase various formatting commits have been applied as the scalafmt style has evolved over time, if desired
one can setup git blame to ignore these commits. The hashes for these specific are stored in [this file](.git-blame-ignore-revs) so to configure
git blame to ignore these commits you can execute the following.

```shell
git config blame.ignoreRevsFile .git-blame-ignore-revs
```
