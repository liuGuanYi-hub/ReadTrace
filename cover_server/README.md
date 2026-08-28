# ReadTrace 预置封面图源存档

本目录存放全部 225 张预置作品封面（约 36.5 MB），**不打包进 APK**。

## 实际加载机制

App 运行时按 `assets/cover_cdn_map.json` 中的「封面键 → 图源 URL」映射，
从**国内可达的图源**自动联网加载，每张封面首次加载后永久缓存到手机本地（离线可看）：

| 图源 | 数量 | 说明 |
|---|---|---|
| i0.hdslb.com（B站番剧库） | 72 | 番剧/电影，按标题经 B站 API 检索匹配 |
| media.steampowered.com（Steam 官方） | 65 | 全部游戏封面，与 Steam 商店同源 |
| img*.doubanio.com（豆瓣） | 29 | 书籍封面原链，App 自动携带 Referer |
| pX.music.126.net（网易云） | 14 | 专辑封面原链 |
| APK 内置资产 | 5 | 通用兜底占位图，离线可用 |
| cdn.jsdelivr.net（GitHub 仓库直读） | 40 | 书籍等无国内对应条目的封面；App 内置 cdn/gcore/testingcf 三镜像自动轮询 |

## 为什么保留本目录

`cdn.jsdelivr.net/gh/liuGuanYi-hub/ReadTrace@main/cover_server/covers/` 直接读取
GitHub 仓库中的这些文件，为 40 张没有国内对应图源的封面提供兜底服务，同时保留
全部原图存档（含原始 bgm.tv 图，可用于将来更换图源）。

## 更换图源

若某图源失效，只需更新 `app/src/main/assets/cover_cdn_map.json` 中对应键的 URL
（键名 `covers/xxx.jpg` 与数据库存储值保持解耦，无需迁移数据），重新编译即可。
