package com.example.readtrace.util

import android.content.Context
import com.example.readtrace.data.UserPreferencesManager
import com.example.readtrace.model.MediaType
import org.json.JSONObject

/**
 * 🖋️ P25 文心雕龙：AI 读后感大师润色与金句提炼工坊引擎 (ThoughtPolisherEngine)
 *
 * 支持三种大师级文风实时重塑与 <=15 字灵魂金句提炼，
 * 具备双轨能力（在线大模型优先 + 高精离线修辞推导兜底）。
 */
object ThoughtPolisherEngine {

    enum class PolishingStyle(
        val displayName: String,
        val promptDesc: String,
        val toneKeywords: String,
    ) {
        PHILOSOPHICAL(
            displayName = "🌿 典雅哲思",
            promptDesc = "木心、黑塞、博尔赫斯式的文学质感与舒缓节奏，注重文字的诗性、意象与时间沉淀感，将日常感知升华为关于存在、命运与宇宙的心智凝视。",
            toneKeywords = "诗性、沉潜、回甘、留白、形而上",
        ),
        CRITICAL(
            displayName = "⚡ 犀利艺评",
            promptDesc = "结构严整、洞察深刻的资深文艺评论人风格，直击叙事内核、戏剧张力与社会人文隐喻，言辞精确有力，剖析作品最具震慑力的艺术锚点。",
            toneKeywords = "透彻、张力、解构、冷峻、内核",
        ),
        INTIMATE(
            displayName = "🕊️ 私享手记",
            promptDesc = "真诚克制、轻灵私密的个人日记札记风格，如同深夜在纸页上的自言自语，卸下一切宏大叙事，捕捉那些细微却击穿灵魂的心动与颤栗瞬间。",
            toneKeywords = "温润、自白、轻语、共振、真诚",
        )
    }

    data class PolishedThought(
        val polishedText: String,
        val goldenQuote: String,
        val style: PolishingStyle,
        val isFromOffline: Boolean = false,
    )

