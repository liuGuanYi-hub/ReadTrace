#!/usr/bin/env bash
# 阅痕 发现页验证脚本：稳健导航 + UI dump + 日志抓取
# 用法：bash tools_verify_discover.sh
ADB="F:/Android/sdk/platform-tools/adb.exe"
APK="app/build/outputs/apk/debug/app-debug.apk"

wait_activity() { # $1 目标 Activity 关键字 $2 超时秒
  local target="$1"; local timeout="${2:-15}"; local i=0
  while [ $i -lt $((timeout*2)) ]; do
    if $ADB shell "dumpsys window | grep mCurrentFocus" | grep -q "$target"; then return 0; fi
    sleep 0.5; i=$((i+1))
  done
  return 1
}

tap_until() { # $1 x $2 y $3 目标 Activity $4 重试次数
  local x=$1 y=$2 target=$3 tries=${4:-5}
  for ((n=0;n<tries;n++)); do
    $ADB shell input tap $x $y >/dev/null
    if wait_activity "$target" 4; then return 0; fi
  done
  return 1
}

echo "== 安装 =="
$ADB install -r "$APK" >/dev/null 2>&1 && echo "installed"
$ADB logcat -c >/dev/null 2>&1

echo "== 启动主界面 =="
$ADB shell am force-stop com.example.readtrace >/dev/null
$ADB shell am start -n com.example.readtrace/.MainActivity >/dev/null
wait_activity ".MainActivity" 25 || { echo "MainActivity 未启动"; exit 1; }
sleep 3

echo "== 藏库 -> 添加页 -> 发现页 =="
tap_until 346 2250 ".MainActivity" 3   # 切到藏库（仍在 MainActivity）
sleep 2
tap_until 901 210 ".AddBookActivity" 6 || { echo "未进入添加页"; exit 1; }
sleep 2
tap_until 540 410 ".DiscoverActivity" 6 || { echo "未进入发现页"; exit 1; }
echo "已进入发现页，等待网络加载..."
sleep 12

echo "== UI dump =="
$ADB shell uiautomator dump --compressed /sdcard/ui_disc.xml >/dev/null
$ADB pull /sdcard/ui_disc.xml /tmp/ui_verify.xml >/dev/null 2>&1

echo "== 网络日志 =="
$ADB logcat -d | grep "BangumiApi" | tail -5
echo "(无日志 = 请求未触发或成功但无异常)"
