#!/usr/bin/env bash
# 最终闭环验证：榜单 → 预览 → 添加 → 防重复
ADB="F:/Android/sdk/platform-tools/adb.exe"

wait_activity() {
  local target="$1" timeout="${2:-15}" i=0
  while [ $i -lt $((timeout*2)) ]; do
    $ADB shell "dumpsys window | grep mCurrentFocus" 2>/dev/null | grep -q "$target" && return 0
    sleep 0.5; i=$((i+1))
  done
  return 1
}
tap_until() {
  local x=$1 y=$2 target=$3 tries=${4:-6}
  for ((n=0;n<tries;n++)); do
    $ADB shell input tap $x $y >/dev/null
    wait_activity "$target" 4 && return 0
  done
  return 1
}
dump_text() {
  for i in 1 2 3 4 5; do
    $ADB shell uiautomator dump --compressed /sdcard/u.xml >/dev/null 2>&1 && break
    sleep 2
  done
  $ADB pull /sdcard/u.xml /tmp/u_final.xml >/dev/null 2>&1
}

echo "== 重启应用 =="
$ADB shell am force-stop com.example.readtrace >/dev/null
$ADB shell am start -n com.example.readtrace/.MainActivity >/dev/null
wait_activity ".MainActivity" 25 || exit 1
sleep 3

echo "== 藏库 -> +记录 -> 发现页 =="
tap_until 346 2250 ".MainActivity" 3
sleep 2
tap_until 901 210 ".AddBookActivity" 6 || exit 1
sleep 2
tap_until 540 410 ".DiscoverActivity" 6 || exit 1
sleep 8
dump_text
python - <<'EOF'
import re
xml = open('/tmp/u_final.xml', encoding='utf-8').read()
for m in re.finditer(r'text="([^"]+)"[^>]*?resource-id="([^"]*)"[^>]*?bounds="(\[[^\]]+\])"', xml):
    t, rid, b = m.groups()
    r = rid.split('/')[-1]
    if r in ('discoverModeTitle','itemDiscoverTitle'):
        print('LIST|', r, '->', repr(t)[:45], b)
EOF
echo "== 点第一张卡 =="
$ADB shell input tap 290 1150
sleep 4
dump_text
python - <<'EOF'
import re
xml = open('/tmp/u_final.xml', encoding='utf-8').read()
for m in re.finditer(r'text="([^"]+)"[^>]*?resource-id="([^"]*)"[^>]*?bounds="(\[[^\]]+\])"', xml):
    t, rid, b = m.groups()
    r = rid.split('/')[-1]
    if r.startswith('preview'):
        print('PREVIEW|', r, '->', repr(t)[:60], b)
EOF
