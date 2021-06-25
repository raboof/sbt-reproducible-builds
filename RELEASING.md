For now releasing is a matter of:
* update version in README.md
* wait for travis builds to complete
* tag

Tagging should trigger the release from GH actions, and an independent
build and publish of just the buildinfo metadata from Travis.
