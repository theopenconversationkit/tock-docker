#!/usr/bin/env bash
echo "Release target: $1"
mvn release:clean release:prepare -Ddeploy -Dtock=$1
mvn release:perform -Ddeploy