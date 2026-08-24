package com.example.readtrace.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.readtrace.model.ArchivedNoteItem
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookCharacter
import com.example.readtrace.model.BookLocation
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.BookOutline
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.MonthlyReadingStat
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.model.ReadingSession
import com.example.readtrace.util.CoverImageHelper
import org.json.JSONArray
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class BookDatabaseHelper(val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_BOOKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_AUTHOR TEXT,
                $COLUMN_COVER_URL TEXT,
                $COLUMN_CATEGORY TEXT,
                $COLUMN_STATUS TEXT NOT NULL DEFAULT 'wishlist',
                $COLUMN_MEDIA_TYPE TEXT NOT NULL DEFAULT 'book',
                $COLUMN_RATING REAL,
                $COLUMN_TAGS TEXT NOT NULL DEFAULT '[]',
                $COLUMN_SHORT_COMMENT TEXT,
                $COLUMN_REVIEW TEXT,
                $COLUMN_START_DATE TEXT,
                $COLUMN_FINISH_DATE TEXT,
                $COLUMN_BUY_CHANNEL TEXT,
                $COLUMN_SHELF_LOCATION TEXT,
                $COLUMN_BINDING_TYPE TEXT,
                $COLUMN_BUY_PRICE REAL,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_UPDATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX index_books_status_deleted ON $TABLE_BOOKS " +
                "($COLUMN_STATUS, $COLUMN_IS_DELETED)",
        )
        createNotesTable(database)
        createReadingSessionsTable(database)
        createCharactersTable(database)
        createOutlinesTable(database)
        createLocationsTable(database)
        createMindprintsTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1.1：新增 notes 表，已有 books 数据保持不变。
            createNotesTable(database)
        }
        if (oldVersion < 3) {
            // v1.5：新增 media_type 字段，已有书籍平滑迁移为默认 'book' 类型。
            database.execSQL(
                "ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_MEDIA_TYPE TEXT NOT NULL DEFAULT 'book'",
            )
        }
        if (oldVersion < 4) {
            // v3.1：增加实体馆藏字段与阅读打卡/角色谱/大纲脑图表
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_BUY_CHANNEL TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_SHELF_LOCATION TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_BINDING_TYPE TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_BUY_PRICE REAL") }
            createReadingSessionsTable(database)
            createCharactersTable(database)
            createOutlinesTable(database)
        }
        if (oldVersion < 5) {
            // v3.2：增加空间地标足迹表与六维心智评分表
            createLocationsTable(database)
            createMindprintsTable(database)
        }
    }

    private fun createNotesTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_NOTE_TYPE TEXT NOT NULL DEFAULT 'note',
                $COLUMN_PAGE TEXT,
                $COLUMN_CHAPTER TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_UPDATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_notes_book_deleted ON $TABLE_NOTES " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createReadingSessionsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_READING_SESSIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_DURATION_MINUTES INTEGER NOT NULL,
                $COLUMN_PAGES_READ TEXT,
                $COLUMN_THOUGHT TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sessions_book_deleted ON $TABLE_READING_SESSIONS " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createCharactersTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_CHARACTERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_ROLE_TITLE TEXT,
                $COLUMN_AVATAR_EMOJI TEXT NOT NULL DEFAULT '👤',
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_RELATIONSHIP TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chars_book_deleted ON $TABLE_BOOK_CHARACTERS " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createOutlinesTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_OUTLINES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_CHAPTER_ORDER INTEGER NOT NULL DEFAULT 1,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_SUMMARY TEXT NOT NULL,
                $COLUMN_KEY_TAKEAWAYS TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_outlines_book_deleted ON $TABLE_BOOK_OUTLINES " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createLocationsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_LOCATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_LOCATION_TYPE TEXT NOT NULL DEFAULT '🏙️ 现实都市',
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_SIGNIFICANCE TEXT,
                $COLUMN_COORDINATES TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_locations_book_deleted ON $TABLE_BOOK_LOCATIONS " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createMindprintsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_MINDPRINTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL UNIQUE,
                $COLUMN_DEPTH_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_ARTISTRY_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_EMOTION_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_LOGIC_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_DIFFICULTY_SCORE REAL NOT NULL DEFAULT 5.0,
                $COLUMN_HEALING_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_UPDATED_AT TEXT NOT NULL,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_mindprints_book ON $TABLE_BOOK_MINDPRINTS ($COLUMN_BOOK_ID)",
        )
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        populatePresetBookRichData(db)
        seedUserAnimeList(db)
    }

    private fun seedUserAnimeList(db: SQLiteDatabase) {
        runCatching {
            data class AnimeEntry(
                val title: String,
                val author: String,
                val category: String,
                val status: String,
                val yearTag: String,
                val tags: List<String>,
                val rating: Double?,
                val shortComment: String?,
                val mindprint: FloatArray? = null, // depth, art, emo, log, diff, heal
            )

            val animeList = listOf(
                // 1995 - 2024 补完番剧
                AnimeEntry("EVA (新世纪福音战士)", "庵野秀明 · Gainax", "机甲哲学", "finished", "1995", listOf("1995年", "EVA", "机甲神作", "神作"), 5.0, "不能逃避，面对人与人之间的AT力场，向所有的福音战士告别。", floatArrayOf(9.9f, 9.6f, 9.8f, 8.5f, 6.0f, 9.5f)),
                AnimeEntry("浪客剑心 追忆篇", "古桥一浩 · Studio Deen", "时代剑戟", "finished", "1999", listOf("1999年", "时代剑戟", "凄美神作", "雪代巴"), 5.0, "你真是能呼唤腥风血雨的人啊，十字伤的宿命与救赎。", floatArrayOf(9.5f, 9.8f, 10.0f, 8.8f, 4.0f, 6.0f)),
                AnimeEntry("夏目友人帐", "大森贵弘 · Brain's Base", "治愈妖怪", "finished", "2008", listOf("2008年", "治愈系", "夏目贵志", "猫咪老师"), 5.0, "我想成为一个温柔的人，因为曾被温柔的人那样对待过。", floatArrayOf(8.8f, 9.5f, 10.0f, 8.0f, 2.0f, 10.0f)),
                AnimeEntry("轻音少女", "山田尚子 · 京都动画", "青春音乐", "finished", "2009", listOf("2009年", "京阿尼", "萌系日常", "放学后TEA TIME"), 4.8, "请不要拔掉插头，因为我们还要演奏更多属于青春的旋律！", floatArrayOf(7.5f, 9.0f, 9.2f, 7.0f, 1.5f, 10.0f)),
                AnimeEntry("轻音少女 第二季", "山田尚子 · 京都动画", "青春音乐", "finished", "2010", listOf("2010年", "京阿尼", "毕业季", "天使にふれたよ!"), 5.0, "但我们相遇了天使，毕业并不是终点，我们永远是同伴。", floatArrayOf(8.0f, 9.5f, 9.8f, 7.5f, 1.5f, 10.0f)),
                AnimeEntry("怪盗基德 (魔术快斗)", "TMS Entertainment", "悬疑奇幻", "finished", "2010", listOf("2010年", "怪盗基德", "魔术月光", "黑羽快斗"), 4.7, "如果说怪盗是个技艺精湛的魔术师，那侦探不过是跟在后面的评论家罢了。"),
                AnimeEntry("Angel Beats！", "麻枝准 · P.A.WORKS", "奇幻催泪", "finished", "2010", listOf("2010年", "麻枝准", "死后世界", "催泪神作"), 4.9, "即便转世重生，我依然会喜欢上你，这就是我的心跳声。", floatArrayOf(9.0f, 9.2f, 10.0f, 8.0f, 3.0f, 9.5f)),
                AnimeEntry("JOJO 的奇妙冒险", "津田尚克 · david production", "奇幻热血", "finished", "2012", listOf("2012年", "JOJO", "波纹疾走", "大乔二乔"), 4.9, "人类的赞歌就是勇气的赞歌！人类的伟大就是勇气的伟大！", floatArrayOf(9.0f, 9.2f, 9.5f, 9.0f, 4.5f, 8.0f)),
                AnimeEntry("我的青春恋爱物语果然有问题", "吉村爱 · Brain's Base", "青春思辨", "finished", "2013", listOf("2013年", "春物", "大老师", "真物"), 4.8, "温柔正确的人总是难以生存，因为这世界既不温柔也不正确。", floatArrayOf(9.4f, 9.2f, 9.5f, 8.5f, 4.5f, 8.0f)),
                AnimeEntry("约会大作战", "元永庆太郎 · AIC PLUS+", "奇幻恋爱", "finished", "2013", listOf("2013年", "约战", "精灵", "十香"), 4.5, "那么，开始我们的约会吧。"),
                AnimeEntry("JOJO 的奇妙冒险 星尘远征军", "津田尚克 · david production", "奇幻热血", "finished", "2014", listOf("2014年", "JOJO", "替身白金之星", "承太郎"), 5.0, "正义必然会战胜邪恶，因为你惹怒了我。"),
                AnimeEntry("月刊少女野崎君", "山崎光惠 · 动画工房", "搞笑恋爱", "finished", "2014", listOf("2014年", "搞笑", "少女漫画", "野崎君与佐仓"), 4.8, "我很喜欢哦，看烟花的时候你也是这么想的吧。"),
                AnimeEntry("Charlotte (夏洛特)", "麻枝准 · P.A.WORKS", "超能青春", "finished", "2015", listOf("2015年", "麻枝准", "超能力", "友利奈绪"), 4.6, "掠夺全世界的超能力，只为了守护你一个人的约定。"),
                AnimeEntry("我的青春恋爱物语果然有问题 续", "及川启 · feel.", "青春思辨", "finished", "2015", listOf("2015年", "春物续", "雪乃", "团子", "寻找真物"), 4.9, "我不想要被欺瞒的理解，我想要的是真正的真物。"),
                AnimeEntry("路人女主的养成方法", "龟井干太 · A-1 Pictures", "青春恋爱", "finished", "2015", listOf("2015年", "路人女主", "加藤惠", "圣人惠"), 4.7, "将不起眼的你，培养成心动的主角。"),
                AnimeEntry("齐木楠雄的灾难", "樱井弘明 · J.C.STAFF", "搞笑日常", "finished", "2016", listOf("2016年", "超能力搞笑", "齐神", "咖啡果冻"), 4.9, "呀咧呀咧，虽然超能力很麻烦，但平静的高中生活才是终极目标。"),
                AnimeEntry("JOJO 的奇妙冒险 不灭钻石", "津田尚克 · david production", "奇幻热血", "finished", "2016", listOf("2016年", "杜王町", "东方仗助", "吉良吉影"), 5.0, "黄金精神永不熄灭，守护杜王町的日常与正义。"),
                AnimeEntry("在下坂本，有何贵干？", "高松信司 · Studio Deen", "搞笑日常", "finished", "2016", listOf("2016年", "装逼如风", "坂本大佬", "日常搞笑"), 4.7, "无论面对何种刁难，都要以最优雅从容的姿态化解。"),
                AnimeEntry("灵能百分百", "立川让 · BONES 骨头社", "热血成长", "finished", "2016", listOf("2016年", "骨头社", "龙套", "灵幻新隆"), 5.0, "超能力只是个性的一种，并不代表高人一等，重要的是如何做个好人。", floatArrayOf(9.5f, 9.0f, 9.8f, 9.2f, 3.0f, 9.6f)),
                AnimeEntry("夏目友人帐 伍", "出合小都美 · 朱夏", "治愈妖怪", "finished", "2016", listOf("2016年", "夏目友人帐5", "治愈", "妖怪物语"), 4.9, "那些温暖的羁绊，会在岁月的河流里永恒闪光。"),
                AnimeEntry("来自深渊", "小岛正幸 · Kinema Citrus", "黑暗奇幻", "finished", "2017", listOf("2017年", "阿比斯深渊", "黎明卿", "斯巴拉西"), 5.0, "深渊在凝视着你，探窟者的灵魂永不停歇地追寻未知的深处。", floatArrayOf(9.8f, 9.5f, 9.6f, 9.5f, 7.0f, 3.0f)),
                AnimeEntry("笨女孩", "草川启造 · Diomedéa", "搞笑日常", "finished", "2017", listOf("2017年", "猴子香蕉", "花畑佳子", "爆笑解压"), 4.5, "只要有香蕉吃，智商什么的根本不重要！"),
                AnimeEntry("欢迎来到实力至上主义教室", "岸诚二 · Lerche", "悬疑校园", "finished", "2017", listOf("2017年", "路哥", "绫小路清隆", "实力至上"), 4.6, "所有人对我来说都不过是棋子，只要最后获胜的是我就行了。"),
                AnimeEntry("夏目友人帐 陆", "出合小都美 · 朱夏", "治愈妖怪", "finished", "2017", listOf("2017年", "夏目友人帐6", "治愈", "岁月温柔"), 4.9, "只要有想见的人，就不是孤身一人。"),
                AnimeEntry("路人女主的养成方法 2", "龟井干太 · A-1 Pictures", "青春恋爱", "finished", "2017", listOf("2017年", "路人女主2", "同人游戏制作", "加藤惠"), 4.8, "我们所创作的游戏，就是倾注了所有青春热忱的证明。"),
                AnimeEntry("JOJO 的奇妙冒险 黄金之风", "津田尚克 · 木村泰大 · david production", "奇幻热血", "finished", "2018", listOf("2018年", "JOJO5", "乔鲁诺", "布加拉提"), 5.0, "觉悟就是在这漆黑的荒野中，开辟出一条光明坦途！", floatArrayOf(9.2f, 9.6f, 9.5f, 9.6f, 5.0f, 8.0f)),
                AnimeEntry("青春猪头少年不会梦到兔女郎学姐", "增井壮一 · CloverWorks", "奇幻青春", "finished", "2018", listOf("2018年", "青春综合征", "麻衣学姐", "咲太"), 4.9, "即便全世界都遗忘了你，我也会在操场上大声喊出喜欢你！", floatArrayOf(9.2f, 9.0f, 9.8f, 9.0f, 4.0f, 9.0f)),
                AnimeEntry("紫罗兰永恒花园", "石立太一 · 京都动画", "情感史诗", "finished", "2018", listOf("2018年", "京阿尼巅峰", "薇尔莉特", "爱是什么"), 5.0, "我想知道‘我爱你’究竟是什么意思，自动手记人偶为你传达所有心意。", floatArrayOf(9.6f, 10.0f, 10.0f, 8.5f, 2.5f, 10.0f)),
                AnimeEntry("碧蓝之海", "高松信司 · ZERO-G", "搞笑青春", "finished", "2018", listOf("2018年", "硬核潜水", "乌龙茶(可燃)", "爆笑大学"), 4.8, "来一杯可以点燃的乌龙茶吧，这就是大学潜水社的青春！"),
                AnimeEntry("强风吹拂", "野村和也 · Production I.G", "运动热血", "finished", "2018", listOf("2018年", "箱根驿传", "跑步的意义", "宽政大"), 5.0, "你喜欢跑步吗？跑步不是为了超越别人，而是为了到达属于自己的彼岸。", floatArrayOf(9.5f, 9.2f, 9.8f, 9.5f, 3.5f, 9.8f)),
                AnimeEntry("文豪野犬 第二季", "五十岚卓哉 · BONES 骨头社", "异能战斗", "finished", "2019", listOf("2019年", "黑之时代", "织田作之助", "太宰治"), 4.8, "去成为救人的一方吧，既然在善恶哪边都一样，那就去救人吧。"),
                AnimeEntry("灵能百分百 第二季", "立川让 · BONES 骨头社", "热血成长", "finished", "2019", listOf("2019年", "骨头社神作", "最上启示", "师徒情"), 5.0, "哪怕不依靠超能力，我也想要凭借自己的双手改变人生。"),
                AnimeEntry("约会大作战 第三季", "元永庆太郎 · J.C.STAFF", "奇幻恋爱", "finished", "2019", listOf("2019年", "约战3", "七罪", "折纸"), 4.5, "为了拯救折纸与精灵们，再次展开跨越时空的约会。"),
                AnimeEntry("魔女之旅", "洼冈俊之 · C2C", "奇幻公路", "finished", "2020", listOf("2020年", "伊蕾娜", "灰之魔女", "公路物语"), 4.7, "这是一个关于旅人与不同国度相遇的故事，那位可爱的魔女是谁呢？没错，就是我。"),
                AnimeEntry("我的青春恋爱物语果然有问题。完", "及川启 · feel.", "青春思辨", "finished", "2020", listOf("2020年", "春物完结", "比企谷八幡", "雪之下雪乃"), 4.8, "请将你的人生交给我，即便痛苦，我也要和你纠缠到底。"),
                AnimeEntry("Re：从零开始的异世界生活 新编集版", "渡边政治 · WHITE FOX", "奇幻穿越", "finished", "2020", listOf("2020年", "死亡回归", "菜月昴", "蕾姆"), 4.9, "哪怕从零开始，我也要在这残酷的世界里拯救所有人！"),
                AnimeEntry("国王排名", "八田洋介 · WIT STUDIO", "奇幻成长", "finished", "2021", listOf("2021年", "波吉王子", "卡克", "催泪治愈"), 4.8, "最弱小无声的聋哑王子，拥有这世上最坚强纯善的心灵。"),
                AnimeEntry("转生成蜘蛛又怎样！", "板垣伸 · Millepensee", "异界转生", "finished", "2021", listOf("2021年", "单口相声", "蜘蛛子", "悠木碧"), 4.6, "只要拼尽全力活下去，就算转生成小蜘蛛也能弑神！"),
                AnimeEntry("JOJO 的奇妙冒险 石之海", "铃木健一 · david production", "奇幻热血", "finished", "2021", listOf("2021年", "空条徐伦", "普奇神父", "天堂制造"), 4.9, "这是属于乔斯达家族百年的血脉宿命，人类的觉悟跨越新世界。"),
                AnimeEntry("间谍过家家", "古桥一浩 · WIT STUDIO / CloverWorks", "搞笑日常", "finished", "2022", listOf("2022年", "阿尼亚", "黄昏", "约尔太太"), 4.9, "哇酷哇酷！间谍、杀手与读心术超能力少女的伪装温馨家庭。"),
                AnimeEntry("夏日重现", "渡边步 · OLM", "悬疑循环", "finished", "2022", listOf("2022年", "日都岛", "时间轮回", "影子病"), 4.9, "跨越无数次死亡轮回，只为在日都岛夏日祭上拯救潮与所有人！", floatArrayOf(9.4f, 9.0f, 9.2f, 9.8f, 5.0f, 7.5f)),
                AnimeEntry("孤独摇滚！", "斋藤圭一郎 · CloverWorks", "音乐日常", "finished", "2022", listOf("2022年", "波奇酱", "结束乐队", "社恐神作"), 5.0, "社恐又怎样？只要抱起吉他，我的声音就能穿透整个世界！", floatArrayOf(9.0f, 9.6f, 9.8f, 8.5f, 2.0f, 10.0f)),
                AnimeEntry("蓝色监狱", "渡边彻明 · 8bit", "竞技热血", "finished", "2022", listOf("2022年", "唯我独尊", "洁世一", "利己主义前锋"), 4.8, "丢掉温情脉脉的合作吧，在这里只有最疯狂的利己前锋才能生存！"),
                AnimeEntry("灵能百分百 第三季", "莲井隆弘 · BONES 骨头社", "热血成长", "finished", "2022", listOf("2022年", "神树篇", "告白篇", "完美谢幕"), 5.0, "接纳不完美的自己，龙套与灵幻迎来了最温暖动人的圆满结局。"),
                AnimeEntry("约会大作战 第四季", "中川淳 · GEEK TOYS", "奇幻恋爱", "finished", "2022", listOf("2022年", "本条二亚", "星宫六喰", "约战4"), 4.6, "封印万由里与所有精灵的心灵之门。"),
                AnimeEntry("间谍过家家 第二季", "古桥一浩 · WIT STUDIO / CloverWorks", "搞笑日常", "finished", "2023", listOf("2023年", "豪华游轮篇", "约尔太太大显身手"), 4.8, "守护家人的平静生活，就是杀手与间谍最崇高的使命。"),
                AnimeEntry("我独自升级", "中重俊祐 · A-1 Pictures", "爽快异能", "finished", "2024", listOf("2024年", "成振宇", "暗影君王", "站起来"), 4.7, "从最弱E级猎人到支配死亡的暗影君王，站起来吧！"),
                AnimeEntry("蓝色监狱 第二季", "生原雄次 · 8bit", "竞技热血", "finished", "2024", listOf("2024年", "U-20决战", "怪物觉醒"), 4.7, "赌上职业生涯的U-20代表战，让世界见证蓝色监狱的变革！"),
                AnimeEntry("鬼灭之刃", "外崎春雄 · Ufotable", "和风热血", "finished", "经典", listOf("飞碟社", "炭治郎", "祢豆子", "无限列车"), 4.9, "纵使身形俱灭，也定将恶鬼斩杀！心之火永不熄灭。"),
                AnimeEntry("魔法少女小圆", "新房昭之 · 虚渊玄 · SHAFT", "黑暗奇幻", "finished", "经典", listOf("圆神", "晓美焰", "爱的战士", "神作"), 5.0, "为了拯救你，我愿意在这个无限轮回的时空里战斗千百次！", floatArrayOf(9.9f, 9.5f, 9.8f, 9.8f, 5.5f, 6.0f)),
                AnimeEntry("咒术回战", "朴性厚 · MAPPA", "热血奇幻", "finished", "待整理", listOf("咒术回战", "虎杖悠仁", "五条悟", "MAPPA"), 4.8, "在众人簇拥下死去，吞下宿傩手指的少年走向咒术之路。"),
                AnimeEntry("咒术回战 第二季", "御所园翔太 · MAPPA", "怀玉玉折/涩谷事变", "finished", "待整理", listOf("怀玉玉折", "涩谷事变", "五条悟", "夏油杰"), 5.0, "青春的夏日终究走向破碎，涩谷地下展开了最残酷的咒术大战。"),
                AnimeEntry("咒术回战 第三季 (死灭回游)", "MAPPA", "热血奇幻", "finished", "待整理", listOf("死灭回游", "羂索", "乙骨忧太"), 4.8, "死灭回游结界开启，为了解除五条悟封印而浴血奋战。"),
                AnimeEntry("魔都精兵的奴隶", "Seven Arcs", "战斗奇幻", "finished", "待整理", listOf("魔都精兵", "羽前京香", "奴隶契约"), 4.5, "成为魔防队长的专属奴隶，在魔都阴影中斩杀丑鬼。"),
                AnimeEntry("魔都精兵的奴隶 第二季", "Passione", "战斗奇幻", "finished", "待整理", listOf("魔都精兵2", "八雷神", "魔防队"), 4.5, "更深层次的魔都隐秘揭开，强敌八雷神降临。"),
                AnimeEntry("我推的孩子 第二季", "平牧大辅 · 动画工房", "演艺悬疑", "finished", "待整理", listOf("东京BLADE", "舞台剧篇", "黑川茜", "有马加奈"), 4.9, "在东京BLADE舞台上释放真正的演技，阿库亚直面复仇心魔。"),
                AnimeEntry("我推的孩子 第三季", "动画工房", "演艺悬疑", "finished", "待整理", listOf("我推的孩子3", "偶像与复仇", "真相逼近"), 4.8, "B小町全国巡演与隐藏在黑暗中的终极幕后黑手。"),

                // 待看列表 (想追 / wishlist)
                AnimeEntry("玉子市场", "山田尚子 · 京都动画", "日常治愈", "wishlist", "待看", listOf("待看清单", "京阿尼", "兔山商店街", "大路饼藏"), null, "兔山商店街的温馨人情，会说人话的奇妙鸟与纯真青春。"),
                AnimeEntry("某科学的超电磁炮", "长井龙雪 · J.C.STAFF", "超能战斗", "wishlist", "待看", listOf("待看清单", "炮姐", "御坂美琴", "唯我超电磁炮"), null, "学园都市最强LV5超电磁炮，硬币弹起的瞬间就是正义。"),
                AnimeEntry("凉宫春日的忧郁", "石原立也 · 京都动画", "校园科幻", "wishlist", "待看", listOf("待看清单", "SOS团", "凉宫春日", "漫无止境的八月"), null, "我对普通的人类没有兴趣，如果你们之中有外星人、未来人、超能力者，就来找我吧！"),
                AnimeEntry("中二病也要谈恋爱！", "石原立也 · 京都动画", "青春恋爱", "wishlist", "待看", listOf("待看清单", "京阿尼", "邪王真眼", "六花与勇太"), null, "被漆黑烈焰吞噬吧！爆裂吧现实，粉碎吧精神，放逐这个世界！"),
                AnimeEntry("冰菓", "武本康弘 · 京都动画", "青春推理", "wishlist", "待看", listOf("待看清单", "京阿尼巅峰", "折木奉太郎", "我很好奇"), null, "做没有必要做的事情就不要做，必须做的事情就从简。但是，我很好奇！"),
                AnimeEntry("野良神", "田村耕太郎 · BONES 骨头社", "神魔奇幻", "wishlist", "待看", listOf("待看清单", "骨头社", "五元神明", "夜斗"), null, "只需五元香火钱，实现你所有愿望的无家可归神明夜斗。"),
                AnimeEntry("请问您今天要来点兔子吗？", "桥本裕之 · WHITE FOX", "萌系日常", "wishlist", "待看", listOf("待看清单", "点兔", "保登心爱", "香风智乃"), null, "充满咖啡香气与温暖兔子的木造小镇日常生活。"),
                AnimeEntry("干物妹！小埋", "太田雅彦 · 动画工房", "搞笑日常", "wishlist", "待看", listOf("待看清单", "小埋", "披着仓鼠斗篷", "可乐薯片"), null, "在外完美无瑕的高中女神，回家立刻化身二头身吃零食打游戏！"),
                AnimeEntry("逆转裁判", "渡边步 · A-1 Pictures", "法庭推理", "wishlist", "待看", listOf("待看清单", "成步堂龙一", "异议阿里", "法庭对决"), null, "异议あり！在绝境中寻找证据的唯一矛盾，彻底逆转法庭！"),
                AnimeEntry("杀戮天使", "铃木健太郎 · J.C.STAFF", "悬疑逃脱", "wishlist", "待看", listOf("待看清单", "Angels of Death", "扎克", "瑞吉儿"), null, "誓言与杀戮的约定，从这栋封闭的大楼深处逃出生天。"),
                AnimeEntry("多罗罗", "古桥一浩 · MAPPA / 手冢Production", "暗黑武侠", "wishlist", "待看", listOf("待看清单", "手冢治虫", "百鬼丸", "夺回身体"), null, "被魔神夺走四十八处器官的少年，在战国乱世中斩魔夺回肉身。"),
                AnimeEntry("葬送的芙莉莲", "斋藤圭一郎 · Madhouse", "史诗治愈", "wishlist", "待看", listOf("待看清单", "年度霸权", "千年精灵", "勇者辛美尔"), null, "在打倒魔王之后的漫长岁月里，长生精灵芙莉莲重新踏上体会人心的旅程。"),
                AnimeEntry("超时空要塞", "河森正治 · Satelight", "机甲歌姬", "wishlist", "待看", listOf("待看清单", "超时空要塞", "战术音乐", "女武神"), null, "歌声能跨越宇宙的硝烟，机甲与歌姬唱响银河最后的恋歌。"),
                AnimeEntry("辉夜大小姐想让我告白", "畠山守 · A-1 Pictures", "恋爱喜剧", "wishlist", "待看", listOf("待看清单", "天才们的恋爱头脑战", "辉夜与会长"), null, "先告白的人就是输家！秀知院学园两位顶级天才的傲娇智斗恋爱。"),
            )

            val now = currentTimestamp()
            for (anime in animeList) {
                val cursor = db.query(
                    TABLE_BOOKS,
                    arrayOf(COLUMN_ID),
                    "$COLUMN_TITLE = ? AND $COLUMN_IS_DELETED = 0",
                    arrayOf(anime.title),
                    null, null, null,
                )
                val existingId = cursor.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }

                val bookId = if (existingId != null) {
                    existingId
                } else {
                    val cv = ContentValues().apply {
                        put(COLUMN_TITLE, anime.title)
                        put(COLUMN_AUTHOR, anime.author)
                        put(COLUMN_CATEGORY, anime.category)
                        put(COLUMN_STATUS, anime.status)
                        put(COLUMN_MEDIA_TYPE, "anime")
                        put(COLUMN_RATING, anime.rating)
                        put(COLUMN_TAGS, JSONArray(anime.tags).toString())
                        put(COLUMN_SHORT_COMMENT, anime.shortComment)
                        put(COLUMN_REVIEW, if (anime.status == "finished") "已完成追番 · 留存在《阅痕》的珍贵青春印记" else "加入个人待看追番清单")
                        put(COLUMN_START_DATE, if (anime.status == "finished") "${anime.yearTag}-01-01" else null)
                        put(COLUMN_FINISH_DATE, if (anime.status == "finished") "${anime.yearTag}-12-31" else null)
                        put(COLUMN_BUY_CHANNEL, "Bilibili / 官方正版番剧")
                        put(COLUMN_SHELF_LOCATION, "展厅第3层 · 经典番剧回廊")
                        put(COLUMN_BINDING_TYPE, "TV / 剧场版动画")
                        put(COLUMN_CREATED_AT, now)
                        put(COLUMN_UPDATED_AT, now)
                        put(COLUMN_IS_DELETED, 0)
                    }
                    db.insert(TABLE_BOOKS, null, cv)
                }

                // 如果包含心智评分，则注入雷达
                if (anime.mindprint != null && bookId > 0) {
                    val mp = anime.mindprint
                    val mpCv = ContentValues().apply {
                        put(COLUMN_BOOK_ID, bookId)
                        put(COLUMN_DEPTH_SCORE, mp[0].toDouble())
                        put(COLUMN_ARTISTRY_SCORE, mp[1].toDouble())
                        put(COLUMN_EMOTION_SCORE, mp[2].toDouble())
                        put(COLUMN_LOGIC_SCORE, mp[3].toDouble())
                        put(COLUMN_DIFFICULTY_SCORE, mp[4].toDouble())
                        put(COLUMN_HEALING_SCORE, mp[5].toDouble())
                        put(COLUMN_UPDATED_AT, now)
                    }
                    db.insertWithOnConflict(TABLE_BOOK_MINDPRINTS, null, mpCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
        }
    }

    private fun populatePresetBookRichData(db: SQLiteDatabase) {
        runCatching {
            fun findOrInsertBook(title: String, author: String, category: String, status: String, shortComment: String, review: String, rating: Double, tags: List<String>, coverUrl: String, buyChannel: String, shelfLocation: String, bindingType: String, buyPrice: Double, mediaType: String = "book"): Long {
                val cursor = db.query(TABLE_BOOKS, arrayOf(COLUMN_ID), "$COLUMN_TITLE LIKE ? AND $COLUMN_IS_DELETED = 0", arrayOf("%$title%"), null, null, null)
                val bookId = cursor.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }

                val now = currentTimestamp()
                if (bookId != null) {
                    // 更新已有作品的藏本属性
                    val cv = ContentValues().apply {
                        put(COLUMN_BUY_CHANNEL, buyChannel)
                        put(COLUMN_SHELF_LOCATION, shelfLocation)
                        put(COLUMN_BINDING_TYPE, bindingType)
                        put(COLUMN_BUY_PRICE, buyPrice)
                    }
                    db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(bookId.toString()))
                    return bookId
                } else {
                    val cv = ContentValues().apply {
                        put(COLUMN_TITLE, title)
                        put(COLUMN_AUTHOR, author)
                        put(COLUMN_CATEGORY, category)
                        put(COLUMN_STATUS, status)
                        put(COLUMN_MEDIA_TYPE, mediaType)
                        put(COLUMN_SHORT_COMMENT, shortComment)
                        put(COLUMN_REVIEW, review)
                        put(COLUMN_RATING, rating)
                        put(COLUMN_TAGS, JSONArray(tags).toString())
                        put(COLUMN_COVER_URL, coverUrl)
                        put(COLUMN_START_DATE, "2026-06-01")
                        put(COLUMN_FINISH_DATE, "2026-06-15")
                        put(COLUMN_BUY_CHANNEL, buyChannel)
                        put(COLUMN_SHELF_LOCATION, shelfLocation)
                        put(COLUMN_BINDING_TYPE, bindingType)
                        put(COLUMN_BUY_PRICE, buyPrice)
                        put(COLUMN_CREATED_AT, now)
                        put(COLUMN_UPDATED_AT, now)
                        put(COLUMN_IS_DELETED, 0)
                    }
                    return db.insert(TABLE_BOOKS, null, cv)
                }
            }

            fun populateForBook(
                bookId: Long,
                depth: Double, artistry: Double, emotion: Double, logic: Double, diff: Double, heal: Double,
                characters: List<Triple<String, String, String>>, // name, role, desc
                outlines: List<Triple<Int, String, String>>, // order, title, summary
                locations: List<Triple<String, String, String>>, // name, type, desc
                sessions: List<Pair<Int, String>>, // duration, pages
                quotes: List<Pair<String, String>>, // content, page
            ) {
                val now = currentTimestamp()

                // 心智评分
                val mindCv = ContentValues().apply {
                    put(COLUMN_BOOK_ID, bookId)
                    put(COLUMN_DEPTH_SCORE, depth)
                    put(COLUMN_ARTISTRY_SCORE, artistry)
                    put(COLUMN_EMOTION_SCORE, emotion)
                    put(COLUMN_LOGIC_SCORE, logic)
                    put(COLUMN_DIFFICULTY_SCORE, diff)
                    put(COLUMN_HEALING_SCORE, heal)
                    put(COLUMN_UPDATED_AT, now)
                }
                db.insertWithOnConflict(TABLE_BOOK_MINDPRINTS, null, mindCv, SQLiteDatabase.CONFLICT_REPLACE)

                // 角色谱
                val charCount = db.query(TABLE_BOOK_CHARACTERS, arrayOf("COUNT(*)"), "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0", arrayOf(bookId.toString()), null, null, null).use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
                if (charCount == 0) {
                    characters.forEach { (name, role, desc) ->
                        val cv = ContentValues().apply {
                            put(COLUMN_BOOK_ID, bookId)
                            put(COLUMN_NAME, name)
                            put(COLUMN_ROLE_TITLE, role)
                            put(COLUMN_AVATAR_EMOJI, when {
                                name.contains("小王子") -> "👑"
                                name.contains("玫瑰") -> "🌹"
                                name.contains("狐狸") -> "🦊"
                                name.contains("叶文洁") -> "🔭"
                                name.contains("汪淼") -> "👓"
                                name.contains("史强") -> "👮"
                                name.contains("布恩迪亚") -> "👴"
                                else -> "👤"
                            })
                            put(COLUMN_DESCRIPTION, desc)
                            put(COLUMN_CREATED_AT, now)
                            put(COLUMN_IS_DELETED, 0)
                        }
                        db.insert(TABLE_BOOK_CHARACTERS, null, cv)
                    }
                }

                // 章节大纲
                val outlineCount = db.query(TABLE_BOOK_OUTLINES, arrayOf("COUNT(*)"), "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0", arrayOf(bookId.toString()), null, null, null).use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
                if (outlineCount == 0) {
                    outlines.forEach { (order, title, summary) ->
                        val cv = ContentValues().apply {
                            put(COLUMN_BOOK_ID, bookId)
                            put(COLUMN_CHAPTER_ORDER, order)
                            put(COLUMN_TITLE, title)
                            put(COLUMN_SUMMARY, summary)
                            put(COLUMN_CREATED_AT, now)
                            put(COLUMN_IS_DELETED, 0)
                        }
                        db.insert(TABLE_BOOK_OUTLINES, null, cv)
                    }
                }

                // 空间地标
                val locCount = db.query(TABLE_BOOK_LOCATIONS, arrayOf("COUNT(*)"), "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0", arrayOf(bookId.toString()), null, null, null).use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
                if (locCount == 0) {
                    locations.forEach { (name, type, desc) ->
                        val cv = ContentValues().apply {
                            put(COLUMN_BOOK_ID, bookId)
                            put(COLUMN_NAME, name)
                            put(COLUMN_LOCATION_TYPE, type)
                            put(COLUMN_DESCRIPTION, desc)
                            put(COLUMN_CREATED_AT, now)
                            put(COLUMN_IS_DELETED, 0)
                        }
                        db.insert(TABLE_BOOK_LOCATIONS, null, cv)
                    }
                }

                // 专注打卡
                val sessionCount = db.query(TABLE_READING_SESSIONS, arrayOf("COUNT(*)"), "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0", arrayOf(bookId.toString()), null, null, null).use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
                if (sessionCount == 0) {
                    sessions.forEach { (duration, pages) ->
                        val cv = ContentValues().apply {
                            put(COLUMN_BOOK_ID, bookId)
                            put(COLUMN_DURATION_MINUTES, duration)
                            put(COLUMN_PAGES_READ, pages)
                            put(COLUMN_THOUGHT, "潜心专注阅读，沉浸在宏大与细腻的文字意境中。")
                            put(COLUMN_CREATED_AT, now)
                            put(COLUMN_IS_DELETED, 0)
                        }
                        db.insert(TABLE_READING_SESSIONS, null, cv)
                    }
                }

                // 经典金句
                val noteCount = db.query(TABLE_NOTES, arrayOf("COUNT(*)"), "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0", arrayOf(bookId.toString()), null, null, null).use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
                if (noteCount == 0) {
                    quotes.forEach { (content, page) ->
                        val cv = ContentValues().apply {
                            put(COLUMN_BOOK_ID, bookId)
                            put(COLUMN_CONTENT, content)
                            put(COLUMN_NOTE_TYPE, "quote")
                            put(COLUMN_PAGE, page)
                            put(COLUMN_CREATED_AT, now)
                            put(COLUMN_UPDATED_AT, now)
                            put(COLUMN_IS_DELETED, 0)
                        }
                        db.insert(TABLE_NOTES, null, cv)
                    }
                }
            }

            // 1. 《小王子》
            val princeId = findOrInsertBook(
                title = "小王子",
                author = "圣埃克苏佩里",
                category = "文学名著",
                status = "finished",
                shortComment = "正因为你为你的玫瑰花费了时间，这才使你的玫瑰变得如此重要。",
                review = "这是一本写给所有曾经是小孩的大人的童话。它用最纯净的语言，道出了人世间最深刻的真理：爱是驯服与责任，唯有用心才能看清本质。",
                rating = 5.0,
                tags = listOf("童话", "治愈", "哲学", "经典"),
                coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                buyChannel = "上海独立书店 · 季风书园",
                shelfLocation = "书架第1层 · 治愈精神馆",
                bindingType = "精装全彩插图典藏本",
                buyPrice = 45.0,
            )
            populateForBook(
                bookId = princeId,
                depth = 9.2, artistry = 9.8, emotion = 10.0, logic = 7.5, diff = 2.0, heal = 10.0,
                characters = listOf(
                    Triple("小王子", "B-612星球守护者", "纯真执着的金发小男孩，游历星际寻找生命真谛"),
                    Triple("玫瑰花", "骄傲的初恋", "生长在B-612小行星上的娇艳花朵，有四根刺和虚荣的骄傲"),
                    Triple("狐狸", "智慧人生导师", "沙漠中的灵性生物，教会小王子什么是‘驯服’与‘责任’"),
                    Triple("飞行员", "孤独的大人知音", "迫降在撒哈拉沙漠的成年人，保留着童年看透蟒蛇吞大象的纯真眼光"),
                ),
                outlines = listOf(
                    Triple(1, "初遇与星际漫游", "飞行员迫降撒哈拉沙漠，遇到请求画羊的小王子，得知其来自B-612小行星"),
                    Triple(2, "国王与点灯人的荒谬行星", "小王子游历各小行星，见识了爱发号施令的国王、虚荣者与盲目忙碌的点灯人"),
                    Triple(3, "狐狸的秘密与爱的驯服", "在地球遇到五千朵相同的玫瑰而伤心，狐狸教会小王子爱的真谛与独一无二"),
                    Triple(4, "蛇的信约与重归星空", "为了对自己的玫瑰负责，小王子在沙漠中将肉身留在地球，灵魂重归星空"),
                ),
                locations = listOf(
                    Triple("🪐 B-612 小行星", "架空星际", "只有一间房子大的星球，拥有三座火山与一朵骄傲的玫瑰"),
                    Triple("🏜️ 撒哈拉大沙漠", "现实自然", "无边无际的金色沙海，飞行员迫降与奇遇之地"),
                    Triple("🌹 地球玫瑰园", "现实景观", "盛开着五千朵一模一样红玫瑰的花园，打破幻觉认知"),
                ),
                sessions = listOf(
                    Pair(45, "P.1 ~ P.45"),
                    Pair(60, "P.46 ~ P.96"),
                ),
                quotes = listOf(
                    Pair("正因为你为你的玫瑰花费了时间，这才使你的玫瑰变得如此重要。", "85"),
                    Pair("所有的大人都曾经是小孩，虽然，只有少数的人记得。", "3"),
                    Pair("如果你说你在下午四点来，从三点开始，我就开始感到快乐了。", "82"),
                ),
            )

            // 2. 《百年孤独》
            val solitudeId = findOrInsertBook(
                title = "百年孤独",
                author = "加西亚·马尔克斯",
                category = "拉美文学",
                status = "finished",
                shortComment = "多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。",
                review = "魔幻现实主义的巅峰神作。七代人的宿命轮回，将人类无法摆脱的孤独、激情、荒诞与历史遗忘写到了极致。",
                rating = 5.0,
                tags = listOf("魔幻现实", "拉美", "家族史诗", "经典"),
                coverUrl = "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=600",
                buyChannel = "京东自营 · 范晔译本",
                shelfLocation = "书架第2层 · 拉美魔幻现实",
                bindingType = "精装布面烫金",
                buyPrice = 59.8,
            )
            populateForBook(
                bookId = solitudeId,
                depth = 10.0, artistry = 9.8, emotion = 9.0, logic = 8.8, diff = 7.5, heal = 6.0,
                characters = listOf(
                    Triple("何塞·阿尔卡蒂奥·布恩迪亚", "马孔多缔造者", "狂热探求炼金术与科学的家族始祖，晚年被绑在栗树下"),
                    Triple("乌尔苏拉", "家族坚韧之母", "活过百岁维系着庞大家族运转的伟大女性，家族百年兴衰见证者"),
                    Triple("奥雷里亚诺·布恩迪亚上校", "传奇革命将领", "发动过32次武装起义，晚年在孤独中反复制作金小鱼"),
                    Triple("梅尔基亚德斯", "吉普赛预言智者", "带来磁铁手稿，预言了马孔多的兴起与被最后的飓风抹去"),
                ),
                outlines = listOf(
                    Triple(1, "马孔多的诞生与吉普赛魔术", "布恩迪亚夫妇穿过沼泽建立马孔多，世界初开时万物未命名"),
                    Triple(2, "三十二场内战与金小鱼", "上校转战南北看破虚无，在孤独中熔炼金小鱼度过余生"),
                    Triple(3, "香蕉公司的屠杀与漫长阴雨", "外来资本摧毁马孔多，三千四百名工人被枪杀，随后下了一场近五年的阴雨"),
                    Triple(4, "羊皮纸手稿与最后的飓风", "家族最后一人破译羊皮纸，飓风席卷马孔多将其从大地上彻底抹去"),
                ),
                locations = listOf(
                    Triple("🏡 马孔多 (Macondo)", "魔幻现实", "被沼泽与河流环绕的封闭市镇，见证布恩迪亚家族七代兴衰"),
                ),
                sessions = listOf(
                    Pair(50, "P.1 ~ P.60"),
                    Pair(75, "P.180 ~ P.260"),
                ),
                quotes = listOf(
                    Pair("多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。", "1"),
                    Pair("家族中的第一个人将被绑在树上，最后一个人正在被蚂蚁吃掉。", "356"),
                ),
            )

            // 3. 《三体》
            val threeBodyId = findOrInsertBook(
                title = "三体",
                author = "刘慈欣",
                category = "科幻小说",
                status = "finished",
                shortComment = "给岁月以文明，而不是给文明以岁月。",
                review = "中国科幻的巍峨丰碑。从红岸基地的第一声啼鸣到整个宇宙的黑暗森林法则，宏大的想象力与冷峻的文明思考令人叹为观止。",
                rating = 5.0,
                tags = listOf("硬核科幻", "宇宙文明", "硬科幻", "经典"),
                coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                buyChannel = "科幻世界特约渠道",
                shelfLocation = "书架第1层 · 硬核科幻神作",
                bindingType = "精装硬壳纪念版",
                buyPrice = 52.0,
            )
            populateForBook(
                bookId = threeBodyId,
                depth = 9.8, artistry = 8.5, emotion = 8.8, logic = 10.0, diff = 6.8, heal = 4.5,
                characters = listOf(
                    Triple("叶文洁", "红岸统帅 / 执剑先祖", "目睹创伤后对人性绝望，向宇宙发出引向三体文明的第一封信号"),
                    Triple("汪淼", "纳米材料科学家", "幽灵倒计时见证者，进入三体VR游戏破译三体世界规律"),
                    Triple("史强 (大史)", "刑警队长 / 粗中有细的智者", "以‘虫子从来没有被真正消灭过’鼓舞人类斗志的草莽英雄"),
                ),
                outlines = listOf(
                    Triple(1, "红岸基地与宇宙初鸣", "叶文洁利用太阳能量镜面增益放大电波，向宇宙发射呼救信号"),
                    Triple(2, "幽灵倒计时与三体游戏", "汪淼眼前出现倒计时，通过纳米材料与VR游戏揭开三日凌空灾难"),
                    Triple(3, "古筝行动与智子封锁", "纳米飞刃切开审判日号截获三体信息，得知智子已到达地球封锁基础科学"),
                ),
                locations = listOf(
                    Triple("📡 红岸基地", "历史科研", "位于大兴安岭雷达峰的绝密国防科考基地，拥有巨大抛物面天线"),
                    Triple("🌌 半人马座α三星系统", "硬核科幻", "三颗恒星无规则运转的混沌地狱，乱纪元与恒纪元残酷交替"),
                ),
                sessions = listOf(
                    Pair(60, "P.1 ~ P.80"),
                    Pair(90, "P.200 ~ P.280"),
                ),
                quotes = listOf(
                    Pair("给岁月以文明，而不是给文明以岁月。", "218"),
                    Pair("不要回答！不要回答！不要回答！", "176"),
                    Pair("弱小和无知不是生存的障碍，傲慢才是。", "190"),
                ),
            )

            // 4. 《新世纪福音战士：终》 (番剧 / 动漫)
            val evaId = findOrInsertBook(
                title = "新世纪福音战士：终",
                author = "庵野秀明 · Khara",
                category = "神作番剧",
                status = "finished",
                shortComment = "不能逃避，面对人与人之间的AT力场，向所有的福音战士告别，再见所有的Evangelion。",
                review = "跨越四分之一个世纪的青春终章。庵野秀明用最真诚的成年人笔触，打破了虚幻的避难所，教我们走出忧郁，拥抱真实的人间与现实世界。",
                rating = 5.0,
                tags = listOf("神作番剧", "机甲科幻", "哲学心智", "治愈成长"),
                coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                buyChannel = "Bilibili 番剧 · 正版特装",
                shelfLocation = "展厅第3层 · 经典番剧回廊",
                bindingType = "BD 蓝光典藏全集",
                buyPrice = 128.0,
                mediaType = "anime",
            )
            populateForBook(
                bookId = evaId,
                depth = 9.9, artistry = 9.6, emotion = 9.8, logic = 8.5, diff = 6.0, heal = 9.5,
                characters = listOf(
                    Triple("碇真嗣", "初号机适格者", "背负巨大心理创伤的少年，历经三次冲击与补完，最终选择走出孤独拥抱现实"),
                    Triple("绫波丽", "零号机驾驶员 / 灵魂之源", "人造灵魂与神性容器，在第三村体验了人间烟火与劳作温暖的无垢少女"),
                    Triple("式波·明日香", "二号机驾驶员 / 骄傲的战士", "用坚硬外壳包裹脆弱自尊的王牌驾驶员，在绝境中完成自我和解"),
                ),
                outlines = listOf(
                    Triple(1, "第三村的生活与人间烟火", "真嗣在废墟后的避难村落休养，黑丽体验耕种与情感，真嗣逐渐走出绝望"),
                    Triple(2, "最终作战：大和号进发", "WILLE舰队驶向南极中心，与NERV终极机体展开悲壮决战"),
                    Triple(3, "心之补完与再见所有EVA", "真嗣在意识宇宙与父亲碇源堂对谈解开心结，重塑没有EVA的新现实世界"),
                ),
                locations = listOf(
                    Triple("🌾 第三村 (Village 3)", "末世田园", "废墟世界中充满人情味的幸存者村落，木质电车与麦田"),
                    Triple("🌊 巴黎塞纳河畔 / 新世界", "现实人间", "脱去特摄战斗服后的现实车站，拥抱阳光与奔跑的街道"),
                ),
                sessions = listOf(
                    Pair(155, "全剧完结"),
                ),
                quotes = listOf(
                    Pair("不能逃避，不能逃避……只要活着，哪里都是天堂。", "全篇"),
                    Pair("再见了，所有的福音战士 (Neon Genesis)。", "终幕"),
                ),
            )
        }
    }

    fun insertBook(book: Book): Long {
        val now = currentTimestamp()
        val values = book.toContentValues().apply {
            put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
            put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOKS, null, values)
    }

    fun getBooks(status: BookStatus? = null): List<Book> {
        val selectionParts = mutableListOf("$COLUMN_IS_DELETED = ?")
        val selectionArgs = mutableListOf("0")
        if (status != null) {
            selectionParts += "$COLUMN_STATUS = ?"
            selectionArgs += status.databaseValue
        }

        return readableDatabase.query(
            TABLE_BOOKS,
            null,
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            null,
            null,
            "$COLUMN_UPDATED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }
    }

    fun getBook(bookId: Long): Book? =
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook() else null
        }

    fun updateBook(book: Book): Boolean {
        if (book.id <= 0) return false
        val values = book.toContentValues().apply {
            put(COLUMN_UPDATED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(book.id.toString(), "0"),
        ) > 0
    }

    fun archiveBook(bookId: Long): Boolean {
        if (bookId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
        ) > 0
    }

    /**
     * 恢复已归档的书籍
     */
    fun restoreBook(bookId: Long): Boolean {
        if (bookId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "1"),
        ) > 0
    }

    /**
     * 获取所有已归档的书籍（回收站）
     */
    fun getArchivedBooks(): List<Book> {
        return readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_IS_DELETED = ?",
            arrayOf("1"),
            null,
            null,
            "$COLUMN_DELETED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }
    }

    /**
     * 彻底物理删除书籍（物理清理关联封面文件、关联笔记及书籍本体）
     * 必须在用户二次确认后调用
     */
    fun hardDeleteBook(bookId: Long): Boolean {
        if (bookId <= 0) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. 查询并清理关联的本地封面文件
            val book = getBookAny(bookId)
            book?.coverUrl?.let { path ->
                CoverImageHelper.deleteCoverFile(path)
            }

            // 2. 删除关联的笔记
            db.delete(TABLE_NOTES, "$COLUMN_BOOK_ID = ?", arrayOf(bookId.toString()))

            // 3. 删除书籍记录
            val deleted = db.delete(TABLE_BOOKS, "$COLUMN_ID = ?", arrayOf(bookId.toString())) > 0
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    private fun getBookAny(bookId: Long): Book? =
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(bookId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook() else null
        }

    /**
     * 批量导入书籍，利用事务提升性能，并检查标题和作者去重
     * @return 实际成功插入的新书籍数量
     */
    fun importBooks(books: List<Book>): Int {
        if (books.isEmpty()) return 0
        val db = writableDatabase
        var insertedCount = 0
        db.beginTransaction()
        try {
            val now = currentTimestamp()
            for (book in books) {
                val cleanTitle = book.title.trim()
                if (cleanTitle.isEmpty()) continue

                // 查重：同名且同作者（未归档）则跳过
                val exists = isBookExists(db, cleanTitle, book.author?.trim())
                if (!exists) {
                    val values = book.toContentValues().apply {
                        put(COLUMN_TITLE, cleanTitle)
                        put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
                        put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
                        put(COLUMN_IS_DELETED, 0)
                        putNull(COLUMN_DELETED_AT)
                    }
                    val rowId = db.insert(TABLE_BOOKS, null, values)
                    if (rowId > 0) {
                        insertedCount++
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return insertedCount
    }

    private fun isBookExists(db: SQLiteDatabase, title: String, author: String?): Boolean {
        val selection: String
        val args: Array<String>
        if (author.isNullOrEmpty()) {
            selection = "$COLUMN_TITLE = ? AND ($COLUMN_AUTHOR IS NULL OR $COLUMN_AUTHOR = '') AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(title)
        } else {
            selection = "$COLUMN_TITLE = ? AND $COLUMN_AUTHOR = ? AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(title, author)
        }
        return db.query(TABLE_BOOKS, arrayOf(COLUMN_ID), selection, args, null, null, null, "1").use { cursor ->
            cursor.moveToFirst()
        }
    }

    /**
     * 获取「那年今日」回忆书籍与描述信息。
     * 优先匹配历史年份今日读完的书籍，次之随机精选一本已读书籍作为时光漫忆。
     */
    fun getMemoryBook(): Pair<Book, String>? {
        val today = LocalDate.now()
        val monthDayPattern = String.format("%%-%02d-%02d", today.monthValue, today.dayOfMonth)

        // 1. 查询历史同月同日读完的书籍（非当年今天）
        val todayBooks = readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_FINISH_DATE LIKE ? AND $COLUMN_IS_DELETED = 0 AND $COLUMN_STATUS = ?",
            arrayOf(monthDayPattern, BookStatus.FINISHED.databaseValue),
            null,
            null,
            "$COLUMN_FINISH_DATE DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }

        // 寻找非今天（往年）读完的书
        for (book in todayBooks) {
            val finishDateStr = book.finishDate ?: continue
            val finishDate = runCatching { LocalDate.parse(finishDateStr) }.getOrNull() ?: continue
            val years = ChronoUnit.YEARS.between(finishDate, today)
            if (years > 0) {
                return Pair(book, "${years} 年前的今天，你读完了这本书")
            } else if (finishDate == today) {
                return Pair(book, "今天读完的书籍，愿余味长存")
            }
        }

        // 2. 如果无今日匹配，则优选一本最近或评分较高的已读书籍作为时光漫忆
        val finishedBooks = readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_STATUS = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(BookStatus.FINISHED.databaseValue, "0"),
            null,
            null,
            "$COLUMN_RATING DESC, $COLUMN_UPDATED_AT DESC",
            "10",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }

        if (finishedBooks.isNotEmpty()) {
            val randomBook = finishedBooks.random()
            return Pair(randomBook, "时光漫忆 · 曾留在心里的作品")
        }

        return null
    }

    /**
     * 获取月度读完统计（按完成日期月份统计）
     */
    fun getMonthlyFinishedStats(limit: Int = 6): List<MonthlyReadingStat> {
        val sql = """
            SELECT SUBSTR($COLUMN_FINISH_DATE, 1, 7) AS month_str, COUNT(*) AS count_num
            FROM $TABLE_BOOKS
            WHERE $COLUMN_IS_DELETED = 0 
              AND $COLUMN_STATUS = '${BookStatus.FINISHED.databaseValue}'
              AND $COLUMN_FINISH_DATE IS NOT NULL 
              AND length($COLUMN_FINISH_DATE) >= 7
            GROUP BY month_str
            ORDER BY month_str DESC
            LIMIT ?
        """.trimIndent()

        return readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val month = cursor.getString(0) ?: ""
                    val count = cursor.getInt(1)
                    if (month.isNotEmpty()) {
                        add(MonthlyReadingStat(month, count))
                    }
                }
            }
        }
    }

    fun insertNote(note: Note): Long {
        val now = currentTimestamp()
        val values = note.toContentValues().apply {
            put(COLUMN_CREATED_AT, note.createdAt.ifBlank { now })
            put(COLUMN_UPDATED_AT, note.updatedAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
        }
        return writableDatabase.insertOrThrow(TABLE_NOTES, null, values)
    }

    fun getNotes(bookId: Long): List<Note> =
        readableDatabase.query(
            TABLE_NOTES,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
            null,
            null,
            "$COLUMN_CREATED_AT ASC, $COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toNote())
                }
            }
        }

    fun getNote(noteId: Long): Note? =
        readableDatabase.query(
            TABLE_NOTES,
            null,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "0"),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toNote() else null
        }

    fun updateNote(note: Note): Boolean {
        if (note.id <= 0) return false
        val values = note.toContentValues().apply {
            put(COLUMN_UPDATED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(note.id.toString(), "0"),
        ) > 0
    }

    fun archiveNote(noteId: Long): Boolean {
        if (noteId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "0"),
        ) > 0
    }

    /**
     * 恢复已归档的笔记
     */
    fun restoreNote(noteId: Long): Boolean {
        if (noteId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "1"),
        ) > 0
    }

    /**
     * 获取所有已归档的笔记（带所属书名）
     */
    fun getArchivedNotes(): List<ArchivedNoteItem> {
        val sql = """
            SELECT n.$COLUMN_ID, n.$COLUMN_BOOK_ID, n.$COLUMN_CONTENT, n.$COLUMN_NOTE_TYPE,
                   n.$COLUMN_PAGE, n.$COLUMN_CHAPTER, n.$COLUMN_CREATED_AT, n.$COLUMN_UPDATED_AT,
                   n.$COLUMN_IS_DELETED, n.$COLUMN_DELETED_AT, b.$COLUMN_TITLE AS book_title
            FROM $TABLE_NOTES n
            LEFT JOIN $TABLE_BOOKS b ON n.$COLUMN_BOOK_ID = b.$COLUMN_ID
            WHERE n.$COLUMN_IS_DELETED = 1
            ORDER BY n.$COLUMN_DELETED_AT DESC, n.$COLUMN_ID DESC
        """.trimIndent()

        return readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val note = cursor.toNote()
                    val titleIndex = cursor.getColumnIndex("book_title")
                    val bookTitle = if (titleIndex != -1 && !cursor.isNull(titleIndex)) cursor.getString(titleIndex) else null
                    add(ArchivedNoteItem(note, bookTitle))
                }
            }
        }
    }

    /**
     * 彻底物理删除笔记
     * 必须在用户二次确认后调用
     */
    fun hardDeleteNote(noteId: Long): Boolean {
        if (noteId <= 0) return false
        return writableDatabase.delete(
            TABLE_NOTES,
            "$COLUMN_ID = ?",
            arrayOf(noteId.toString()),
        ) > 0
    }

    /**
     * 彻底清空回收站中的所有书籍与笔记，并清理所有相关封面图片
     * 必须在用户二次确认后调用
     * @return Pair(删除的书籍数量, 删除的笔记数量)
     */
    fun clearAllTrash(): Pair<Int, Int> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. 获取所有归档书籍的封面并清理文件
            val archivedBooks = getArchivedBooks()
            archivedBooks.forEach { book ->
                book.coverUrl?.let { CoverImageHelper.deleteCoverFile(it) }
            }

            // 2. 物理删除所有归档书籍关联的笔记及单独归档的笔记
            val deletedNotesCount = db.delete(TABLE_NOTES, "$COLUMN_IS_DELETED = 1", null)
            val deletedBooksCount = db.delete(TABLE_BOOKS, "$COLUMN_IS_DELETED = 1", null)

            db.setTransactionSuccessful()
            return Pair(deletedBooksCount, deletedNotesCount)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 获取全部作品及各自关联的笔记（用于全量备份与多格式导出）
     */
    fun getAllWorksWithNotes(): List<Pair<Book, List<Note>>> {
        val books = getBooks()
        return books.map { book ->
            val notes = getNotes(book.id)
            Pair(book, notes)
        }
    }

    /**
     * 导入全量备份数据（含作品与笔记）
     * @return Pair(成功导入的新增作品数, 成功导入的笔记数)
     */
    fun importFullBackup(items: List<Pair<Book, List<Note>>>): Pair<Int, Int> {
        if (items.isEmpty()) return Pair(0, 0)
        val db = writableDatabase
        db.beginTransaction()
        var importedWorks = 0
        var importedNotes = 0
        try {
            items.forEach { (book, notes) ->
                // 1. 查找是否存在同名且同作者/创作者的作品
                val existingBookId = findBookId(db, book.title, book.author)
                val targetBookId = if (existingBookId != null) {
                    existingBookId
                } else {
                    val values = book.toContentValues().apply {
                        put(COLUMN_CREATED_AT, if (book.createdAt.isNotBlank()) book.createdAt else currentTimestamp())
                        put(COLUMN_UPDATED_AT, if (book.updatedAt.isNotBlank()) book.updatedAt else currentTimestamp())
                        put(COLUMN_IS_DELETED, if (book.isDeleted) 1 else 0)
                        putNullable(COLUMN_DELETED_AT, book.deletedAt)
                    }
                    val newId = db.insert(TABLE_BOOKS, null, values)
                    if (newId > 0) {
                        importedWorks++
                        newId
                    } else null
                }

                if (targetBookId != null) {
                    // 2. 导入关联的笔记（避免重复内容）
                    val existingNotes = getNotes(targetBookId)
                    notes.forEach { note ->
                        val isDuplicate = existingNotes.any { it.content.trim() == note.content.trim() }
                        if (!isDuplicate && note.content.isNotBlank()) {
                            val noteValues = note.copy(bookId = targetBookId).toContentValues().apply {
                                put(COLUMN_CREATED_AT, if (note.createdAt.isNotBlank()) note.createdAt else currentTimestamp())
                                put(COLUMN_UPDATED_AT, if (note.updatedAt.isNotBlank()) note.updatedAt else currentTimestamp())
                                put(COLUMN_IS_DELETED, if (note.isDeleted) 1 else 0)
                                putNullable(COLUMN_DELETED_AT, note.deletedAt)
                            }
                            if (db.insert(TABLE_NOTES, null, noteValues) > 0) {
                                importedNotes++
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
            return Pair(importedWorks, importedNotes)
        } finally {
            db.endTransaction()
        }
    }

    private fun findBookId(db: SQLiteDatabase, title: String, author: String?): Long? {
        val trimmedTitle = title.trim()
        val trimmedAuthor = author?.trim()
        val selection: String
        val args: Array<String>
        if (trimmedAuthor.isNullOrEmpty()) {
            selection = "$COLUMN_TITLE = ? AND ($COLUMN_AUTHOR IS NULL OR $COLUMN_AUTHOR = '') AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(trimmedTitle)
        } else {
            selection = "$COLUMN_TITLE = ? AND $COLUMN_AUTHOR = ? AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(trimmedTitle, trimmedAuthor)
        }
        return db.query(TABLE_BOOKS, arrayOf(COLUMN_ID), selection, args, null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /**
     * 获取 3D 私人展厅陈列精选作品（优先高分 rating >= 8.0 或 已完成 finished，若不足则补充最新收录，上限 24 部）
     */
    fun getGalleryFeaturedWorks(limit: Int = 24): List<Book> {
        val allBooks = getBooks()
        if (allBooks.isEmpty()) return emptyList()

        // 优先筛选高分 (>= 8.0) 或已读完作品
        val featured = allBooks.filter {
            (it.rating != null && it.rating >= 8.0) || it.status == BookStatus.FINISHED
        }.sortedWith(
            compareByDescending<Book> { it.rating ?: 0.0 }
                .thenByDescending { it.updatedAt },
        )

        if (featured.size >= limit) {
            return featured.take(limit)
        }

        // 如果不足 limit 部，则从其余作品中按最新更新时间补充
        val remaining = allBooks.filterNot { featured.contains(it) }
            .sortedByDescending { it.updatedAt }

        return (featured + remaining).take(limit)
    }

    /**
     * 获取所有书籍的不重复标签列表及频次统计，按出现频次降序排列
     */
    fun getAllUniqueTags(): List<Pair<String, Int>> {
        val tagCountMap = mutableMapOf<String, Int>()
        val books = getBooks()
        books.forEach { book ->
            book.tags.forEach { tag ->
                val clean = tag.trim()
                if (clean.isNotEmpty()) {
                    tagCountMap[clean] = (tagCountMap[clean] ?: 0) + 1
                }
            }
        }
        return tagCountMap.toList().sortedByDescending { it.second }
    }

    /**
     * 获取书籍上次阅读的页码
     */
    fun getReadingPage(bookId: Long): Int {
        val sp = context.getSharedPreferences("readtrace_reader_prefs", Context.MODE_PRIVATE)
        return sp.getInt("book_page_$bookId", 0)
    }

    /**
     * 保存书籍当前阅读页码
     */
    fun saveReadingPage(bookId: Long, pageIndex: Int) {
        val sp = context.getSharedPreferences("readtrace_reader_prefs", Context.MODE_PRIVATE)
        sp.edit().putInt("book_page_$bookId", pageIndex).apply()
    }

    /**
     * 获取已读完书籍总数
     */
    fun getTotalFinishedBooksCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_STATUS = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(BookStatus.FINISHED.databaseValue),
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取有效书籍总数
     */
    fun getTotalBooksCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取有效笔记总数
     */
    fun getTotalNotesCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NOTES WHERE $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取不同书籍分类总数
     */
    fun getUniqueCategoriesCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT $COLUMN_CATEGORY) FROM $TABLE_BOOKS WHERE $COLUMN_CATEGORY IS NOT NULL AND TRIM($COLUMN_CATEGORY) != '' AND $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取 9.0 分及以上的高分好评书籍数量
     */
    fun getHighRatingBooksCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_RATING >= 9.0 AND $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // --- ⏱️ 阅读打卡日志 (Reading Sessions) ---

    fun insertReadingSession(session: ReadingSession): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, session.bookId)
            put(COLUMN_DURATION_MINUTES, session.durationMinutes)
            putNullable(COLUMN_PAGES_READ, session.pagesRead)
            putNullable(COLUMN_THOUGHT, session.thought)
            put(COLUMN_CREATED_AT, session.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_READING_SESSIONS, null, values)
    }

    fun getReadingSessions(bookId: Long): List<ReadingSession> =
        readableDatabase.query(
            TABLE_READING_SESSIONS,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_CREATED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toReadingSession())
                }
            }
        }

    fun getTotalReadingMinutes(bookId: Long): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM($COLUMN_DURATION_MINUTES) FROM $TABLE_READING_SESSIONS WHERE $COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun deleteReadingSession(sessionId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_READING_SESSIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(sessionId.toString()),
        ) > 0
    }

    // --- 👥 人物角色谱 (Book Characters) ---

    fun insertCharacter(character: BookCharacter): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, character.bookId)
            put(COLUMN_NAME, character.name.trim())
            putNullable(COLUMN_ROLE_TITLE, character.roleTitle)
            put(COLUMN_AVATAR_EMOJI, character.avatarEmoji.ifBlank { "👤" })
            putNullable(COLUMN_DESCRIPTION, character.description)
            putNullable(COLUMN_RELATIONSHIP, character.relationship)
            put(COLUMN_CREATED_AT, character.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOK_CHARACTERS, null, values)
    }

    fun getCharacters(bookId: Long): List<BookCharacter> =
        readableDatabase.query(
            TABLE_BOOK_CHARACTERS,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBookCharacter())
                }
            }
        }

    fun deleteCharacter(characterId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_BOOK_CHARACTERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(characterId.toString()),
        ) > 0
    }

    // --- 🗺️ 章节大纲与脑图 (Book Outlines) ---

    fun insertOutline(outline: BookOutline): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, outline.bookId)
            put(COLUMN_CHAPTER_ORDER, outline.chapterOrder)
            put(COLUMN_TITLE, outline.title.trim())
            put(COLUMN_SUMMARY, outline.summary.trim())
            putNullable(COLUMN_KEY_TAKEAWAYS, outline.keyTakeaways)
            put(COLUMN_CREATED_AT, outline.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOK_OUTLINES, null, values)
    }

    fun getOutlines(bookId: Long): List<BookOutline> =
        readableDatabase.query(
            TABLE_BOOK_OUTLINES,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_CHAPTER_ORDER ASC, $COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBookOutline())
                }
            }
        }

    fun deleteOutline(outlineId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_BOOK_OUTLINES,
            values,
            "$COLUMN_ID = ?",
            arrayOf(outlineId.toString()),
        ) > 0
    }

    // --- 🗺️ 空间地标与叙事足迹 (Book Locations) ---

    fun insertLocation(location: BookLocation): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, location.bookId)
            put(COLUMN_NAME, location.name.trim())
            put(COLUMN_LOCATION_TYPE, location.locationType.ifBlank { "🏙️ 现实都市" })
            putNullable(COLUMN_DESCRIPTION, location.description)
            putNullable(COLUMN_SIGNIFICANCE, location.significance)
            putNullable(COLUMN_COORDINATES, location.coordinates)
            put(COLUMN_CREATED_AT, location.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOK_LOCATIONS, null, values)
    }

    fun getLocations(bookId: Long): List<BookLocation> =
        readableDatabase.query(
            TABLE_BOOK_LOCATIONS,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBookLocation())
                }
            }
        }

    fun deleteLocation(locationId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_BOOK_LOCATIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(locationId.toString()),
        ) > 0
    }

    // --- 🕸️ 六维心智评分雷达 (Book Mindprints) ---

    fun saveMindprint(mindprint: BookMindprint): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, mindprint.bookId)
            put(COLUMN_DEPTH_SCORE, mindprint.depthScore)
            put(COLUMN_ARTISTRY_SCORE, mindprint.artistryScore)
            put(COLUMN_EMOTION_SCORE, mindprint.emotionScore)
            put(COLUMN_LOGIC_SCORE, mindprint.logicScore)
            put(COLUMN_DIFFICULTY_SCORE, mindprint.difficultyScore)
            put(COLUMN_HEALING_SCORE, mindprint.healingScore)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.insertWithOnConflict(
            TABLE_BOOK_MINDPRINTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getMindprint(bookId: Long): BookMindprint =
        readableDatabase.query(
            TABLE_BOOK_MINDPRINTS,
            null,
            "$COLUMN_BOOK_ID = ?",
            arrayOf(bookId.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBookMindprint() else BookMindprint(bookId = bookId)
        }

    fun getAnnualMindprintPersona(): com.example.readtrace.model.ReadingPersona? {
        val finishedBooks = getBooks(BookStatus.FINISHED)
        if (finishedBooks.isEmpty()) return null

        val mindprints = finishedBooks.map { getMindprint(it.id) }
        val count = mindprints.size.toDouble()

        val avgDepth = mindprints.sumOf { it.depthScore } / count
        val avgArtistry = mindprints.sumOf { it.artistryScore } / count
        val avgEmotion = mindprints.sumOf { it.emotionScore } / count
        val avgLogic = mindprints.sumOf { it.logicScore } / count
        val avgDifficulty = mindprints.sumOf { it.difficultyScore } / count
        val avgHealing = mindprints.sumOf { it.healingScore } / count

        val avgMindprint = com.example.readtrace.model.BookMindprint(
            bookId = 0L,
            depthScore = avgDepth,
            artistryScore = avgArtistry,
            emotionScore = avgEmotion,
            logicScore = avgLogic,
            difficultyScore = avgDifficulty,
            healingScore = avgHealing,
        )

        val dims = listOf(
            "depth" to avgDepth,
            "artistry" to avgArtistry,
            "emotion" to avgEmotion,
            "logic" to avgLogic,
            "difficulty" to avgDifficulty,
            "healing" to avgHealing,
        )
        val maxDim = dims.maxByOrNull { it.second }?.first ?: "depth"

        val (title, desc, dominant) = when (maxDim) {
            "depth" -> Triple("🧠 深邃哲思探索者", "沉醉于对世界本质与生命哲理的深度审视，在思想高原上自由漫步。", "思想深度")
            "artistry" -> Triple("🖋️ 唯美文学审美家", "对文字的韵律美、诗性意境与修辞质感具有极高的审美敏锐度。", "文笔意境")
            "emotion" -> Triple("❤️ 细腻共鸣共情家", "在字里行间捕获最真挚的人性温热，以心感应万千生灵的喜怒哀乐。", "情感共鸣")
            "logic" -> Triple("📐 严密理性格局派", "追求严丝合缝的因果规律与宏大世界构建，崇尚清晰有力的理性推演。", "逻辑构架")
            "difficulty" -> Triple("⛰️ 硬核学术攀登者", "敢于直面深奥晦涩的经典大作与思想峻岭，在攀登中享受智识蜕变。", "思想门槛")
            else -> Triple("🌿 纯粹心灵疗愈者", "在静谧的书海中寻找灵魂的安顿与精神绿洲，温和而坚定地被文字抚慰。", "心灵治愈")
        }

        return com.example.readtrace.model.ReadingPersona(
            personaTitle = title,
            personaDesc = desc,
            dominantDimension = dominant,
            avgMindprint = avgMindprint,
            finishedBooksCount = finishedBooks.size,
        )
    }

    private fun Cursor.toBook(): Book =
        Book(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
            author = getNullableString(COLUMN_AUTHOR),
            coverUrl = getNullableString(COLUMN_COVER_URL),
            category = getNullableString(COLUMN_CATEGORY),
            status = BookStatus.fromDatabaseValue(
                getString(getColumnIndexOrThrow(COLUMN_STATUS)),
            ),
            mediaType = MediaType.fromDatabaseValue(
                getNullableString(COLUMN_MEDIA_TYPE),
            ),
            rating = getNullableDouble(COLUMN_RATING),
            tags = parseTags(getNullableString(COLUMN_TAGS)),
            shortComment = getNullableString(COLUMN_SHORT_COMMENT),
            review = getNullableString(COLUMN_REVIEW),
            startDate = getNullableString(COLUMN_START_DATE),
            finishDate = getNullableString(COLUMN_FINISH_DATE),
            buyChannel = getNullableString(COLUMN_BUY_CHANNEL),
            shelfLocation = getNullableString(COLUMN_SHELF_LOCATION),
            bindingType = getNullableString(COLUMN_BINDING_TYPE),
            buyPrice = getNullableDouble(COLUMN_BUY_PRICE),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            updatedAt = getString(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
            deletedAt = getNullableString(COLUMN_DELETED_AT),
        )

    private fun Cursor.toNote(): Note =
        Note(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            content = getString(getColumnIndexOrThrow(COLUMN_CONTENT)),
            noteType = NoteType.fromDatabaseValue(
                getString(getColumnIndexOrThrow(COLUMN_NOTE_TYPE)),
            ),
            page = getNullableString(COLUMN_PAGE),
            chapter = getNullableString(COLUMN_CHAPTER),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            updatedAt = getString(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
            deletedAt = getNullableString(COLUMN_DELETED_AT),
        )

    private fun Cursor.toReadingSession(): ReadingSession =
        ReadingSession(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            durationMinutes = getInt(getColumnIndexOrThrow(COLUMN_DURATION_MINUTES)),
            pagesRead = getNullableString(COLUMN_PAGES_READ),
            thought = getNullableString(COLUMN_THOUGHT),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookCharacter(): BookCharacter =
        BookCharacter(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            name = getString(getColumnIndexOrThrow(COLUMN_NAME)),
            roleTitle = getNullableString(COLUMN_ROLE_TITLE),
            avatarEmoji = getNullableString(COLUMN_AVATAR_EMOJI) ?: "👤",
            description = getNullableString(COLUMN_DESCRIPTION),
            relationship = getNullableString(COLUMN_RELATIONSHIP),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookOutline(): BookOutline =
        BookOutline(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            chapterOrder = getInt(getColumnIndexOrThrow(COLUMN_CHAPTER_ORDER)),
            title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
            summary = getString(getColumnIndexOrThrow(COLUMN_SUMMARY)),
            keyTakeaways = getNullableString(COLUMN_KEY_TAKEAWAYS),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookLocation(): BookLocation =
        BookLocation(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            name = getString(getColumnIndexOrThrow(COLUMN_NAME)),
            locationType = getString(getColumnIndexOrThrow(COLUMN_LOCATION_TYPE)),
            description = getNullableString(COLUMN_DESCRIPTION),
            significance = getNullableString(COLUMN_SIGNIFICANCE),
            coordinates = getNullableString(COLUMN_COORDINATES),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookMindprint(): BookMindprint =
        BookMindprint(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            depthScore = getDouble(getColumnIndexOrThrow(COLUMN_DEPTH_SCORE)),
            artistryScore = getDouble(getColumnIndexOrThrow(COLUMN_ARTISTRY_SCORE)),
            emotionScore = getDouble(getColumnIndexOrThrow(COLUMN_EMOTION_SCORE)),
            logicScore = getDouble(getColumnIndexOrThrow(COLUMN_LOGIC_SCORE)),
            difficultyScore = getDouble(getColumnIndexOrThrow(COLUMN_DIFFICULTY_SCORE)),
            healingScore = getDouble(getColumnIndexOrThrow(COLUMN_HEALING_SCORE)),
            updatedAt = getString(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
        )

    private fun Cursor.getNullableString(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getNullableDouble(columnName: String): Double? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun parseTags(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) putNull(key) else put(key, normalized)
    }

    private fun Book.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_TITLE, title.trim())
            putNullable(COLUMN_AUTHOR, author)
            putNullable(COLUMN_COVER_URL, coverUrl)
            putNullable(COLUMN_CATEGORY, category)
            put(COLUMN_STATUS, status.databaseValue)
            put(COLUMN_MEDIA_TYPE, mediaType.databaseValue)
            if (rating == null) putNull(COLUMN_RATING) else put(COLUMN_RATING, rating)
            put(COLUMN_TAGS, JSONArray(tags).toString())
            putNullable(COLUMN_SHORT_COMMENT, shortComment)
            putNullable(COLUMN_REVIEW, review)
            putNullable(COLUMN_START_DATE, startDate)
            putNullable(COLUMN_FINISH_DATE, finishDate)
            putNullable(COLUMN_BUY_CHANNEL, buyChannel)
            putNullable(COLUMN_SHELF_LOCATION, shelfLocation)
            putNullable(COLUMN_BINDING_TYPE, bindingType)
            if (buyPrice == null) putNull(COLUMN_BUY_PRICE) else put(COLUMN_BUY_PRICE, buyPrice)
        }

    private fun Note.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_BOOK_ID, bookId)
            put(COLUMN_CONTENT, content.trim())
            put(COLUMN_NOTE_TYPE, noteType.databaseValue)
            putNullable(COLUMN_PAGE, page)
            putNullable(COLUMN_CHAPTER, chapter)
        }

    private fun currentTimestamp(): String =
        OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    companion object {
        const val DATABASE_NAME = "readtrace.db"
        const val DATABASE_VERSION = 5

        private const val TABLE_BOOKS = "books"
        private const val TABLE_NOTES = "notes"
        private const val TABLE_READING_SESSIONS = "reading_sessions"
        private const val TABLE_BOOK_CHARACTERS = "book_characters"
        private const val TABLE_BOOK_OUTLINES = "book_outlines"
        private const val TABLE_BOOK_LOCATIONS = "book_locations"
        private const val TABLE_BOOK_MINDPRINTS = "book_mindprints"

        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_AUTHOR = "author"
        private const val COLUMN_COVER_URL = "cover_url"
        private const val COLUMN_CATEGORY = "category"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_MEDIA_TYPE = "media_type"
        private const val COLUMN_RATING = "rating"
        private const val COLUMN_TAGS = "tags"
        private const val COLUMN_SHORT_COMMENT = "short_comment"
        private const val COLUMN_REVIEW = "review"
        private const val COLUMN_START_DATE = "start_date"
        private const val COLUMN_FINISH_DATE = "finish_date"
        private const val COLUMN_BUY_CHANNEL = "buy_channel"
        private const val COLUMN_SHELF_LOCATION = "shelf_location"
        private const val COLUMN_BINDING_TYPE = "binding_type"
        private const val COLUMN_BUY_PRICE = "buy_price"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_UPDATED_AT = "updated_at"
        private const val COLUMN_IS_DELETED = "is_deleted"
        private const val COLUMN_DELETED_AT = "deleted_at"

        private const val COLUMN_BOOK_ID = "book_id"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_NOTE_TYPE = "note_type"
        private const val COLUMN_PAGE = "page"
        private const val COLUMN_CHAPTER = "chapter"

        private const val COLUMN_DURATION_MINUTES = "duration_minutes"
        private const val COLUMN_PAGES_READ = "pages_read"
        private const val COLUMN_THOUGHT = "thought"

        private const val COLUMN_NAME = "name"
        private const val COLUMN_ROLE_TITLE = "role_title"
        private const val COLUMN_AVATAR_EMOJI = "avatar_emoji"
        private const val COLUMN_DESCRIPTION = "description"
        private const val COLUMN_RELATIONSHIP = "relationship"

        private const val COLUMN_CHAPTER_ORDER = "chapter_order"
        private const val COLUMN_SUMMARY = "summary"
        private const val COLUMN_KEY_TAKEAWAYS = "key_takeaways"

        private const val COLUMN_LOCATION_TYPE = "location_type"
        private const val COLUMN_SIGNIFICANCE = "significance"
        private const val COLUMN_COORDINATES = "coordinates"

        private const val COLUMN_DEPTH_SCORE = "depth_score"
        private const val COLUMN_ARTISTRY_SCORE = "artistry_score"
        private const val COLUMN_EMOTION_SCORE = "emotion_score"
        private const val COLUMN_LOGIC_SCORE = "logic_score"
        private const val COLUMN_DIFFICULTY_SCORE = "difficulty_score"
        private const val COLUMN_HEALING_SCORE = "healing_score"
    }
}
