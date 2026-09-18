package com.example.readtrace.community

import android.content.Context
import com.example.readtrace.community.repository.CommunityRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * V0 ·「仓库即 CMS」的合并语义测试（Robolectric，跑在 `testDebugUnitTest` 门禁内，CI 无需模拟器）。
 *
 * 这里守的是 vibe 计划 V0 的核心验收标准：
 * **用户已点赞的展厅，在远端内容刷新回来后 `isLiked` 必须保留**——
 * 否则「内容可持续更新」会以「用户状态被冲掉」为代价，是本改造最容易踩的坑。
 *
 * 另外三条覆盖合并的边界：远端新增 / 本地独有保留 / 版本未推进时跳过。
 *
 * 说明：`CommunityRepository` 是 object 单例，测试间共享内存状态，
 * 因此每个用例只断言自己关心的切片，不假设列表的初始形态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommunityRemoteMergeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // 触发内置种子初始化（后续断言都建立在「种子已就绪」之上）
        CommunityRepository.getExhibitions()
    }

    // ---------- 测试夹具 ----------

    private fun remoteRoot(vararg items: JSONObject): JSONObject = JSONObject().apply {
        put("version", 99)
        put("updated_at", "2026-09-18")
        put("exhibitions", JSONArray().apply { items.forEach { put(it) } })
    }

    private fun remoteExhibition(
        id: String,
        likeCount: Int,
        title: String,
        commentCount: Int = 0,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("authorName", "远端策展人")
        put("authorAvatar", "🛰️")
        put("title", title)
        put("themeDescription", "远端策展词")
        put("tags", JSONArray(listOf("测试")))
        put("likeCount", likeCount)
        put("commentCount", commentCount)
        put("createdAt", "2026-09-18 00:00")
        put("featuredTheme", "星空漫想")
        put("curatedBooks", JSONArray())
    }

    // ---------- 核心验收 ----------

    @Test
    fun `用户点赞状态在远端合并后保留`() {
        val id = "ex-001"
        val before = CommunityRepository.getExhibitionById(id)
        assertNotNull("前置：内置种子应包含 ex-001", before)

        // 置为已点赞（若此前用例已点过则跳过，保证幂等）
        if (CommunityRepository.getExhibitionById(id)?.isLiked != true) {
            assertTrue("点赞应成功", CommunityRepository.toggleLike(id, context))
        }

        // 远端把同一展厅的 likeCount 改成 500、标题也换掉（模拟编辑部更新内容）
        val applied = CommunityRepository.applyRemoteExhibitions(
            remoteRoot(remoteExhibition(id, likeCount = 500, title = "远端改过的标题")),
            context,
            forceRefresh = true,
        )
        assertTrue("远端内容应被应用", applied)

        val after = CommunityRepository.getExhibitionById(id)!!
        assertTrue("【验收标准】用户点赞状态必须保留", after.isLiked)
        // V3 起 likeCount 语义为「编辑部推荐指数」，不再叠加用户点赞增量
        assertEquals("推荐指数应直接取远端值，不随用户点赞变化", 500, after.likeCount)
        assertEquals("内容字段应被远端覆盖", "远端改过的标题", after.title)
    }

    @Test
    fun `用户点赞不会改变编辑部推荐指数`() {
        val id = "ex-002"
        val before = CommunityRepository.getExhibitionById(id)!!
        val scoreBefore = before.likeCount
        val likedBefore = before.isLiked

        CommunityRepository.toggleLike(id, context)
        val afterToggle = CommunityRepository.getExhibitionById(id)!!
        assertEquals("V3：点赞只改状态，不得改动推荐指数", scoreBefore, afterToggle.likeCount)
        assertTrue("状态应已翻转", afterToggle.isLiked != likedBefore)

        // 还原，避免影响后续用例
        CommunityRepository.toggleLike(id, context)
    }

    @Test
    fun `远端留言计数不会低于本地值`() {
        val id = "ex-003"
        val localComments = CommunityRepository.getExhibitionById(id)?.commentCount ?: 0
        assertTrue("前置：内置种子 ex-003 应有留言计数", localComments > 0)

        // 远端给出一个更小的计数，合并后不应回退
        CommunityRepository.applyRemoteExhibitions(
            remoteRoot(remoteExhibition(id, likeCount = 10, title = "标题", commentCount = 0)),
            context,
            forceRefresh = true,
        )

        val after = CommunityRepository.getExhibitionById(id)!!
        assertTrue(
            "留言计数应取 max(远端, 本地)，不得回退（本地 $localComments → 实际 ${after.commentCount}）",
            after.commentCount >= localComments,
        )
    }

    // ---------- 合并边界 ----------

    @Test
    fun `远端新增的展厅会被追加`() {
        val id = "ex-999"
        val applied = CommunityRepository.applyRemoteExhibitions(
            remoteRoot(remoteExhibition(id, likeCount = 7, title = "远端独有展厅")),
            context,
            forceRefresh = true,
        )
        assertTrue("远端内容应被应用", applied)

        val added = CommunityRepository.getExhibitionById(id)
        assertNotNull("远端独有展厅应可被检索到", added)
        assertEquals("远端独有展厅", added!!.title)
    }

    @Test
    fun `本地独有的展厅不会被远端合并抹掉`() {
        // 上一用例已让远端列表只剩 ex-999，此时种子里的展厅应靠「本地独有保留」活下来
        CommunityRepository.applyRemoteExhibitions(
            remoteRoot(remoteExhibition("ex-998", likeCount = 1, title = "只含一条的远端包")),
            context,
            forceRefresh = true,
        )

        assertNotNull(
            "远端包未收录的展厅必须保留（内容只增不减，避免远端不完整导致展厅消失）",
            CommunityRepository.getExhibitionById("ex-002"),
        )
    }

    @Test
    fun `版本号未推进时跳过重复合并`() {
        val root = remoteRoot(remoteExhibition("ex-777", likeCount = 5, title = "版本控制测试"))

        assertTrue(
            "首次应用应成功",
            CommunityRepository.applyRemoteExhibitions(root, context, forceRefresh = true),
        )
        assertFalse(
            "同版本号再次应用应被跳过（省去无谓合并）",
            CommunityRepository.applyRemoteExhibitions(root, context, forceRefresh = false),
        )
        assertTrue(
            "强刷应无视版本号强制应用",
            CommunityRepository.applyRemoteExhibitions(root, context, forceRefresh = true),
        )
    }

    // ---------- 降级路径 ----------

    @Test
    fun `远端不可用时返回 false 且列表保持可用`() {
        val sizeBefore = CommunityRepository.getExhibitions().size

        assertFalse(
            "root 为 null（远端与缓存均不可用）时应返回 false",
            CommunityRepository.applyRemoteExhibitions(null, context),
        )
        assertEquals(
            "降级路径不得改动列表——断网冷启动必须与改造前表现一致",
            sizeBefore,
            CommunityRepository.getExhibitions().size,
        )
    }
}
