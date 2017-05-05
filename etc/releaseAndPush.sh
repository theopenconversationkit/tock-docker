#!/usr/bin/env bash
echo "Release target: $1"
mvn release:clean release:prepare release:perform -Ddeploy -Dtock=$1