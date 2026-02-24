#!/bin/bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")
export ANDROID_HOME="$HOME/Library/Android/sdk"

./gradlew assembleDebug "$@"
