#!/usr/bin/env bash

sed 's/,/;,/g' results.csv | column -t -e -s\; | sed 's/ ,/,/g' > temp && mv temp results.csv
