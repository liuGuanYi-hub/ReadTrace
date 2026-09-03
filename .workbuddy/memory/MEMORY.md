# 阅痕 ReadTrace 项目长期约定

## 版本号规范（用户 2026-09-01 最终确认）
- 项目自 **1.0.0** 起为正式版序列，当前已发布 **1.0.6**（versionCode 42）
- 版本名按 **1.0.0 → 1.0.1 → 1.0.2 → … → 1.0.30 → 1.1.0** 顺序顺延
- 中间位（1.0.x）走到 30 之后才升小版本到 1.1
- versionCode 仍单调递增（不受版本名规则限制）
- 每次发版同步产出项目根目录 `ReadTrace_<version>.apk`
- **用户明确要求：任何文档、计划、对话中一律不再提及旧 4.2.x 序列**；历史完成的分期改用中性称呼（如「导入一期/导入二期」）

## 主页分页化 P35（2026-09-03，开发计划第 45 章）
- 设计目标：主页首屏=清爽记录台（正方形 headerPanel 居中），第二页起才放探索内容；**首屏不得露出第二页内容**
- 关键实现：ScrollView 子 LinearLayout 内 `firstScreenStage` FrameLayout 包裹 headerPanel，运行时
  `firstScreenStage.minimumHeight = hubScroll.height - hubScroll.paddingTop`（只扣顶部 padding，底边贴屏幕底）+ `headerPanel.minimumHeight = headerPanel.width`（正方形）+ `layout_gravity="center"`
- 记录台杂志式排版：眉标行 → 分隔线 → 大标题 → 五媒介统计网格（arcCount*×5，weight 均分）→ 按钮 2 行网格（添加满宽 + 导入/备份/回收站 weight 均分，避免单行横滚截断）→ 页脚铭文
- **「我的最爱」在主页第二页**：那年今日之后 `favShowcaseSection`（默认 gone），`renderFavoriteStrip()` 五媒介分表 flatMap + rankOrder 排序，无收藏隐藏；卡片 `item_hub_favorite_card.xml`（88×124 封面 + 金色 NO.x 角标），无封面时 `loadCover(cover, null, placeholder)` 占位降级（emoji + 标题前 4 字）
- 弹窗统一设计语言：主页导入书单弹窗弃 AlertDialog，改 `bg_elegant_dialog` 玻璃容器 + 行式选项逐段渐入 + `attachSpringTouch` + 描边取消胶囊（与 ElegantFormDialog 一致）
- 滚动条收敛：主页 hubScroll / 回收站 ScrollView 一律 `android:scrollbars="none"`
- **2026-09-03 决定：不再需要 ISBN 扫码**——已移除 ML Kit barcode-scanning + CameraX（曾致 APK 44.5MB，移除后 20.9MB），扫码入口/相机权限/IsbnScannerActivity 全清；zxing core（二维码生成）保留

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

## UI 验证方法（2026-09-03 更新）
- Read 读截图依赖当前模型是否多模态：支持时首选 `adb exec-out screencap -p > x.png` + Read 肉眼看版式；**不支持时**改用程序化验证：`dumpsys activity top` 视图树看控件可见性（V/E/G 标记，如封面卡 cover V + placeholder G = 走封面分支）、几何（l,t-r,b）与滚动位置
- **dumpsys activity top 的 l,t-r,b 是布局坐标（相对父容器）**，不含 ScrollView scrollY；求屏幕绝对坐标需按缩进构建 view 树、累加祖先链 mLeft/mTop（Python 脚本，可复用）
- uiautomator dump 对本 App 首页/详情页基本失效（`could not get idle state`，常驻自绘动画导致非 idle，关系统动画也无效）——不要再依赖
- 本机 python（managed 3.13）无 PIL；Anaconda `F:/work/Anaconda/python.exe` 自带 Pillow 10.3，亮度分带/裁剪放大用它
- 数据库查看/修改：**release 包不可 `run-as`（package not debuggable），模拟器也非 root 镜像**（`adb root` 报 production builds）→ 查库只能在 debug 包或测试注入前先确认包可调试；历史记的 run-as 流程仅 debug 包有效
- 模拟器可能无网（ping 223.5.5.5 全丢），依赖联网的功能测试需先检查；无网时远程 CDN 封面加载失败会显示空米色底（bg_book_card），**非代码缺陷**
- `adb shell input text` 不支持中文；非 exported Activity 无法 `am start`（SecurityException，正常安全行为）；monkey 启动偶尔落在非目标 Activity，需 `am force-stop` 后重 `am start`

## Android 自定义绘制踩过的坑（2026-08-30）
- `StaticLayout` 会自己按 `setAlignment` 计算行偏移，传入的画笔若 `textAlign=CENTER` 会把每行再居中一次，整段左偏半行宽 → 必须传 `TextPaint(src).apply { textAlign = Paint.Align.LEFT }`
- 需要 `TextUtils.ellipsize` 时，画笔字段要声明成 `TextPaint` 而不是 `Paint`，否则报 `Argument type mismatch`
- 自定义 View 想锁定宽高比：`onMeasure` 里按期望比例算高度，绘制时再「等比缩放 + 居中」适配实际可用空间
- 父 `LinearLayout` 里若还有兄弟控件（说明文字/按钮），自定义 View 必须保持 `0dp + layout_weight=1`；改成 `wrap_content` 会吃掉全部剩余空间把兄弟挤没

## Git 推送凭据
- 全局 `credential.helper` 指向的 WorkBuddy 便携版 GCM 会段错误，push 时临时覆盖：
  ```
  git -c credential.helper= -c "credential.helper=F:/work/git/Git/mingw64/bin/git-credential-manager.exe" push origin main
  ```
- 全局/本地 `http.proxy=http://127.0.0.1:7897` 配了但代理软件未必在跑；不在线时 push 会 `Failed to connect to github.com:443 over proxy 127.0.0.1`。临时清空代理推送：
  ```
  git -c http.proxy= -c https.proxy= -c credential.helper= -c "credential.helper=F:/work/git/Git/mingw64/bin/git-credential-manager.exe" push origin main
  ```
  （与 GCM 覆盖参数一起用，互不冲突；fetch/ls-remote 同理）

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
