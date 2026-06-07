# 阅痕 ReadTrace

阅痕 ReadTrace 是一个 Android 本地书籍记录 App，目标是在手机上记录书籍、阅读状态、评分、标签、简短评价和读后感。

## 当前工程

当前 Android 工程位于仓库根目录。

```txt
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/
app/
docs/
archive/
```

主模块：

```txt
:app
```

当前 Android 配置：

- project name：`readtrace`
- namespace：`com.example.readtrace`
- applicationId：`com.example.readtrace`
- compileSdk：37
- minSdk：31
- targetSdk：37
- Android Gradle Plugin：9.1.1
- 当前 UI：XML layout + AppCompat

## 当前可用功能

- 添加书籍并保存到设备本地 SQLite
- 首页展示未归档书籍
- 按全部、想读、在读、已读筛选
- 保存评分、标签、简短评价、读后感和阅读日期
- 标签以 JSON 字符串写入数据库
- 数据层默认排除软删除记录，不提供物理删除入口

## 文档

- [安卓开发文档](docs/安卓开发文档.md)
- [开发文档](docs/开发文档.md)
- [开发进度](docs/开发进度.md)

## 原型归档

早期 Web/FastAPI 代码已归档：

```txt
archive/web-react-vite/
archive/fastapi-backend/
```

这些代码只作为参考，不作为 v1.0 安卓主线。

## 数据原则

- 不允许物理删除用户数据。
- 删除默认使用软删除 / 归档。
- v1.0 使用原生 SQLite 本地保存。
- v1.0 不做登录、云同步、AI、摘录和笔记。

任何可能减少 C 盘容量的操作，都必须先说明预计增加量，并等待确认。
