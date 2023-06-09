#!/bin/bash

#
# Copyright (c) 2023 by MILOSZ GILGA <http://miloszgilga.pl>
#
# File name: jar-kill.sh
# Last modified: 19/05/2023, 22:04
# Project name: air-hub-master-server
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
# file except in compliance with the License. You may obtain a copy of the License at
#
#     <http://www.apache.org/license/LICENSE-2.0>
#
# Unless required by applicable law or agreed to in writing, software distributed under
# the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
# OF ANY KIND, either express or implied. See the License for the specific language
# governing permissions and limitations under the license.
#

if [ -n "$1" ]; then
    cd "$1" || exit 0
fi

EXEC_JAR_FILE_NAME="air-hub-master-server-[0-9]\.[0-9]\.[0-9]\.jar"
EXEC_JAR_FILE_NAME=$(find . -name "$EXEC_JAR_FILE_NAME" -exec  echo {} \;)

if [ "$EXEC_JAR_FILE_NAME" == "" ]; then
    echo "[bash kill script err] <> Executable JAR file not found in current directory"
    exit 1
fi

EXEC_JAR_FILE_NAME=${EXEC_JAR_FILE_NAME#./}
PID=$(pgrep -f "$EXEC_JAR_FILE_NAME")
if [ "$PID" == "" ]; then
    echo "[bash kill script info] <> Process on proxy domain is not running"
    exit 0
fi

kill "$PID"
echo "[bash kill script info] <> Process on proxy domain with PID '$PID' was terminated"
