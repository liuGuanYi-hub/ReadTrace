# 阅痕 ReadTrace — 个人精神文化印记与 3D 虚拟展厅

> **Android 原生开发 · OpenGL ES 3D 展厅 · 3D 情绪等高线拓扑 · 3D 时空穿梭虫洞 · 3D 拟真黑胶/磁带播放器 · 线性马达触觉引擎 · 双耳空间音频 · 陀螺仪全息视差 · 极光流体着色器 · 3D 拟真翻书 · Glance 桌面小组件 · 纯本地数据掌控**

[![Build CI](https://github.com/liuGuanYi-hub/ReadTrace/actions/workflows/ci.yml/badge.svg)](https://github.com/liuGuanYi-hub/ReadTrace/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/liuGuanYi-hub/ReadTrace?color=3A6348&logo=github)](https://github.com/liuGuanYi-hub/ReadTrace/releases)
[![Android](https://img.shields.io/badge/Platform-Android%2012%2B-green.svg?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%20100%25-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

《阅痕 ReadTrace》是一个专为爱书人、影迷、ACGN 爱好者与深度思考者打造的 **个人精神文化印记空间与先锋美学虚拟展厅**。它打破了传统记录工具的扁平刻板，融合了 **美术馆策展级 Bento Grid 布局、3D 高斯势能等高线地形图、3D 时空穿梭虫洞、3D 拟真黑胶唱机与磁带卡座、物理线性马达触觉引擎、陀螺仪双耳空间音频、3D 陀螺仪全息光影与 Glance 桌面微缩视窗**，让每一次翻阅、追番、观影与通关都成为一场触手可及的艺术漫游。

---

## 📸 先锋四大创意与核心视效画卷（真机实测截图）

### 1. 🌟 先锋四大创新模块 (P1 ~ P4 重大系统)
| 💽 3D 拟真黑胶唱机与磁带卡座 | 🌌 3D 时空穿梭隧道与流光胶囊 | 🗺️ 3D 情绪等高线心智地形图 | 🛂 精神巡礼护照与白金签戳 |
|:---:|:---:|:---:|:---:|
| ![](docs/screenshots/28_vinyl_cassette_player.png) | ![](docs/screenshots/29_time_warp_tunnel.png) | ![](docs/screenshots/30_mindprint_topology_3d.png) | ![](docs/screenshots/25_cultural_passport.png) |

### 2. 🏛️ 美术馆策展级 Bento Grid 与极光流体光影
| 🏛️ 策展焦点大卡与双副卡 | 🍱 四大多元媒介长廊 | 📚 精神藏库多维筛选流 | 🎟️ 纪念创享工坊大厅 |
|:---:|:---:|:---:|:---:|
| ![](docs/screenshots/17_curatorial_bento_hub.png) | ![](docs/screenshots/18_curatorial_media_bento.png) | ![](docs/screenshots/21_library_shelf.png) | ![](docs/screenshots/24_memoir_hub.png) |

### 3. 🎴 3D 陀螺仪全息视差、文化护照与实体纪念物
| 🎟️ 复古电影透光撕票票根 | 🕹️ 游戏白金全息实体卡带 | 📖 共享元素转场书籍详情 | 🌌 211 颗认知星辰全景 |
|:---:|:---:|:---:|:---:|
| ![](docs/screenshots/26_movie_ticket_poster.png) | ![](docs/screenshots/27_game_cartridge_poster.png) | ![](docs/screenshots/20_book_detail_holographic.png) | ![](docs/screenshots/15_constellation_galaxy.png) |

### 4. 📖 3D 拟真纸张翻书阅读器与云端展览社区
| 📜 3D 拟真排版 (小王子) | 🌌 暗夜漫想深色主题 | 🌐 阅痕社区广场互访 | 🚀 策展与发布我的展厅 |
|:---:|:---:|:---:|:---:|
| ![](docs/screenshots/05_3d_reader_page1.png) | ![](docs/screenshots/07_3d_reader_night_theme.png) | ![](docs/screenshots/11_community_square.png) | ![](docs/screenshots/14_community_publish.png) |

---

## ✨ 核心先锋特性矩阵

### 1. 💽 3D 拟真黑胶唱机与复古磁带卡座系统 (P1)
- **3D 拟真黑胶转盘 (`VinylTurntableView`)**：铝合金底座、微沟槽碳纤维质感、中心 Cover Label 艺术图、双极各向异性径向同心圆高光、23° 金属唱臂物理落针/抬针。
- **复古透明磁带卡座 (`CassetteDeckView`)**：80 年代高透亚克力外壳、双六角白色自旋齿轮、供带轮/收带轮磁带厚度动态消长、复古网格手写标签贴纸与 A/B 面无缝翻转。
- **环形流体声波与歌词流淌 (`AudioVisualizerParticleView`)**：音频自发光反应粒子与羊皮纸金句歌词同步流淌。
- **收录夜鹿 (Yorushika) 与永远是深夜有多好 (ZUTOMAYO) 23~24 年高光曲目**（《晴る》《斜陽》《花に亡霊》《残機》等 16 首高光曲目）。

### 2. 🎧 触觉马达振动引擎与双耳空间音频联动系统 (P2)
- **物理线性马达专属触觉引擎 (`HapticFeedbackEngine`)**：
  - 🛂 `stampImpact`：精神护照盖印时的重沉打击感与高频微颤；
  - 🎟️ `ticketTearRipped`：电影票打孔连续 4 段撕裂齿轮顿挫震感；
  - 📖 `pageTurnRustle`：3D 纸张微摩擦触感；
  - 💽 `needleDropCrackle` / `cartridgeSnap`：黑胶落针微震与卡带卡扣弹跳；
  - 🌌 `celestialResonancePulse`：星系引力脉冲。
- **程序化 PCM 实时双耳空间音频 (`SpatialAudioEngine`)**：
  - 0MB 内存占用，实时数学正弦波与滤波白噪音合成；
  - 结合手机陀螺仪偏航角（Roll）动态计算左右声道增益因子（Binaural Panning）。

### 3. 🌌 3D 时空穿梭隧道与心智流光胶囊系统 (P3)
- **3D 透视时空虫洞 (`TimeWarpTunnelView`)**：基于相机视锥体 \(Scale = f / (f + Z)\) 真实三维透视投影，径向多边形时空环、5 色光速拉丝粒子束、Painter 算法倒序层级遮挡。
- **流光记忆胶囊 (`TimeWarpTunnelActivity`)**：自发光悬停胶囊，支持 1x 慢速漫游、5x 曲速跳跃、10x 光速折跃巡航，轻触胶囊全息裂变展开。

### 4. 🗺️ 3D 情绪拓扑与等高线心智地形图系统 (P4)
- **多峰复合高斯势能地形算法 (`MindprintTopologyView`)**：\(h(x, y) = \sum A_i \cdot e^{-\frac{(x - x_i)^2 + (y - y_i)^2}{2\sigma^2}}\)，将全量作品的心智五维雷达转化为 26x26 精神海拔起伏地貌。
- **三大先锋渲染模式**：3D 发光等高线 (`CONTOUR`)、空间立体线框 (`WIREFRAME`)、能量引力热力场 (`HEATMAP`)。
- **精神海拔等高切片推杆 (Elevation Slicer)**：0m ~ 8848m 实时地貌剖面切片分析。
- **巅峰水晶方尖碑信标与 1080P Ultra-HD 图谱海报**：高光山峰树立自发光信标，支持从任意作品详情一键「🗺️ 3D 地形」直达聚焦，并一键生成 1080x1440 典藏拓扑图谱海报分享。

### 5. 🎨 美术馆策展级 Bento Grid 与极光流体光影
- **美术馆策展级 Bento Grid 布局**：26sp 衬线大标、今日焦点大画幅浮雕展位、在读/专注非对称双副卡与羊皮纸灵感金句横幅。
- **陀螺仪重力感应全息高光掠影 (Gyroscope Hologram)**：基于 `Sensor.TYPE_ROTATION_VECTOR` 融合算法与低通滤波，实现倾斜手机时 3D 空间倾角透视与紫霞/冰蓝/纯白/金曜全息彩虹漫反射。
- **硬件加速极光流体着色器背景 (Dynamic Liquid Aurora Mesh)**：多核心正弦液体轨迹与 Android 12+ `RenderEffect.createBlurEffect` 深度交融。

### 6. 📱 Glance 桌面微缩视窗小部件
- **⏱️「今日阅读计时打卡」组件**：实时统计今日阅读分钟数与连胜天数，支持桌面一键「▶ 开始专注」。
- **📜「每日金句/灵感摘录」桌面便签**：羊皮纸拟真纹理，支持桌面「🔄 换一句」即时换签。
- **📖「在读作品进度」直达卡片**：直观展示书籍封面、阅读百分比与页码刻度，轻触直达阅读器。

### 7. 🏛️ OpenGL ES 3D 私人展厅 & 3D 翻书阅读器
- 原生 OpenGL ES 3.0/2.0 构建，零臃肿依赖、内存占用 <20MB、稳定 60fps/120fps 高刷。
- 内置 11 部经典名著纯净文本库（《小王子》《活着》《月亮与六便士》《1984》等），支持 3D 纸张卷曲光影动画与三大质感主题。

---

## 🛠️ 技术架构

```mermaid
graph TD
    A[ReadTrace Client] --> B[UI Layer: ViewPager2 / Bento Grid / Glassmorphism]
    A --> C[P1: 3D Vinyl & Cassette Player System]
    A --> D[P2: Haptic Vibration & Spatial Audio Engine]
    A --> E[P3: 3D Time Warp Tunnel & Memory Capsules]
    A --> F[P4: 3D Mindprint Topology & Iso-Contour Map]
    A --> G[Render Engine: OpenGL ES 3.0 / GLSL / Custom Canvas]
    A --> H[Motion Engine: GyroscopeParallax / AuroraFluid / SharedElement]
    A --> I[Desktop Widgets: AndroidX Glance / AppWidgets]
    A --> J[Data Layer: SQLite DB v3 / SAF Multi-Format Export]
```

- **UI 架构**：AndroidX ViewPager2 + OpenGL ES 3.0 + Glance AppWidgets + Material & Glassmorphism Design
- **核心先锋引擎**：`MindprintTopologyView` + `TimeWarpTunnelView` + `VinylTurntableView` + `CassetteDeckView` + `HapticFeedbackEngine` + `SpatialAudioEngine`
- **动效引擎**：`GyroscopeParallaxHelper` + `HolographicSpecularOverlayView` + `AuroraFluidBackgroundView` + `TransitionHelper`
- **数据存储**：Android SQLite 数据库 (DB v3) + SAF 存储访问框架
- **开发语言**：Kotlin 100%
- **构建系统**：Android Gradle Plugin 9.1.1 + Gradle 9.3.1 (compileSdk: 37 / minSdk: 31)

---

## 🚀 快速开始与构建

1. 使用 Android Studio 打开项目根目录。
2. 连接 Android 手机或启动模拟器（推荐 API 31+）。
3. 终端执行编译打包：
   ```bash
   ./gradlew.bat assembleDebug
   ```
4. 安装包路径：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 🗺️ 先锋创意开发路线图全量竣工回顾 (Roadmap)

| 优先级 | 核心先锋模块 | 视觉震撼度 | 状态 | 核心价值与交互体验 |
|:---:|:---|:---:|:---:|:---|
| **P1** | **💽 3D 黑胶唱机与磁带卡座播放器** | 🌟🌟🌟🌟🌟 | ✅ **已完成** | **补齐播客与声音影视的极致拟真媒介体验**<br>· 3D 唱针 23° 物理落针/抬针与黑胶唱片同心圆各向异性反光<br>· 复古透明磁带 A/B 面翻转与齿轮转动动效<br>· 音轨波形与歌词金句同步流淌 |
| **P2** | **🎧 触觉马达振动引擎与空间音频联动** | 🌟🌟🌟🌟 | ✅ **已完成** | **赋予每次撕票、盖章、翻书灵魂般的触感**<br>· 盖印章时重沉打击感 + 线性马达高频微颤 (`HapticFeedbackEngine`)<br>· 撕开电影票打孔处的清脆齿轮顿挫反馈<br>· 陀螺仪自适应双耳立体空间声场 (`SpatialAudioEngine`) |
| **P3** | **🌌 3D 时空穿梭隧道与心智流光胶囊** | 🌟🌟🌟🌟🌟 | ✅ **已完成** | **彻底颠覆传统扁平历史记录与时间轴**<br>· 景深虚化与无限延伸的 3D 透视时空虫洞 (`TimeWarpTunnelView`)<br>· 发光记忆胶囊悬停裂变与心境释放 (`TimeWarpTunnelActivity`)<br>· 1x 慢速漫游 / 5x 曲速跳跃 / 10x 光速折跃巡航 |
| **P4** | **🗺️ 3D 情绪拓扑与等高线心智地形图** | 🌟🌟🌟🌟 | ✅ **已完成** | **知识库与数据分析维度的降维打击**<br>· 基于多维心智复合高斯势能的 3D 地貌 (`MindprintTopologyView`)<br>· 3D 等高线 / 立体线框网格 / 能量热力图三维渲染<br>· 单指/双指 3D 俯仰旋转、海拔等高切片与巅峰信标聚焦 (`MindprintTopologyActivity`) |

---

## 📄 开源许可证

本项目基于 [Apache License 2.0](LICENSE) 协议开源。
