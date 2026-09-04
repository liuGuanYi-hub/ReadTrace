# 阅痕 ReadTrace v1.0.8 — 全量存档一次导入与账号数据主权发布说明 📤🔑

[![Platform](https://img.shields.io/badge/Platform-Android%2012%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-v1.0.8-3A6348.svg)](https://github.com/liuGuanYi-hub/ReadTrace/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **版本定位**：v1.0.8 聚焦「数据主权」——219 部预设作品的富内容（角色谱/语录/章节大纲）全量补至 100%，新增**全量存档合并包**「一个账号 = 一个 JSON」一次导入全部恢复；同时上线**打字验证清空账号数据**，换机 / 换账号 / 从零重导一条龙闭环。

---

## 📤 1. 全量存档合并包 · 一次导入全部到位

| 能力 | 说明 |
|:---|:---|
| 📦 一个账号一个 JSON | `readtrace_full_backup.json`（219 部）在应用内「📥」→「🎨 选择本地富内容 JSON 文件...」一次导入，全部作品与角色谱/语录/大纲即刻恢复 |
| 🏷️ 条目级媒介标记 | 合并包每条作品内嵌 `media` 标记，逐条准确归档到动漫/书籍/游戏/影视/音乐，不受文件名影响 |
| 🔁 幂等安全 | 重复导入、导入旧版均不覆盖已有内容与手动修改 |

## 🎨 2. 富内容 JSON 本地导入 · 自动建库

- 本地 `rich_content_*.json` 导入时，藏库缺失的作品**自动创建最小骨架**（媒介按条目标记 / 文件名推断，非标准命名默认书籍），富内容随建库一并写入；
- 已有作品按标题精确匹配 → 标题前缀匹配（短标题防误中）补齐，创作者/评分等留空可后补；
- assets 自动播种路径语义不变（`DATABASE_VERSION` 升级 / 全新安装依旧自动生效）。

## 🗑️ 3. 清空账号数据 · 打字二次验证

- 入口：我的 → 备份与数据管理 → 底部「⚠️ 危险区」→「🗑️ 清空账号数据」；
- 必须完整输入「**我确定删除账号数据**」确认按钮才解禁，误触不可能执行；
- 物理清空全部作品、笔记、阅读打卡、角色谱、章节大纲、空间地标、六维心智模型与黑胶关联曲目，并清理本地封面缓存；
- 同数据库版本内**不触发预设重播种**——清空后是干净空库，正好配合合并包从零重导。

## 🌐 4. 219 部预设富内容全量补全

- AI 批量补全完成：anime 71 / books 52 / games 69 / movies_podcasts 27，全部条目角色谱（≥2 人）与语录齐备，218 部具备 ≥2 章大纲；
- 快照同步至 `docs/预设数据导出/`（骨架 CSV + 富内容 JSON + 合并包 + 导入说明）。

## 🧪 5. 质量与验证报告

- **设备端测试 9/9 全绿**（Medium_Phone / API 36）：合并包 219/219 全量匹配、导入幂等、自动建库、媒介推断（含 music）、清空归零、清空后重导闭环；
- **构建**：`assembleRelease` BUILD SUCCESSFUL（versionCode 44 / versionName 1.0.8）；
- **Git**：功能提交（`838fc2d`、`5895360`）与升版提交均已推送 origin/main；
- **架构兼容**：Android 12+ (API 31 ~ 37)。
