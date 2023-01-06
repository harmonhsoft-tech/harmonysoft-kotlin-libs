#!/bin/bash

#./gradlew clean build

function execute {
  COMMAND=$1
  echo "executing $COMMAND"
  $COMMAND
  RES=$?
  if [ $RES -ne 0 ]
  then
    echo "stopping the execution because the following command failed: $COMMAND"
    exit 1
  fi
}

execute "./gradlew build"
execute "./gradlew :harmonysoft-http-mock-server-cucumber:cucumber"
execute "./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository"