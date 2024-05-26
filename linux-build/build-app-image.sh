#!/bin/bash
# Run this script from the project root

export JAVA_HOME=~/.jdks/corretto-19.0.2/
./gradlew composeApp:packageAppImage
rm -rf ./linux-build/LANd.AppDir/usr/
mkdir ./linux-build/LANd.AppDir/usr
mv composeApp/build/compose/binaries/main/app/LANd/bin ./linux-build/LANd.AppDir/usr/
mv composeApp/build/compose/binaries/main/app/LANd/lib ./linux-build/LANd.AppDir/usr/
./linux-build/appimagetool-aarch64.AppImage ./linux-build/LANd.AppDir