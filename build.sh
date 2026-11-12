#!/usr/bin/env sh
set -eu
java -version
mvn -B clean verify
echo "Built: target/GawharaMissions-1.0.3.jar"
