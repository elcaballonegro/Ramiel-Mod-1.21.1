#!/bin/bash
# Preview and test Ramiel's brick model WITHOUT Minecraft. Needs: JDK 17+, python3 with Pillow.
#   tools/model-harness/run.sh check                      -> NaN / pop test over every form-to-form morph
#   tools/model-harness/run.sh forms out.png              -> contact sheet of every form
#   tools/model-harness/run.sh morph FROM TO out.png [n]  -> n frames of a transformation (e.g. morph CROSS DORMANT out.png 12)
# Forms: DORMANT SHIELD DRILL LANCE CROSS SUNBURST SENTINEL STAR URCHIN
set -e
here="$(cd "$(dirname "$0")" && pwd)"; root="$here/../.."
w="$(mktemp -d)"; mkdir -p "$w/dev/ramiel/client/model"
cp "$root/src/main/java/dev/ramiel/Form.java" "$w/dev/ramiel/"
for f in Brick FormPoses RamielModel; do cp "$root/src/main/java/dev/ramiel/client/model/$f.java" "$w/dev/ramiel/client/model/"; done
javac -d "$w/bin" "$here/org/joml/Quaternionf.java" "$w/dev/ramiel/Form.java" "$w"/dev/ramiel/client/model/*.java "$here/Dump.java" "$here/Check.java"
case "$1" in
  check) java -cp "$w/bin" Check ;;
  forms) java -cp "$w/bin" Dump "$w/f.jsonl" static:DORMANT:0:0 static:LANCE:0:0 static:CROSS:0:0 static:SUNBURST:0.6:0 static:SENTINEL:0:0 static:STAR:1:0 static:URCHIN:0:0 static:DRILL:0:0:3
         python3 "$here/render.py" "$w/f.jsonl" "$2" 4 size=330 dist=5.6 yaw=20 pitch=10 ;;
  morph) n="${5:-12}"; java -cp "$w/bin" Dump "$w/m.jsonl" "morph:$2:$3:$n:0:0"
         python3 "$here/render.py" "$w/m.jsonl" "$4" 4 size=300 dist=6.2 yaw=22 pitch=14 ;;
  *) echo "usage: run.sh check | forms out.png | morph FROM TO out.png [frames]" ;;
esac
