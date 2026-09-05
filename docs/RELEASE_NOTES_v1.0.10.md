# 阅痕 ReadTrace v1.0.10 — 安全加固收官与三项预置缺陷修复发布说明 🔐🛡️

[![Platform](https://img.shields.io/badge/Platform-Android%2012%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-v1.0.10-3A6348.svg)](https://github.com/liuGuanyi-hub/ReadTrace/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **版本定位**：v1.0.10 为 P38「数据安全与性能纵深」收官版——落地安全加固五项（凭据加密落盘、验证码防暴力、小组件后台化、速记查重与解析修复）；装机回归中另定位并修复三个长期潜伏的预置缺陷（剪贴板嗅探在 Android 10+ 恒失效、获取验证码按钮永久不可见、通行证卡高光线穿过标题）。

---

## 🔐 1. 安全加固五项（P38 Phase 4）

| 编号 | 修复 | 说明 |
|:--|:---|:---|
| G13 | WebDAV 密码加密落盘 | 新增 `SecurePrefs`：AndroidKeyStore AES-256-GCM，密钥不可导出；旧明文首读自动迁移并抹除；解密失败即清条目宁缺勿明文。实现上未用已废弃的 androidx security-crypto，零新增依赖 |
| G14 | 验证码防暴力 | `SecureRandom` 取代 `Random(nanoTime)`，验证码不可预测；单条验证码最多 5 次错误尝试，超限即作废，须重新获取 |
| G12 | 小组件后台化 | 「灵感伴读」「全息心智看板」两个桌面小组件的查库、会话统计与位图解码全部移交后台单线程，主线程零阻塞 |
| G7 | 速记弹窗修复 | 一句话速记行改持宿主 Dialog 引用关闭（原 rootView 为装饰层致弹窗永不收起、连点重复入库）；入库前来源精确 + 同媒介同名双查重，命中提示「已在藏库，不重复收录」 |
| G8 | 速记解析修复 | 书名取剔除关键词后的整段文本，「读完 Snow Crash 9分」完整入库《Snow Crash》，不再截断为《Snow》 |

## 🛠️ 2. 装机回归定位并修复的三个预置缺陷

- **剪贴板嗅探在 Android 10+ 恒失效**：`onResume` 时窗口未获输入焦点，`ClipboardService` 一律拒绝读取（模拟器日志实锤 `Denying clipboard access`）。改为 onResume 标记、`onWindowFocusChanged(true)` 后再嗅探，恢复「发现剪贴板作品」极光收录胶囊功能；
- **获取验证码按钮永久不可见**：`btnRequestCode` 所在 `otpSection` 容器 XML 中 `visibility="gone"` 且代码从未恢复，手机号快捷登录链路断死；移除错误隐藏，验证码输入区仍独立控制展开；
- **通行证卡标题被高光线穿过**：顶部高光原用 24dp 高 `drawRoundRect` 描边，其底边恰好横穿「CURATOR PASS」标题行；改为 Path 仅描顶边与两个上圆角。

## 🧪 3. 装机回归验证报告（Medium_Phone 模拟器实测）

- G13：注入旧版明文密码 → 打开 WebDAV 配置页自动迁移 → 旧 prefs 明文键抹除、加密仓仅存密文、界面预填 18 位原值一致；
- G14：错误 1~4 次依次提示「剩余 4/3/2/1 次机会」，第 5 次提示「错误次数过多，请重新获取验证码」，随后输**正确**码亦被拒（会话已作废）；
- G7+G8：剪贴板「读完 Snow Crash 9分」→ 收录胶囊 → 速记行显示完整《Snow Crash》→ 一键入库弹窗自动收起；换变体文本二次录入被拦截「已在藏库，不重复收录」，书籍计数 55→56 仅 +1；
- G12：4×2 小组件成功上桌渲染（书目/状态/引言/直达键齐全），后台线程执行无 ANR；
- 解析器单测 7/7 全绿（含多词英文书名回归用例）。

## 📦 4. 升级说明

- versionCode 45 → 46，versionName 1.0.9 → 1.0.10；
- 老版本升级安装无需任何操作：WebDAV 密码在首次读取时自动迁入加密仓，同步配置不受影响；换机还原场景下若系统 Keystore 密钥不可迁移，仅要求重新填写一次 WebDAV 应用密码。
