#!/bin/sh

set -e

rm -r report || true
mkdir report

find . -name "reproducible-builds-report.md" | sort | xargs cat > report/index.md
find . -name "reproducible-builds-diffoscope-output-*" | sort | while read line ; do cp -r $line report ; done

markdown report/index.md > report/index.html

