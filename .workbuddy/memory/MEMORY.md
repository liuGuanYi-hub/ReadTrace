# 阅痕 ReadTrace 项目长期约定

## 版本号规范（用户明确要求，2026-08-30）
- 版本名按 **4.2.7 → 4.2.8 → … → 4.2.20 → 4.3.0** 顺序顺延
- 小改动/修复只追加最后一位，**不要因为一次较完整的功能就跳小版本到 4.3**
- 只有最后一位走到 20 之后才升到 4.3
- versionCode 仍单调递增（不受版本名规则限制）
- 每次发版同步产出项目根目录 `ReadTrace_<version>.apk`

## 打包命令
- 构建前必须指定 JDK：`export JAVA_HOME="C:/Users/ZZD/.jdks/dragonwell-ex-21.0.9"`
  （旧的 `F:/work/JDK/...` 路径已失效；本机 JDK 都在 `C:/Users/ZZD/.jdks` 下：dragonwell-ex-21.0.9 / ms-17.0.16 / corretto-1.8.0_472）
- 本机 Gradle 并行构建会在 `D:\gradle-home\caches\...\transforms\<hash>.lock` 报「拒绝访问」，必须串行：
  ```
  ./gradlew :app:assembleRelease --max-workers=1
  ```
- WorkBuddy 沙箱下构建会失败，需 `dangerouslyDisableSandbox` 执行
- 产物路径 `app/build/outputs/apk/release/app-release.apk`，拷到项目根 `ReadTrace_<version>.apk`（*.apk 已被 .gitignore 忽略，不入库）

## 本机 Android 环境
- adb 不在 PATH：`F:/Android/sdk/platform-tools/adb.exe`（SDK 根目录见 `local.properties` = `F:\Android\sdk`）
- 模拟器：`F:/Android/sdk/emulator/emulator.exe -avd Medium_Phone -gpu host -no-snapshot-load`

## UI 验证方法（模型看不了截图）
- 截图无法被模型读取，改用 `adb shell uiautomator dump /sdcard/ui.xml` + `adb pull` 后用 Python 解析 XML 的 `bounds`/`text`
- 判断文本被截断：单行 TextView 高度只有 1 行，且估算文本宽度 > 可用宽度 ⇒ 被 `ellipsize` 切掉
- 像素→dp 换算：用已知 dp 的控件反推 density（36dp 控件实测 95px ⇒ density ≈ 2.64）

## Git 推送凭据
- 全局 `credential.helper` 指向的 WorkBuddy 便携版 GCM 会段错误，push 时临时覆盖：
  ```
  git -c credential.helper= -c "credential.helper=F:/work/git/Git/mingw64/bin/git-credential-manager.exe" push origin main
  ```

## 本机 Git 环境坑（2026-08-30 踩过）
- `.git/refs` 下的引用文件会偶发被删除 → 表现为 `not a git repository` / `bad object HEAD` / `unknown revision origin/main`
- 恢复步骤：① `find .git/refs` 确认为空 → ② 从 `.git/logs/HEAD` 末尾取 main 的最后 SHA → ③ `mkdir -p .git/refs/heads && echo <sha> > .git/refs/heads/main` → ④ `git reset --mixed HEAD` 重建索引（**不要**用 --hard，会丢未提交改动）
- 对象库若也损坏（pack 文件丢失）：`git fetch origin` 可从远程拉回全部对象（前提是都已 push）
- 强推历史改提交信息用 `--force-with-lease=refs/heads/main:<远程当前SHA>`（本地远程跟踪引用常失效，`--force-with-lease` 不带参数会报 stale info）
- 改提交信息脚本化做法：`GIT_SEQUENCE_EDITOR`（改 pick→reword/drop）+ `GIT_EDITOR`（按内容 sed 替换）驱动 `git rebase -i <base>`；rebase 前必须先 `git checkout --` 清掉未提交改动，改完再拷回

## 敏感信息
- 网易云 MUSIC_U Cookie 存放于 `不要放进git/`（已在 .gitignore 忽略）
- 排查接口时如需使用，禁止明文输出，用完删除临时文件，绝不入库

## 封面图源
- 预置封面由 `assets/cover_cdn_map.json`（键 `covers/xxx.jpg`）映射到国内 CDN；
  未收录的 40 张打包在 APK 内置资产里
- 映射表键已带 `covers/` 前缀，代码里不要再拼一次（历史上出过双前缀导致 145 张封面加载失败）
