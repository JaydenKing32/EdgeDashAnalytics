#!/usr/bin/env bash

sed 's/,/;,/g' results.csv | sed 's/^;,/!;,/g' | column -t -e -s\; | sed 's/  ,/,/g' | sed 's/^!/ /g' > temp && mv temp results.csv
