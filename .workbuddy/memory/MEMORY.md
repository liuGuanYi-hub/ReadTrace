# 阅痕 ReadTrace 项目长期约定

## 版本号规范（用户明确要求，2026-08-30）
- 版本名按 **4.2.7 → 4.2.8 → … → 4.2.20 → 4.3.0** 顺序顺延
- 小改动/修复只追加最后一位，**不要因为一次较完整的功能就跳小版本到 4.3**
- 只有最后一位走到 20 之后才升到 4.3
- versionCode 仍单调递增（不受版本名规则限制）
- 每次发版同步产出项目根目录 `ReadTrace_<version>.apk`

## 打包命令
- 本机 Gradle 并行构建会在 `D:\gradle-home\caches\...\transforms\<hash>.lock` 报「拒绝访问」，必须串行：
  ```
  ./gradlew :app:assembleRelease --max-workers=1
  ```
- WorkBuddy 沙箱下构建会失败，需 `dangerouslyDisableSandbox` 执行

## Git 推送凭据
- 全局 `credential.helper` 指向的 WorkBuddy 便携版 GCM 会段错误，push 时临时覆盖：
  ```
  git -c credential.helper= -c "credential.helper=F:/work/git/Git/mingw64/bin/git-credential-manager.exe" push origin main
  ```

## 敏感信息
- 网易云 MUSIC_U Cookie 存放于 `不要放进git/`（已在 .gitignore 忽略）
- 排查接口时如需使用，禁止明文输出，用完删除临时文件，绝不入库

## 封面图源
- 预置封面由 `assets/cover_cdn_map.json`（键 `covers/xxx.jpg`）映射到国内 CDN；
  未收录的 40 张打包在 APK 内置资产里
- 映射表键已带 `covers/` 前缀，代码里不要再拼一次（历史上出过双前缀导致 145 张封面加载失败）
