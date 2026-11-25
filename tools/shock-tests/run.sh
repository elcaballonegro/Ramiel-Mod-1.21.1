#!/bin/bash
# Unit tests for the shockwave maths (ShockMath) and tree finding (TreeScan). Needs a JDK. No Minecraft.
# Checks, among others, that the placed tree blocks equal what the renderer draws (no pop when a tree lands),
# that touching canopies stay separate trees, and that log houses / player leaves are never treated as trees.
set -e
here="$(cd "$(dirname "$0")" && pwd)"; root="$here/../.."
w="$(mktemp -d)"; mkdir -p "$w/src"
for f in ShockMath TreeScan; do sed 's/^package dev.ramiel.world;//' "$root/src/main/java/dev/ramiel/world/$f.java" > "$w/src/$f.java"; done
javac -d "$w/bin" "$here/org/joml/Quaternionf.java" "$w"/src/*.java "$here/ShockTest.java"
java -cp "$w/bin" ShockTest
