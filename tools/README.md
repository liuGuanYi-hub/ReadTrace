# tools/ —— 开发期辅助脚本

本目录存放开发与验证过程中用到的 Python 脚本。
**全部零第三方依赖**（仅用标准库），直接 `python <脚本>` 即可运行。

这些脚本不属于 App 运行时，不入 APK；入库仅为防丢失与复用。

---

## dumpsys_coords.py

从 `adb shell dumpsys activity top` 的输出中求解控件的**屏幕绝对坐标**。

**为什么需要**：自动化验证 App 时会撞上两道墙 ——
① 非 exported 的 Activity 无法 `am start`（SecurityException），只能靠 UI 点击进入；
② `uiautomator dump` 对带常驻自绘动画的页面会失败（`could not get idle state`）。
此时唯一可用的视图信息来源就是 `dumpsys activity top` 的 View Hierarchy。

但 dumpsys 给出的 `l,t-r,b` 是**相对直接父容器的布局坐标**，且**不含 ScrollView 的 scrollY**，
不能直接拿去 `input tap` —— 必须按缩进重建 view 树、累加整条祖先链的偏移。本脚本做这件事。

```bash
adb shell dumpsys activity top > top.txt
python dumpsys_coords.py top.txt backupButton
```

**注意**：dumpsys 可能同时输出多个 Activity 的层级（如 Launcher + 前台 App），
脚本自动取**最后一段**。得到的坐标不含滚动偏移 —— 目标若在滚动区，
先用 `input swipe` 让它进入视口，再用 `uiautomator dump` 的 `bounds` 交叉校验。

---

## backfill_mindprint.py

为备份文件（默认 `912.json`）中**缺失 mindprint 的作品**推导六维心智档案。

推导依据（按优先级）：
1. `category` 流派关键词 → 六维基线（最能反映作品精神气质）
2. `tags` 标签关键词 → 六维微调
3. `rating` 评分 → 整体强度缩放
4. `mediaType` → 媒介固有偏置（游戏的 `difficulty` 天然偏高、音乐偏情绪等）

**设计原则**：
- `difficultyScore`（阅读阻力）**不与 rating 正相关**，而与「体裁难度」相关
- 保留小数一位，避免「全 8.0」这种明显的默认值特征
- **输出到新文件，绝不覆盖原始 JSON**

---

## gen_aot_preset.py

生成《进击的巨人》六季的 preset 条目并合并进 `preset_all.json`。

- **机器字段**（标题/评分/标签/简介/导演/制作/话数）取自 Bangumi API 实测数据（`build/bgm/`）
- **内容字段**（金句/短评/章节大纲/语录/六维心智/角色身份）为人工撰写

> 这是「新增一部作品」的参考范例：机器字段靠 API、内容字段靠撰写。
> 更新 `preset_all.json` 后若要生效，需按 §数据与播种机制 的约定改 `DATABASE_VERSION` 重播种。

---

## verify_quota.py

验证 `MindprintConstellationView` 的**三层配额策略**在真实数据下的分布情况。
严格按 Kotlin 侧逻辑复刻：

1. 同媒介主星连线（骨架）—— 不占跨媒介配额
2. 跨媒介弦：按媒介对分组 → 组内轮转录取（每轮每书最多 1 条）→ 余量还给全局
3. 每书上限 `MAX_EDGES_PER_BOOK`

> 用途：改了星图连线算法后，用真实数据先跑一遍看分布，不用等装到设备上才发现星图糊成一团。