    /**
     * 发起读后感润色与金句提炼
     */
    fun polish(
        context: Context,
        rawThought: String,
        bookTitle: String,
        author: String? = null,
        mediaType: MediaType = MediaType.BOOK,
        style: PolishingStyle = PolishingStyle.PHILOSOPHICAL,
        callback: (PolishedThought) -> Unit,
    ) {
        val apiKey = UserPreferencesManager.getAiApiKey(context)
        val baseUrl = UserPreferencesManager.getAiBaseUrl(context)
        val model = UserPreferencesManager.getAiModel(context)

        val trimmedRaw = rawThought.trim()

        // 未配置 Key 则直接进入高精离线推导
        if (apiKey.isBlank()) {
            val offline = generateOfflinePolishedThought(trimmedRaw, bookTitle, author, mediaType, style)
            callback(offline)
            return
        }

        Thread {
            try {
                val mediaLabel = mediaType.displayName
                val authorInfo = if (!author.isNullOrBlank()) "（创作者：$author）" else ""
                val prompt = """
                    你是一位精通跨媒介文艺评析的文学大师与策展评论家。
                    请为${mediaLabel}作品《$bookTitle》$authorInfo 的读者读后感/评语进行文学润色，并提炼一句话灵魂金句。

                    【读者原始草稿】：
                    ${if (trimmedRaw.isNotBlank()) trimmedRaw else "（暂无具体草稿，请结合该作核心内核与艺术魅力，直接创作为该作量身定制的顶级读后感）"}

                    【文风要求】：
                    ${style.displayName}：${style.promptDesc}（关键词：${style.toneKeywords}）

                    【输出格式与规范】：
                    请严格输出合法的 JSON 对象，格式如下：
                    {
                      "polishedText": "润色后的精炼读后感正文，保持在 70~130 字之间，词句雅致克制，层层递进，绝不堆砌空洞黑话。",
                      "goldenQuote": "提炼出的灵魂高光金句，严格不超过 15 个汉字，具有强烈穿透力与留白美感，适合印制在实体藏书票与票根上。"
                    }

                    要求：
                    1. 严禁包含 markdown 标记代码块（如 ```json），只返回纯 JSON 字符串；
                    2. goldenQuote 必须严格 <= 15 个字；
                    3. 文本具有强烈的文学品味与真诚感。
                """.trimIndent()

                val content = AiChatClient.requestChatCompletion(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    systemPrompt = "你是一位精通叙事学与文学修辞的顶尖文艺评论家。请严格只返回合法纯 JSON，不要包含任何多余解释。",
                    userPrompt = prompt,
                    temperature = 0.75,
                )

                if (content != null) {
                    val parsed = parseJsonResult(content, style)
                    if (parsed != null) {
                        callback(parsed)
                        return@Thread
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 失败或异常时平滑降级至离线推导
            val fallback = generateOfflinePolishedThought(trimmedRaw, bookTitle, author, mediaType, style)
            callback(fallback)
        }.start()
    }

    fun parseJsonResult(rawOutput: String, style: PolishingStyle): PolishedThought? {
        try {
            val clean = rawOutput.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val json = JSONObject(clean)
            val text = json.optString("polishedText").trim()
            var quote = json.optString("goldenQuote").trim()
            if (quote.length > 15) {
                quote = quote.take(15)
            }
            if (text.isNotBlank()) {
                return PolishedThought(
                    polishedText = text,
                    goldenQuote = if (quote.isNotBlank()) quote else extractQuoteFallback(text),
                    style = style,
                    isFromOffline = false,
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun extractQuoteFallback(text: String): String {
        val firstSentence = text.split("。", "！", "？", "\n")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: text
        return if (firstSentence.length <= 15) firstSentence else firstSentence.take(15)
    }

    /**
     * 离线高精文风推导库：在断网或无 Key 时保证 100% 具备高质量产出
     */
    fun generateOfflinePolishedThought(
        rawThought: String,
        bookTitle: String,
        author: String?,
        mediaType: MediaType,
        style: PolishingStyle,
    ): PolishedThought {
        val hasRaw = rawThought.isNotBlank()
        val authorPrefix = if (!author.isNullOrBlank()) "$author 的" else ""

        val (polishedText, goldenQuote) = when (style) {
            PolishingStyle.PHILOSOPHICAL -> {
                if (hasRaw) {
                    val text = "掩卷长思，《$bookTitle》所勾勒的并非单纯的故事走向，而是对生命本真的一次深潜叩问。$rawThought——在这份质朴的触动之下，时间仿佛被过滤成静谧的光影，字里行间沉淀着对虚无与永恒的温柔抵抗。"
                    val quote = "在虚无的浪潮里打捞微光"
                    text to quote
                } else {
                    val text = "在《$bookTitle》的意象图谱中，${authorPrefix}笔触呈现出一种极为罕见的文学沉潜。它不急于给出确凿的答案，而是在时间与叙事的留白处，让每一个凝视它的灵魂照见自身的浮沉与宿命。"
                    val quote = "字句如灯，照见宿命浮沉"
                    text to quote
                }
            }
            PolishingStyle.CRITICAL -> {
                if (hasRaw) {
                    val text = "从叙事美学与内核张力审视，《$bookTitle》展现了极高的掌控力。正如所感：“$rawThought”，其价值正是在于撕开了表象的温存，将个体的抉择置于时代与命运的残酷齿轮间，完成了一场精准而冷峻的人文解构。"
                    val quote = "冷峻解构，刺穿命运的齿轮"
                    text to quote
                } else {
                    val text = "《$bookTitle》构建了严密的隐喻矩阵。${authorPrefix}创作不仅具有极强的感官张力，更直指现代性困境中的精神原点。叙事节奏层层递进，直至在终局爆发摧枯拉朽的艺术震慑力。"
                    val quote = "严密叙事下的精神风暴"
                    text to quote
                }
            }
            PolishingStyle.INTIMATE -> {
                if (hasRaw) {
                    val text = "夜深人静时读完《$bookTitle》，心里久久不能平息。想起自己写下的这句“$rawThought”，忽然明白那些未曾言说的遗憾与温柔，早已在某个瞬间被它轻轻接住了。这不仅是一次阅读，更是与自己的一场和解。"
                    val quote = "它在夜深处轻轻接住了我"
                    text to quote
                } else {
                    val text = "有些作品是写给世界的，而《$bookTitle》像是写给某一个孤独夜晚的私语。没有居高临下的说教，只有细腻到近乎透明的心跳共振。在合上它的那一刻，周遭的喧嚣都悄然隐退。"
                    val quote = "写给孤独夜晚的温柔私语"
                    text to quote
                }
            }
        }

        val safeQuote = if (goldenQuote.length <= 15) goldenQuote else goldenQuote.take(15)

        return PolishedThought(
            polishedText = polishedText,
            goldenQuote = safeQuote,
            style = style,
            isFromOffline = true,
        )
    }
}
