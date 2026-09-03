# 阅痕 ReadTrace v1.0.7 (P35) — 主页分页化重塑与安装包减半发布说明 🏛️📦

[![Platform](https://img.shields.io/badge/Platform-Android%2012%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-v1.0.7-3A6348.svg)](https://github.com/liuGuanYi-hub/ReadTrace/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **版本定位**：承接 v1.0.5 的体验纵深，《阅痕 ReadTrace》在 v1.0.6 完成 **P35 主页分页化重塑（Phase 1）**——首屏回归"记录台独尊"的极简美学，探索内容整体下沉第二页；v1.0.7 依据产品取舍**移除 ISBN 扫码链路**，APK 由 44.5MB 减半至 **20.9MB**，安装更轻盈、权限更克制。

---

## 📦 1. v1.0.7 · 安装包减半与权限精简

| 变更 | 说明 |
|:---|:---|
| ⚖️ 移除扫码功能 | 删除 ISBN 扫码链路：`IsbnScannerActivity` + CameraX 预览 + ML Kit `barcode-scanning:17.3.0`（bundle 分发把原生库打进 4 个 ABI），同步移除 `CAMERA` 权限与快捷录入弹窗"📷 扫码"入口 |
| 🎯 安装包减半 | APK 体积 44.5MB → **21,894,564B（20.9MB）**：`lib/` 原生库归零、`classes2.dex` 回落到 2.83MB |
| 🧹 权限克制 | 仅保留 `INTERNET` / `VIBRATE`，无相机权限诉求，隐私声明更干净 |
| 🧪 全链路回归 | 移除后模拟器冒烟通过：安装 Success、MainActivity 启动无崩溃、crash buffer 为空 |

> 📌 设计取舍：快速录入仍可通过「高级录入」与「剪贴板嗅探」（复制书名/豆瓣/Bangumi 链接自动识别）完成，核心录入体验不受影响；`zxing` 二维码**生成**能力保留（深链微卡等场景）。

---

## 🏛️ 2. v1.0.6 · P35 主页分页化重塑 (Phase 1)

### 🎯 首屏独尊 · 清爽记录台
- 首屏不再堆砌多区块，改为**一张居中舒展的记录台**（headerPanel），撑满整屏呈现"今日记录"仪式感；
- Hero 大位的长随想（quote）**整段移除**，不再折叠/展开，页面更聚焦；
- 那年今日、晶体工坊胶囊、统计胶囊、收藏展厅等**全部下沉第二页**，滑动自然承接。

### ⭐ 我的最爱心选展厅入驻主页
- 主页第二页新增横滑**心选展厅**（金色 `NO.x` 角标封面带），展示策展人最爱藏品；
- 无收藏时整段自动隐藏；封面缺失走占位降级，不出现破图。

### 🎨 弹窗与滚动体验统一
- **导入书单弹窗**弃用系统 `AlertDialog`，统一为玻璃拟态容器（行式选项渐入 + 弹性触感 + 金色胶囊按钮）；
- **回收站 / 主页**滚动条静默隐藏，滚动区域视觉纯净。

---

## 🧪 3. 质量与验证报告

- **构建**：`assembleRelease` BUILD SUCCESSFUL（versionCode 43 / versionName 1.0.7）；
- **冒烟**：模拟器冷启动 → `adb install -r ReadTrace_1.0.7.apk` Success → MainActivity 进程存活 → `dumpsys` 确认 versionCode=43 / versionName=1.0.7 → `logcat -b crash` 为空；
- **Git**：移除扫码（`b00e19d`）、文档记录（`909a926`）、升版（`33279cd`）等提交全部推送 origin/main；
- **架构兼容**：Android 12+ (API 31 ~ 37)。
