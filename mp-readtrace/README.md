# 阅痕 ReadTrace · 微信小程序端 (mp-readtrace)

P19 跨端漫游：uni-app Vue3 + TypeScript 构建的微信轻量漫游端，
与 Android 端数据模型 **100% 同构**（字段名与 JSON 备份 Schema v4 完全一致）。

## 功能范围（对应 P19 计划）

| 模块 | 说明 |
|---|---|
| `pages/library` | 库藏列表：媒介筛选 + 搜索 + Local-First 本地缓存（离线可用） |
| `pages/quick-log` | 3 秒极速速记：一句话分词（与 `NaturalQuickAddParser` 同构）+ 五态秒存 + 智能心智推导 |
| `components/DioramaCard` | 2.5D 空间视差标本微卡：`wx.onDeviceMotionChange` 陀螺仪 + 触控双通道，4 层差速 |
| `components/VinylPlayer` | 悬浮黑胶：WebAudio 合成 432~528Hz 泛音（评分分级，与引力琴同构），无音频实现时纯视觉降级 |
| `utils/sync.ts` | WebDAV 增量同步：与 Android `WebDavSyncEngine` 同路径/同 Schema，拉推 + 内容级去重 |
| `utils/models.ts` | Book / Note / Mindprint 数据模型镜像 + `deriveMindprint` 智能推导 |

## 运行

```bash
cd mp-readtrace
npm install          # 仅安装到本目录 node_modules
npm run dev:mp-weixin
# 用微信开发者工具打开 dist/dev/mp-weixin
```

`src/manifest.json` 中的 `YOUR_WECHAT_APPID` 需替换为你的小程序 AppID。

## 与 Android 端互通

- **方式一（数据主权，推荐）**：两端均配置同一坚果云/NAS WebDAV，
  分别执行同步即可按内容级去重合并（Schema v4 全量高阶资产）。
- **方式二**：Android 端 `WechatMinappSyncProtocol.kt` 定义的序列化协议
  可用于后续接入「微信云开发」托管模式（尚未实装，预留）。

## 已知边界

- 小程序端暂不承载六维雷达编辑、海报工坊等重交互（请使用 App 端）；
- `wx.request` 对 PUT/MKCOL 的兼容性以坚果云/Nextcloud 实测为准，失败时回退只读浏览。
