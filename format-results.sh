#!/usr/bin/env bash

sed 's/,/;,/g' results.csv | column -t -s\; | sed 's/ ,/,/g' > temp && mv temp results.csv
