package com.example.readtrace.util

import android.content.Context
import com.example.readtrace.data.UserPreferencesManager
import com.example.readtrace.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🤖 AI 角色与故事大纲分析引擎 (AiAssistantEngine)
 *
 * 支持通过用户自填的 OpenAI / DeepSeek / Kimi / 通义千问 / Ollama API 生成角色表与分幕大纲，
 * 同时提供经典作品离线高精知识库与结构化推导兜底，保证 100% 离线也优雅可用。
 */
object AiAssistantEngine {

    data class CharacterItem(
        val name: String,
        val identity: String,
        val description: String,
    )

    data class OutlineChapter(
        val phase: String,
        val title: String,
        val summary: String,
    )

    data class AiStoryAnalysis(
        val premise: String,
        val characters: List<CharacterItem>,
        val outline: List<OutlineChapter>,
        val isFromOffline: Boolean = false,
    )

    /**
     * 发起分析请求（网络优先，无 Key 或异常时自动平滑降级至离线知识库）
     */
    fun analyzeStory(
        context: Context,
        title: String,
        author: String?,
        mediaType: MediaType,
        existingSummary: String?,
        callback: (AiStoryAnalysis) -> Unit,
    ) {
        val apiKey = UserPreferencesManager.getAiApiKey(context)
        val baseUrl = UserPreferencesManager.getAiBaseUrl(context)
        val model = UserPreferencesManager.getAiModel(context)

        // 未配置 API Key 则直接使用离线高精知识库
        if (apiKey.isBlank()) {
            val offline = getOfflineAnalysis(title, author, mediaType, existingSummary)
            callback(offline)
            return
        }

        Thread {
            try {
                val cleanUrl = baseUrl.trimEnd('/') + "/chat/completions"
                val url = URL(cleanUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

                val mediaLabel = mediaType.displayName
                val prompt = """
                    请分析${mediaLabel}作品《$title》${if (!author.isNullOrBlank()) "（创作者：$author）" else ""}。
                    请严格输出合法的 JSON 对象，格式如下：
                    {
                      "premise": "一句话核心主旨与故事背景",
                      "characters": [
                        {"name": "角色名", "identity": "身份/地位", "description": "性格特征与关键行为动机"}
                      ],
                      "outline": [
                        {"phase": "起/承/转/合 或 序章/第一幕等", "title": "分段标题", "summary": "该阶段核心剧情脉络"}
                      ]
                    }
                    要求：
                    1. 角色列出 3~6 位核心人物；
                    2. 大纲列出 3~5 个核心阶段；
                    3. 语言优雅、精准、有文学感；
                    4. 只返回 JSON 字符串，不要包含任何 markdown 标记或附加说明。
                """.trimIndent()

                val bodyJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "你是一位精通文学、影视、动漫与叙事艺术的资深策展分析助手。请只输出合法纯 JSON。")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.7)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(bodyJson.toString()) }

                val code = conn.responseCode
                if (code == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val respText = reader.readText()
                    val respJson = JSONObject(respText)
                    val content = respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    val parsed = parseAiOutput(content)
                    if (parsed != null) {
                        callback(parsed)
                        return@Thread
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 失败时平滑降级至离线知识库
            val fallback = getOfflineAnalysis(title, author, mediaType, existingSummary)
            callback(fallback)
        }.start()
    }

    private fun parseAiOutput(rawText: String): AiStoryAnalysis? {
        return try {
            val clean = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(clean)
            val premise = obj.optString("premise", "一部展现人类命运与思考的经典之作。")
            val chars = mutableListOf<CharacterItem>()
            val charsArray = obj.optJSONArray("characters")
            if (charsArray != null) {
                for (i in 0 until charsArray.length()) {
                    val c = charsArray.getJSONObject(i)
                    chars.add(
                        CharacterItem(
                            name = c.optString("name", "主要角色"),
                            identity = c.optString("identity", "关键人物"),
                            description = c.optString("description", "推动核心主线发展的重要角色。"),
                        ),
                    )
                }
            }
            val outline = mutableListOf<OutlineChapter>()
            val outlineArray = obj.optJSONArray("outline")
            if (outlineArray != null) {
                for (i in 0 until outlineArray.length()) {
                    val o = outlineArray.getJSONObject(i)
                    outline.add(
                        OutlineChapter(
                            phase = o.optString("phase", "阶段 ${i + 1}"),
                            title = o.optString("title", "核心脉络"),
                            summary = o.optString("summary", "情节推进与主题升华。"),
                        ),
                    )
                }
            }
            AiStoryAnalysis(premise, chars, outline, isFromOffline = false)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 离线经典作品高精知识库与通用智能结构化推导
     */
    fun getOfflineAnalysis(
        title: String,
        author: String?,
        mediaType: MediaType,
        existingSummary: String?,
    ): AiStoryAnalysis {
        val t = title.trim()
        val offlineMap = mapOf(
            "三体" to AiStoryAnalysis(
                premise = "文化大革命中受挫的叶文洁向宇宙发出文明信号，引来四光年外的三体舰队，人类文明面临前所未有的生存危机与宇宙黑暗森林法则考验。",
                characters = listOf(
                    CharacterItem("叶文洁", "天体物理学家 / 统帅", "红岸基地工程师，因对人类道德绝望而向三体文明发送引航信号。"),
                    CharacterItem("汪淼", "纳米材料专家", "通过幽灵倒计时与三体 VR 游戏，揭开智子封锁与地球三体组织内幕。"),
                    CharacterItem("史强", "前刑警 / 安保长官", "看似粗鲁但洞察敏锐，提出著名的「古筝行动」，以蝗虫比喻守护人类信念。"),
                    CharacterItem("申玉菲 / 潘寒", "地球三体组织核心", "拯救派与降临派的代表，围绕三体文明的降临展开意识形态博弈。"),
                ),
                outline = listOf(
                    OutlineChapter("起", "红岸迷雾与太阳天线", "叶文洁在冷酷的历史动荡中目睹至亲离世，在红岸基地利用太阳增益向深空发出人类讯息。"),
                    OutlineChapter("承", "幽灵倒计时与三体游戏", "纳米科学家汪淼眼球出现倒计时，通过高维三体沉浸游戏揭示三颗太阳混沌运动的文明毁灭循环。"),
                    OutlineChapter("转", "古筝行动与审判日号", "多国军方在巴拿马运河使用纳米飞刃切割「审判日号」，截获三体世界与地球叛军的全部绝密通信。"),
                    OutlineChapter("合", "智子封锁与蝗虫誓言", "三体人利用质子展开技术锁死地球基础物理，面对绝境，史强带领科学家们在麦田见证生命永不屈服的韧性。"),
                ),
                isFromOffline = true,
            ),
            "黑暗森林" to AiStoryAnalysis(
                premise = "面对三体文明的智子封锁与舰队逼近，人类推选四位面壁者展开思维战略防御，罗辑最终悟出宇宙社会学公理并建立黑暗森林威慑。",
                characters = listOf(
                    CharacterItem("罗辑", "面壁者 / 执剑人", "浪荡随性的宇宙学者，在叶文洁启发与庄颜指引下领悟黑暗森林法则，单枪匹马威慑三体。"),
                    CharacterItem("章北海", "太空军政委 / 逃亡主义者", "极端坚定的远见者，伪装胜利信念夺取「自然选择号」为人类文明保留火种。"),
                    CharacterItem("庄颜", "罗辑的妻子", "面壁计划为罗辑寻找的心灵寄托，唤醒了罗辑对人类未来的守护责任感。"),
                    CharacterItem("托马斯·维德", "PIA 局长", "极度冷酷的行动派，主导阶梯计划，名言「失去人性失去很多，失去兽性失去一切」。"),
                ),
                outline = listOf(
                    OutlineChapter("起", "面壁计划与破壁博弈", "联合国推选四位面壁者，泰勒、雷迪亚兹、希恩斯相继被智子指派的破壁人识破战略。"),
                    OutlineChapter("承", "水滴降临与末日血战", "三体探测器「水滴」以强相互作用力摧毁人类两千艘太空主力战舰，章北海率舰逃向深空。"),
                    OutlineChapter("转", "星舰黑暗森林与咒语应验", "逃亡飞船内部爆发黑暗森林猜疑链相互摧毁；罗辑曾标记的恒星被高维光粒摧毁，公理得到验证。"),
                    OutlineChapter("合", "褐蚁之墓与终极威慑", "罗辑在叶文洁墓前将枪口对准心脏与太阳引力波广播，三体舰队被迫停航，黑暗森林威慑时代开启。"),
                ),
                isFromOffline = true,
            ),
            "百年孤独" to AiStoryAnalysis(
                premise = "布恩迪亚家族七代人在马孔多小镇兴衰荣辱的百年史诗，在魔幻与现实交织中揭示了人类深层无法逃脱的宿命孤独。",
                characters = listOf(
                    CharacterItem("何塞·阿尔卡蒂奥·布恩迪亚", "家族始祖 / 拓荒者", "狂热执着于吉普赛炼金术与科学幻想，最终在栗树下神志不清地度过余生。"),
                    CharacterItem("乌尔苏拉", "家族女家长", "勤劳坚韧的长寿支柱，维系着庞大家族的运转与繁衍，见证了百年的兴衰。"),
                    CharacterItem("奥雷里亚诺·布恩迪亚上校", "革命领袖", "发动 32 场武装起义全部失败，晚年在作坊里日复一日熔铸并重做小金鱼。"),
                    CharacterItem("梅尔基亚德斯", "吉普赛智者", "预言布恩迪亚家族百年的梵文羊皮纸卷轴书写者，超越生死的智慧化身。"),
                ),
                outline = listOf(
                    OutlineChapter("起", "马孔多拓荒与炼金狂想", "何塞·阿尔卡蒂奥夫妇带领众人穿越沼泽建立马孔多，吉普赛人带来磁铁、望远镜与冰块。"),
                    OutlineChapter("承", "内战烽火与香蕉惨案", "奥雷里亚诺上校征战四方终归虚无，外国香蕉公司入驻后制造大屠杀并被历史抹除记忆。"),
                    OutlineChapter("转", "暴雨四年与家族凋零", "连绵四年十一天的暴雨冲刷着小镇，家族后代在欲望与近亲繁衍的宿命中走向腐朽。"),
                    OutlineChapter("合", "羊皮卷破译与飓风抹灭", "最后的奥雷里亚诺破译了梅尔基亚德斯的羊皮卷，狂暴的飓风将马孔多从大地上永远抹去。"),
                ),
                isFromOffline = true,
            ),
            "小王子" to AiStoryAnalysis(
                premise = "来自 B612 小行星的小王子在星际漫游中结识各种大人，最终在地球与飞行员和狐狸相遇，领悟了爱、驯服与生命本质的纯真寓言。",
                characters = listOf(
                    CharacterItem("小王子", "纯真旅人", "金发孩童，深爱着自己星球上独一无二的玫瑰花，用澄澈的双眼审视成人的荒唐世界。"),
                    CharacterItem("玫瑰花", "小王子的爱与牵挂", "骄傲任性又脆弱敏感的四刺花朵，小王子在离别后才懂得她刺背后的柔情。"),
                    CharacterItem("狐狸", "智慧启蒙者", "渴望被驯服的生灵，教导小王子「唯有用心才能看清事物，本质的东西用眼睛是看不见的」。"),
                    CharacterItem("飞行员", "故事记录者", "迫降在撒哈拉沙漠的大人，与小王子建立了超越言语的灵魂共鸣。"),
                ),
                outline = listOf(
                    OutlineChapter("起", "B612 小行星与玫瑰的别离", "小王子清理猴面包树幼苗与火山，因玫瑰的骄傲与猜疑决定离开母星展开星际旅行。"),
                    OutlineChapter("承", "六颗荒谬行星的漫游", "巡访国王、自负者、酒鬼、商人、点灯人与地理学家，看透成人世界的虚荣与机械僵化。"),
                    OutlineChapter("转", "撒哈拉沙漠与狐狸的驯服", "降落地球遇到千万朵玫瑰而迷茫，狐狸教会他「驯服建立羁绊」，自己的玫瑰因此变得独一无二。"),
                    OutlineChapter("合", "蛇的契约与星空微笑", "小王子借助毒蛇的毒液褪去沉重的躯壳返回星球，给飞行员留下了会如风铃般微笑的夜空群星。"),
                ),
                isFromOffline = true,
            ),
        )

        // 精确匹配或包含匹配
        offlineMap.entries.firstOrNull { t.contains(it.key) }?.value?.let { return it }

        // 通用结构化推导
        val desc = existingSummary?.takeIf { it.isNotBlank() } ?: "一部广受赞誉的${mediaType.displayName}作品"
        return AiStoryAnalysis(
            premise = "《$t》围绕着主人公在特定时代与境遇下的抉择展开，通过波澜起伏的情节探索人性、信念与命运的深层命题。",
            characters = listOf(
                CharacterItem("核心主角", "命运探索者", "在故事主线中经历重重考验，推动情节发展并完成心智蜕变。"),
                CharacterItem("引路者 / 关键伙伴", "智慧与支柱", "在主角迷茫时提供关键启示或行动支持的核心人物。"),
                CharacterItem("对立者 / 宿命阻碍", "冲突对峙方", "代表阻碍主角前行的外界规则、宿命抉择或复杂人性面向。"),
            ),
            outline = listOf(
                OutlineChapter("序幕 / 起", "风暴将起与初始世界", "引入《$t》的核心时代舞台，主要角色的平静生活被突如其来的变故打破。"),
                OutlineChapter("中段 / 承", "冲突升级与多重博弈", "主角在困境与迷茫中不断探索，与同伴建立羁绊并迎来最大转折考验。"),
                OutlineChapter("高潮 / 转", "命运决战与认知破局", "所有矛盾在此刻集中爆发，主角做出决定性的牺牲与终极选择。"),
                OutlineChapter("尾声 / 合", "余韵悠长与主题升华", "风暴停息后的重构与反思，给读者/观众留下深远的心灵回响。"),
            ),
            isFromOffline = true,
        )
    }
}
