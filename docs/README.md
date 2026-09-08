# 阅痕 ReadTrace 文档总览与导航 (Documentation Hub)

欢迎查阅《阅痕 ReadTrace》官方文档中心。为了保障项目工程规范性与团队协作效率，所有项目设计、技术架构、版本说明与数据资产均已进行结构化分门归档。

---

## 📂 目录结构全景

```text
docs/
├── README.md                 # [本文件] 文档中心全景索引与查阅指南
├── architecture/             # 🏛️ 系统架构图、交互式拓扑与工程时序
│   ├── readtrace-architecture.html          # 交互式系统架构全景图 (支持深浅色/流光动效)
│   ├── dynamic-archify-architecture.html    # 架构动效交互页
│   ├── dynamic-archify-architecture.svg     # 4K 高清矢量架构图
│   └── dynamic-archify-architecture.gif     # 架构流光演进动图
├── exports/                  # 📦 数据资产导出包、全量备份与预设种子
│   ├── 用户数据导出_2026-09-07.csv/json/md   # 历史用户多格式导出样例
│   ├── 全作品数据导出.csv                    # 全品类作品离线数据表
│   ├── 书单.csv                             # 精选书单种子
│   └── presets/                             # 219部预设作品富内容及导入指引
│       ├── readtrace_full_backup.json       # 全量存档合并包 (Sovereign Backup)
│       ├── preset_*.csv                     # 各媒介（书籍/动漫/影视/游戏）骨架清单
│       ├── rich_content_*.json              # 角色谱/语录/章节大纲等富内容包
│       └── 导入说明.md                       # 应用内导入与恢复完整指引
├── releases/                 # 🚀 官方正式与阶段性发布说明 (Release Notes)
│   ├── RELEASE_NOTES_v1.0.11.md             # 修复预置评分散布与多媒介评分适配
│   ├── RELEASE_NOTES_v1.0.10.md             # 全新安装评分算法修正与冷启动优化
│   ├── RELEASE_NOTES_v1.0.8.md              # 账号数据主权、全量存档恢复与富内容自动建库
│   ├── RELEASE_NOTES_v1.0.7.md              # 3D 黑胶/磁带视听联动与触觉马达引擎
│   ├── RELEASE_NOTES_v1.0.5.md              # 情绪等高线拓扑与五媒介藏库升级
│   ├── RELEASE_NOTES_v1.0.0.md              # 初代核心架构奠基与基础策展系统
│   └── RELEASE_NOTES_v6.0.0.md              # 微信小程序端同步演化说明
├── specs/                    # 📑 产品规划、功能设计与核心技术规格
│   ├── 开发进度.md                           # 完整迭代日志、技术演进与需求清单
│   ├── 安卓开发文档.md                       # Android 端工程架构、核心类说明与开发规范
│   ├── 外部导入功能设计与计划.md              # 豆瓣/Bangumi/Steam 多源导入技术方案
│   ├── 纪念功能设计与计划.md                 # 护照盖章、票根、藏书票与 3D 展厅规划
│   └── 认证正式化接入指南.md                 # 微信鉴权与阿里云短信通道配置指南
├── screenshots/              # 📸 项目 README 视觉画廊高保真实机截图
└── pic/                      # 🧪 本地测试与多端验证临时截图
```

---

## 🧭 模块快速直达

### 1. 🚀 版本发布纪要 (Release Notes)
| 版本 | 发布核心亮点 | 说明文档 |
| :--- | :--- | :--- |
| **v1.0.11** | 修复全新安装评分散布、离散档位平滑过渡、多媒介评分适配 | [查阅 v1.0.11 说明](releases/RELEASE_NOTES_v1.0.11.md) |
| **v1.0.10** | 冷启动优化、评分区间联动与藏库过滤增强 | [查阅 v1.0.10 说明](releases/RELEASE_NOTES_v1.0.10.md) |
| **v1.0.8** | 数据主权备份（Sovereign Backup）、富内容本地一键导入恢复 | [查阅 v1.0.8 说明](releases/RELEASE_NOTES_v1.0.8.md) |
| **v1.0.7** | 3D 拟真黑胶唱机/复古磁带卡座、物理线性马达触觉反馈与空间音频 | [查阅 v1.0.7 说明](releases/RELEASE_NOTES_v1.0.7.md) |
| **v1.0.5** | 3D 情绪等高线拓扑图、五媒介多维藏库与极光流光边框 | [查阅 v1.0.5 说明](releases/RELEASE_NOTES_v1.0.5.md) |
| **v1.0.0** | 原生 Android 策展级记录台首发奠基 | [查阅 v1.0.0 说明](releases/RELEASE_NOTES_v1.0.0.md) |

### 2. 🏛️ 系统架构与拓扑 (Architecture)
- 🌐 **[系统架构交互全景图 (HTML)](architecture/readtrace-architecture.html)**：支持深色/浅色自适应切换、数据流光动态演进与 4K 超清矢量导出。
- 🖼️ **[架构动态演化 GIF](architecture/dynamic-archify-architecture.gif)** 与 **[矢量 SVG](architecture/dynamic-archify-architecture.svg)**。

### 3. 📑 技术规格与规划 (Specs)
- 📌 **[开发进度与全景任务表](specs/开发进度.md)**：包含各阶段迭代记录、待办特性与技术债清理状态。
- 📱 **[安卓开发文档](specs/安卓开发文档.md)**：包含 Android Native 架构规范、数据层事务保护与 UI 渲染约定。
- 📦 **[外部导入功能设计与计划](specs/外部导入功能设计与计划.md)**：豆瓣、Bangumi、Steam、本地 CSV/JSON 解析管道设计。
- 🎴 **[纪念功能设计与计划](specs/纪念功能设计与计划.md)**：精神护照、透光票根、版画藏书票与 3D 展厅方案。
- 🔐 **[认证正式化接入指南](specs/认证正式化接入指南.md)**：微信登录、阿里云短信验证码与 WebDAV 增量校验规范。

### 4. 📦 数据资产与预设 (Exports)
- 📂 **[预设数据资产目录与导入说明](exports/presets/导入说明.md)**：全量预设 219 部作品富内容 JSON 与骨架 CSV。
- 📄 **[全作品数据导出 (CSV)](exports/全作品数据导出.csv)**：可直接通过 Excel / WPS 打开分析。
