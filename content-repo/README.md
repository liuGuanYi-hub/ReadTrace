# readtrace-content · 阅痕内容仓库

阅痕 ReadTrace 的**策展内容源**。本目录的内容应发布到独立公开仓库
`liuGuanYi-hub/readtrace-content`，客户端以只读方式拉取。

> 为什么独立成一个仓库：主仓库是**代码**，这里是**内容**。分开之后策展内容可以随时增补、
> 接受外部投稿，而不必等待 App 发版，也不会打扰主仓库的 Issue 区。

---

## 一、发布步骤（仅需一次）

```bash
# 1. 在 GitHub 建一个公开仓库，名字必须是 readtrace-content
#    （客户端把路径写死为 liuGuanYi-hub/readtrace-content@main，换名字需同步改代码）

# 2. 把本目录内容推上去
cd content-repo
git init
git add .
git commit -m "内容：初始化阅痕内容仓库"
git branch -M main
git remote add origin https://github.com/liuGuanYi-hub/readtrace-content.git
git push -u origin main
```

推完之后**无需发版**：客户端 24h 内（或在社区页点「刷新」强刷）即可拉到新内容。

> 建议给内容仓库单独配置 `README.md` 与 PR 模板 —— 这一层就是「仓库即 CMS」的编辑部。

---

## 二、目录结构

| 路径 | 用途 | 客户端消费状态 |
|:---|:---|:---|
| `exhibitions/index.json` | 社区展厅策展位列表 | ✅ V0 已接入 |
| `daily/index.json` | 每日策展位（按日期） | ⏳ V1 接入 |
| `notices/index.json` | 版本公告 / What's New 运营位 | ⏳ 待接入 |

---

## 三、`exhibitions/index.json` 格式

```jsonc
{
  "version": 1,                    // 必须递增，客户端以此判断有无更新
  "updated_at": "2026-09-18",      // 真实提交日期，用于展示而非虚构社交数字
  "exhibitions": [
    {
      "id": "ex-001",              // 唯一且稳定——客户端按它匹配，改 id 会被当成新展厅
      "authorName": "林栖阁主",
      "authorAvatar": "🌌",
      "title": "荒谬与微光：存在主义思想展台",
      "themeDescription": "一段两三句的策展词",
      "tags": ["哲学", "经典"],       // 用于社区页分类筛选
      "likeCount": 382,             // ⚠️ 见下方「诚实性约定」
      "commentCount": 28,
      "createdAt": "2026-08-20 18:30",
      "featuredTheme": "星空漫想",    // 封面主题：星空漫想 / 暖木书房 / 禅意绿洲 / 曜石夜读
      "curatedBooks": [             // 一到五部，字段与下例一致
        {
          "id": 101,
          "title": "小王子",
          "author": "圣埃克苏佩里",
          "category": "哲学",
          "rating": 9.8,
          "status": "finished",      // wishlist / reading / finished / paused / dropped
          "mediaType": "book",       // book / anime / movie / game / music
          "shortComment": "一句话金句",
          "review": "一段短评"
        }
      ]
    }
  ]
}
```

**字段约束**：`status` 与 `mediaType` 取值必须来自上面注释里的枚举；写错的会被客户端静默降级为
`wishlist` / `book`，不会崩溃但显示不对。

---

## 四、投稿流程

1. Fork 本仓库
2. 在 `exhibitions/index.json` 的 `exhibitions` 数组**末尾追加**一个展厅对象
   （**不要修改已有展厅的 `id`**）
3. 把 `version` 加一
4. 提 PR，说明策展主题与选书理由

收录标准（暂定）：三到五部作品、有明确的主题串联、策展词能说清「为什么是这几部放在一起」。

---

## 五、诚实性约定

`likeCount` / `commentCount` 目前仍是**内置的展示用基础值**，并非真实统计——
社区的真实互动计数要等后端落地（规划中的 V4）才会由服务端聚合。

在那之前请遵守一条原则：**宁可少写，不要编造看起来像社交证明的数字**。
客户端会以 `updated_at` 展示「内容更新于 X」，这比虚构的「382 人喜欢」诚实得多。

---

## 六、客户端行为（供投稿者了解）

| 行为 | 说明 |
|:---|:---|
| 拉取顺序 | `cdn.jsdelivr.net/gh/...` → `raw.githubusercontent.com/...`（前者国内可达性通常更好） |
| 缓存 | 24h 磁盘缓存；命中即不发请求 |
| 兜底 | 网络不可用 → 回退陈旧缓存 → 再不行回退 App 内置种子。**断网冷启动完全正常** |
| 用户数据 | 用户的点赞 / 留言 / 自发布展厅**不会被远端内容冲掉**（合并时只覆盖内容字段） |
| 安全 | 只信任写死的仓库路径；不执行远端返回的任何内容，只解析 JSON |

---

## 七、更新后如何立即验证

改完并 push 后，在 App 里进入社区页点右上角**刷新**（强刷，跳过 24h 缓存）。
若没变化，依次检查：

1. `version` 是否递增了（未递增会被客户端判定为「无更新」而跳过）
2. 仓库名与分支是否确为 `readtrace-content@main`
3. jsDelivr 有缓存延迟，必要时等几分钟或直接访问 `raw.githubusercontent.com` 路径确认已生效
