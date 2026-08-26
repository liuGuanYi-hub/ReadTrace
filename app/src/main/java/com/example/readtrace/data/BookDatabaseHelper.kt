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
        populatePresetRichContent(db)
        seedUserAnimeList(db)
        seedUserMovieList(db)
        seedUserGameList(db)
        seedUserPodcastMusicList(db)
        seedCuratedBookCovers(db)
        autoFillMissingCovers(db)
    }

    /**
     * 批量补充全部预设作品的「登场角色」与「经典台词」。
     * 数据来自 assets 下的 rich_content_*.json，每次开库幂等执行：
     * 仅当该作品尚无角色/笔记数据时才写入，不会覆盖用户已有内容。
     */
    private fun populatePresetRichContent(db: SQLiteDatabase) {
        runCatching {
            val assetFiles = listOf(
                "rich_content_anime.json",
                "rich_content_books.json",
                "rich_content_games.json",
                "rich_content_movies_podcasts.json",
            )
            assetFiles.forEach { fileName ->
                val jsonText = runCatching {
                    context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull() ?: return@forEach
                val entries = JSONArray(jsonText)
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val title = entry.optString("title")
                    if (title.isBlank()) continue
                    // 同名作品可能同时存在多条（如重复导入），全部补齐。
                    var bookIds = db.query(
                        TABLE_BOOKS, arrayOf(COLUMN_ID),
                        "$COLUMN_TITLE = ? AND $COLUMN_IS_DELETED = 0",
                        arrayOf(title), null, null, null,
                    ).use { c ->
                        buildList { while (c.moveToNext()) add(c.getLong(0)) }
                    }
                    if (bookIds.isEmpty()) {
                        // 短标题作 LIKE 匹配时易被长标题误中（如 Ib ⊂ ASTLIBRA），
                        // 因此限定为「以标题开头」，且要求匹配到的标题长度相近。
                        bookIds = db.query(
                            TABLE_BOOKS, arrayOf(COLUMN_ID, COLUMN_TITLE),
                            "$COLUMN_TITLE LIKE ? AND $COLUMN_IS_DELETED = 0",
                            arrayOf("$title%"), null, null, null,
                        ).use { c ->
                            buildList {
                                while (c.moveToNext()) {
                                    if (c.getString(1).length <= title.length + 4) add(c.getLong(0))
                                }
                            }
                        }
                    }
                    if (bookIds.isEmpty()) continue
                    val now = currentTimestamp()

                    val charCount = db.query(
                        TABLE_BOOK_CHARACTERS, arrayOf("COUNT(*)"),
                        "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
                        arrayOf(bookIds.first().toString()), null, null, null,
                    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
                    if (charCount == 0) {
                        val chars = entry.optJSONArray("characters") ?: JSONArray()
                        for (ci in 0 until chars.length()) {
                            val ch = chars.getJSONObject(ci)
                            bookIds.forEach { bookId ->
                                val cv = ContentValues().apply {
                                    put(COLUMN_BOOK_ID, bookId)
                                    put(COLUMN_NAME, ch.optString("name"))
                                    put(COLUMN_ROLE_TITLE, ch.optString("role"))
                                    put(COLUMN_AVATAR_EMOJI, ch.optString("emoji", "👤"))
                                    put(COLUMN_DESCRIPTION, ch.optString("desc"))
                                    put(COLUMN_CREATED_AT, now)
                                    put(COLUMN_IS_DELETED, 0)
                                }
                                db.insert(TABLE_BOOK_CHARACTERS, null, cv)
                            }
                        }
                    }

                    val noteCount = db.query(
                        TABLE_NOTES, arrayOf("COUNT(*)"),
                        "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
                        arrayOf(bookIds.first().toString()), null, null, null,
                    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
                    if (noteCount == 0) {
                        val quotes = entry.optJSONArray("quotes") ?: JSONArray()
                        for (qi in 0 until quotes.length()) {
                            val q = quotes.getJSONObject(qi)
                            bookIds.forEach { bookId ->
                                val cv = ContentValues().apply {
                                    put(COLUMN_BOOK_ID, bookId)
                                    put(COLUMN_CONTENT, q.optString("content"))
                                    put(COLUMN_NOTE_TYPE, "quote")
                                    put(COLUMN_PAGE, q.optString("source"))
                                    put(COLUMN_CREATED_AT, now)
                                    put(COLUMN_UPDATED_AT, now)
                                    put(COLUMN_IS_DELETED, 0)
                                }
                                db.insert(TABLE_NOTES, null, cv)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun seedCuratedBookCovers(db: SQLiteDatabase) {
        runCatching {
            data class BookCoverPreset(val title: String, val author: String, val category: String, val coverUrl: String, val shortComment: String)

            val presetList = listOf(
                BookCoverPreset("1984", "乔治·奥威尔", "反乌托邦", "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=600", "战争即和平，自由即奴役，无知即力量。"),
                BookCoverPreset("鼠疫", "加缪", "存在主义", "https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=600", "在这个世界上存在着瘟疫，也存在着受害者，我们应当尽量不站在瘟疫一边。"),
                BookCoverPreset("动物农场", "乔治·奥威尔", "政治讽喻", "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600", "所有动物生来平等，但有些动物比其他动物更平等。"),
                BookCoverPreset("诡计博物馆", "大山诚一郎", "本格推理", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", "封印二十年的悬案，在赤色博物馆内被纯粹的逻辑瞬间洞穿。"),
                BookCoverPreset("霍乱时期的爱情", "马尔克斯", "拉美文学", "https://images.unsplash.com/photo-1474932430478-367dbb6832c1?w=600", "跨越半个多世纪的等待，换来的是船头上那面永不落下的霍乱黄旗。"),
                BookCoverPreset("悲惨世界", "雨果", "法国文学", "https://images.unsplash.com/photo-1463320726281-696a485928c7?w=600", "释放无限光明的是人心，制造无边黑暗的也是人心。"),
                BookCoverPreset("四世同堂", "老舍", "华语经典", "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600", "小羊圈胡同的悲欢离合，记录了抗战时期北平底层百姓的风骨与苦难。"),
                BookCoverPreset("西西弗神话", "加缪", "哲学思辨", "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?w=600", "登上顶峰的斗争足以充实一个人的心灵，我们应当想象西西弗是幸福的。"),
                BookCoverPreset("挪威的森林", "村上春树", "日本文学", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", "每个人都有属于自己的一片森林，迷失的人迷失了，相逢的人会再相逢。"),
                BookCoverPreset("老人与海", "海明威", "欧美文学", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600", "人不是生来要给打败的，一个人可以被毁灭，但不能被打败。"),
                BookCoverPreset("上帝掷骰子吗", "曹天元", "硬核科普", "https://images.unsplash.com/photo-1507413245164-6160d8298b31?w=600", "这是一部波澜壮阔的量子力学史，带你走进人类认知最神秘的微观微境。"),
                BookCoverPreset("全员嫌疑人", "大山诚一郎", "密室推理", "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=600", "快节奏的逻辑推演，当所有人都被怀疑时，唯有华丽的诡计才能破解真相。"),
                BookCoverPreset("绝叫", "叶真中显", "社会派推理", "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=600", "从普通家庭女性滑向无底深渊，一部令人窒息的平成时代生存绝叫。"),
                BookCoverPreset("人类简史", "尤瓦尔·赫拉利", "历史哲学", "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=600", "认知革命、农业革命与科学革命，虚构故事的能力让人类成为了地球的主宰。"),
                BookCoverPreset("时间简史", "霍金", "宇宙科普", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600", "探寻黑洞、奇点与时间箭头的终极奥秘，仰望星空的最璀璨思想。"),
                BookCoverPreset("经济学原理", "曼昆", "经济社会", "https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=600", "人们面临权衡取舍，某种东西的成本就是为了得到它所放弃的东西。"),
                BookCoverPreset("廊桥遗梦", "沃勒", "情感文学", "https://images.unsplash.com/photo-1518895949257-7621c3c786d7?w=600", "这样确切的爱，一生只有一次。麦迪逊桥头的四天，刻骨铭心的一生。"),
                BookCoverPreset("社会心理学", "迈尔斯", "心理学", "https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=600", "探索群体对个体的影响、态度与从众、偏见与利他，看透人际关系的本质。"),
                BookCoverPreset("在细雨中呼喊", "余华", "华语经典", "https://images.unsplash.com/photo-1534274988757-a28bf1a57c17?w=600", "在江南细雨中回望童年与成长的创伤，记忆像水草一样在时间的河流里摇曳。"),
                BookCoverPreset("白夜行", "东野圭吾", "推理悬疑", "https://images.unsplash.com/photo-1516339901601-2e1b62dc0c45?w=600", "我的天空里没有太阳，总是黑夜，但并不暗，因为有东西代替了太阳。"),
                BookCoverPreset("局外人", "加缪", "存在主义", "https://images.unsplash.com/photo-1507842229451-79b1be8868c2?w=600", "今天，妈妈死了。也许是昨天，我不知道。对这荒谬世界的清醒抗争。"),
                BookCoverPreset("许三观卖血记", "余华", "华语经典", "https://images.unsplash.com/photo-1509021436665-8f07dbf5bf1d?w=600", "一盘炒猪肝，二两黄酒，用身体的鲜血托起一个家庭所有风雨的坚韧史诗。"),
                BookCoverPreset("解忧杂货店", "东野圭吾", "温暖治愈", "https://images.unsplash.com/photo-1513836279014-a89f7a76ae86?w=600", "如果把你的地图比作白纸，正因为是一张白纸，才可以随心所欲地描绘地图。"),
                BookCoverPreset("无人生还", "阿加莎·克里斯蒂", "孤岛推理", "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600", "十个印第安小男孩，孤岛之上的审判与童谣谋杀，本格推理的巅峰神作。"),
                BookCoverPreset("蛇结", "莫里亚克", "法国文学", "https://images.unsplash.com/photo-1528722828814-77b9b83aafb2?w=600", "人心如同纠缠在一起的蛇结，唯有爱与宽恕才能解开这深重的仇恨。"),
                BookCoverPreset("我是猫", "夏目漱石", "日本文学", "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600", "我是猫，还没有名字。以一只猫的独特视角，辛辣审视明治时代文人与社会的百态。"),
                BookCoverPreset("罗生门", "芥川龙之介", "日本文学", "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=600", "在暮色昏暗的罗生门下，每个人都在以谎言掩盖自己丑陋的利己本能。"),
                BookCoverPreset("活着", "余华", "华语经典", "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=600", "人是为活着本身而活着的，而不是为了活着之外的任何事物所活着。"),
                BookCoverPreset("月亮与六便士", "毛姆", "欧美文学", "https://images.unsplash.com/photo-1532693322450-2cb5c511067d?w=600", "满地都是六便士，他却抬头看见了月亮。为了艺术狂热抛弃一切的追寻。"),
                BookCoverPreset("罪与罚", "陀思妥耶夫斯基", "俄苏文学", "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600", "从高傲的超人理论到灵魂的受难救赎，人类心灵最深处的激烈交战。"),
                BookCoverPreset("消失的十三级台阶", "高野和明", "社会派推理", "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600", "踏上死刑台阶前的生死追凶，直击死刑制度与人性救赎的最震撼思索。"),
                BookCoverPreset("同名同姓受害者协会", "下村敦史", "社会悬疑", "https://images.unsplash.com/photo-1476275466078-4007374efbbe?w=600", "同名同姓带来的网络暴力与偏见审判，一场直击现代互联网生态的悬疑悲剧。"),
                BookCoverPreset("呼啸山庄", "艾米莉·勃朗特", "古典名著", "https://images.unsplash.com/photo-1505765050516-f72dcac9c60e?w=600", "荒原上的狂野爱恨，希斯克利夫超越生死与坟墓的终极执念。"),
                BookCoverPreset("蛤蟆先生去看心理医生", "罗伯特·戴博德", "心理成长", "https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=600", "探索儿童自我状态、父母自我状态与成人自我状态，找回属于自己的力量。"),
                BookCoverPreset("帷幕", "阿加莎·克里斯蒂", "古典推理", "https://images.unsplash.com/photo-1499209974431-9dac3ada0047?w=600", "大侦探波洛的谢幕之战，以生命为代价完成对完美罪犯的终极审判。"),
                BookCoverPreset("一个叫欧维的男人决定去死", "巴克曼", "温情治愈", "https://images.unsplash.com/photo-1499209974431-9dac3ada0047?w=600", "一个固执刻板的孤独老头，被一群吵闹的邻居拯救并重新爱上人间的温情旅程。"),
                BookCoverPreset("傲慢与偏见", "简·奥斯汀", "古典名著", "https://images.unsplash.com/photo-1474552226712-ac0f0961a954?w=600", "傲慢让别人无法来爱我，偏见让我无法去爱别人。达西与伊丽莎白的真爱和解。"),
                BookCoverPreset("恶意", "东野圭吾", "心理推理", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", "就算赌上我这一生，我也要将你拉入无间地狱。毫无由来的深渊恶意。"),
                BookCoverPreset("边城", "沈从文", "华语经典", "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=600", "这个人也许永远不回来了，也许‘明天’回来！湘西茶峒的纯美风土与凄美爱恋。"),
                BookCoverPreset("斜阳", "太宰治", "日本文学", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600", "在没落贵族的残照中，向旧道德决裂，用生命的微光进行最后的革命。"),
                BookCoverPreset("基督山伯爵", "大仲马", "传奇冒险", "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600", "人类的一切智慧都包含在这四个字里面：‘等待’和‘希望’。快意恩仇的传奇史诗。"),
                BookCoverPreset("人间失格", "太宰治", "日本文学", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", "生而为人，我很抱歉。叶藏以小丑的面具应付人间，在脆弱中走向毁灭。"),
                BookCoverPreset("ABC谋杀案", "阿加莎·克里斯蒂", "本格推理", "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=600", "按照字母表顺序展开的连环谋杀，波洛用灰色脑细胞破解最巧妙的掩饰诡计。"),
                BookCoverPreset("春雪", "三岛由纪夫", "丰饶之海", "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=600", "清显与聪子之间凄美禁忌的恋情，大正时代绚烂优雅又易碎的贵族挽歌。"),
                BookCoverPreset("追风筝的人", "胡赛尼", "成长救赎", "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?w=600", "为你，千千万万遍。在喀布尔蓝天下的风筝与漫长一生的罪咎救赎。"),
                BookCoverPreset("战争与和平", "列夫·托尔斯泰", "俄苏文学", "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=600", "俄罗斯辽阔大地上五大家族的命运交织，人类历史长河与个人意志的史诗巨著。"),
                BookCoverPreset("生死疲劳", "莫言", "魔幻现实", "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=600", "西门闹经历六道轮回转世为驴、牛、猪、狗、猴，见证半个世纪中国乡土风云变幻。"),
                BookCoverPreset("谋杀启事", "阿加莎·克里斯蒂", "古典推理", "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600", "报纸上赫然刊登的谋杀预告游戏，马普尔小姐在宁静乡村中洞察人性隐秘。"),
                BookCoverPreset("雾都孤儿", "狄更斯", "英国文学", "https://images.unsplash.com/photo-1463320726281-696a485928c7?w=600", "在十九世纪伦敦的阴暗底层与贼窝里，纯真少年奥利弗对善良与尊严的执着追求。"),
                BookCoverPreset("人间椅子", "江户川乱步", "猎奇推理", "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=600", "藏身于扶手椅中的制椅匠，在触觉与暗处的窥视中谱写惊悚奇异的幻觉物语。"),
                BookCoverPreset("雪国", "川端康成", "新感觉派", "https://images.unsplash.com/photo-1517824806704-9040b037703b?w=600", "穿过县界长长的隧道，便是雪国。夜空下一片白茫茫，驹子与岛村徒劳而纯粹的凄美物语。"),
                BookCoverPreset("伊豆的舞女", "川端康成", "新感觉派", "https://images.unsplash.com/photo-1528164344705-475426879c0d?w=600", "少年的孤独与伊豆山道上舞女熏子的纯真目光，洗净了青春所有阴翳的清冽散文诗。"),
            )

            val now = currentTimestamp()
            for (p in presetList) {
                val cursor = db.query(TABLE_BOOKS, arrayOf(COLUMN_ID, COLUMN_COVER_URL), "$COLUMN_TITLE LIKE ? AND $COLUMN_IS_DELETED = 0", arrayOf("%${p.title}%"), null, null, null)
                val (existingId, existingCover) = cursor.use {
                    if (it.moveToFirst()) Pair(it.getLong(0), it.getString(1)) else Pair(null, null)
                }

                if (existingId != null) {
                    if (existingCover.isNullOrBlank()) {
                        val cv = ContentValues().apply {
                            put(COLUMN_COVER_URL, p.coverUrl)
                            put(COLUMN_CATEGORY, p.category)
                            put(COLUMN_UPDATED_AT, now)
                        }
                        db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(existingId.toString()))
                    }
                } else {
                    val cv = ContentValues().apply {
                        put(COLUMN_TITLE, p.title)
                        put(COLUMN_AUTHOR, p.author)
                        put(COLUMN_CATEGORY, p.category)
                        put(COLUMN_STATUS, "wishlist")
                        put(COLUMN_MEDIA_TYPE, "book")
                        put(COLUMN_COVER_URL, p.coverUrl)
                        put(COLUMN_SHORT_COMMENT, p.shortComment)
                        put(COLUMN_REVIEW, "经典文学名著 · 收录于《阅痕》书单")
                        put(COLUMN_TAGS, JSONArray(listOf(p.category, "文学名著", "经典")).toString())
                        put(COLUMN_CREATED_AT, now)
                        put(COLUMN_UPDATED_AT, now)
                        put(COLUMN_IS_DELETED, 0)
                    }
                    db.insert(TABLE_BOOKS, null, cv)
                }
            }
        }
    }

    private fun autoFillMissingCovers(db: SQLiteDatabase) {
        runCatching {
            val defaultCoverMap = mapOf(
                // 经典文学 / 书籍
                "1984" to "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=600",
                "鼠疫" to "https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=600",
                "动物农场" to "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600",
                "诡计博物馆" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                "霍乱时期的爱情" to "https://images.unsplash.com/photo-1474932430478-367dbb6832c1?w=600",
                "悲惨世界" to "https://images.unsplash.com/photo-1463320726281-696a485928c7?w=600",
                "四世同堂" to "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600",
                "西西弗神话" to "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?w=600",
                "挪威的森林" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "老人与海" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                "上帝掷骰子吗" to "https://images.unsplash.com/photo-1507413245164-6160d8298b31?w=600",
                "全员嫌疑人" to "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=600",
                "绝叫" to "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=600",
                "人类简史" to "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=600",
                "时间简史" to "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                "经济学原理" to "https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=600",
                "廊桥遗梦" to "https://images.unsplash.com/photo-1518895949257-7621c3c786d7?w=600",
                "社会心理学" to "https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=600",
                "在细雨中呼喊" to "https://images.unsplash.com/photo-1534274988757-a28bf1a57c17?w=600",
                "白夜行" to "https://images.unsplash.com/photo-1516339901601-2e1b62dc0c45?w=600",
                "局外人" to "https://images.unsplash.com/photo-1507842229451-79b1be8868c2?w=600",
                "许三观卖血记" to "https://images.unsplash.com/photo-1509021436665-8f07dbf5bf1d?w=600",
                "解忧杂货店" to "https://images.unsplash.com/photo-1513836279014-a89f7a76ae86?w=600",
                "无人生还" to "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600",
                "蛇结" to "https://images.unsplash.com/photo-1528722828814-77b9b83aafb2?w=600",
                "我是猫" to "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600",
                "罗生门" to "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=600",
                "活着" to "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=600",
                "月亮与六便士" to "https://images.unsplash.com/photo-1532693322450-2cb5c511067d?w=600",
                "罪与罚" to "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600",
                "消失的十三级台阶" to "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600",
                "同名同姓受害者协会" to "https://images.unsplash.com/photo-1476275466078-4007374efbbe?w=600",
                "呼啸山庄" to "https://images.unsplash.com/photo-1505765050516-f72dcac9c60e?w=600",
                "蛤蟆先生去看心理医生" to "https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=600",
                "帷幕" to "https://images.unsplash.com/photo-1499209974431-9dac3ada0047?w=600",
                "一个叫欧维的男人决定去死" to "https://images.unsplash.com/photo-1499209974431-9dac3ada0047?w=600",
                "傲慢与偏见" to "https://images.unsplash.com/photo-1474552226712-ac0f0961a954?w=600",
                "恶意" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                "边城" to "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=600",
                "斜阳" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                "基督山伯爵" to "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                "人间失格" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "ABC谋杀案" to "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=600",
                "春雪" to "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=600",
                "追风筝的人" to "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?w=600",
                "战争与和平" to "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=600",
                "生死疲劳" to "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=600",
                "谋杀启事" to "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600",
                "雾都孤儿" to "https://images.unsplash.com/photo-1463320726281-696a485928c7?w=600",
                "人间椅子" to "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=600",
                "雪国" to "https://images.unsplash.com/photo-1517824806704-9040b037703b?w=600",
                "伊豆的舞女" to "https://images.unsplash.com/photo-1528164344705-475426879c0d?w=600",

                // 经典番剧
                "EVA" to "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                "新世纪福音战士" to "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                "浪客剑心" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "夏目友人帐" to "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                "轻音少女" to "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600",
                "怪盗基德" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                "魔术快斗" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                "Angel Beats" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "JOJO" to "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
                "我的青春恋爱物语果然有问题" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                "约会大作战" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "月刊少女野崎君" to "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600",
                "Charlotte" to "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                "夏洛特" to "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                "路人女主" to "https://images.unsplash.com/photo-1513836279014-a89f7a76ae86?w=600",
                "齐木楠雄" to "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600",
                "在下坂本" to "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600",
                "灵能百分百" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                "来自深渊" to "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=600",
                "笨女孩" to "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=600",
                "实力至上主义教室" to "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=600",
                "青春猪头少年" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "紫罗兰永恒花园" to "https://images.unsplash.com/photo-1463320726281-696a485928c7?w=600",
                "碧蓝之海" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                "强风吹拂" to "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?w=600",
                "文豪野犬" to "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600",
                "魔女之旅" to "https://images.unsplash.com/photo-1528164344705-475426879c0d?w=600",
                "从零开始的异世界生活" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "国王排名" to "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                "转生成蜘蛛" to "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600",
                "间谍过家家" to "https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=600",
                "夏日重现" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                "孤独摇滚" to "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                "蓝色监狱" to "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600",
                "我独自升级" to "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
                "鬼灭之刃" to "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                "魔法少女小圆" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "咒术回战" to "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600",
                "魔都精兵" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "我推的孩子" to "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600",
                "玉子市场" to "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600",
                "超电磁炮" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                "凉宫春日" to "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                "中二病" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "冰菓" to "https://images.unsplash.com/photo-1517824806704-9040b037703b?w=600",
                "野良神" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                "点兔" to "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600",
                "请问您今天要来点兔子吗" to "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600",
                "小埋" to "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600",
                "逆转裁判" to "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=600",
                "杀戮天使" to "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=600",
                "多罗罗" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                "葬送的芙莉莲" to "https://images.unsplash.com/photo-1463320726281-696a485928c7?w=600",
                "超时空要塞" to "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                "辉夜大小姐" to "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600",
            )

            // 查询所有 cover_url 为空或 null 的作品
            val cursor = db.query(
                TABLE_BOOKS,
                arrayOf(COLUMN_ID, COLUMN_TITLE, COLUMN_MEDIA_TYPE),
                "($COLUMN_COVER_URL IS NULL OR $COLUMN_COVER_URL = '') AND $COLUMN_IS_DELETED = 0",
                null, null, null, null,
            )

            val missingList = mutableListOf<Triple<Long, String, String>>()
            cursor.use {
                while (it.moveToNext()) {
                    missingList.add(Triple(it.getLong(0), it.getString(1), it.getString(2) ?: "book"))
                }
            }

            val now = currentTimestamp()
            for ((id, title, mediaType) in missingList) {
                var matchedUrl: String? = null
                for ((key, url) in defaultCoverMap) {
                    if (title.contains(key, ignoreCase = true)) {
                        matchedUrl = url
                        break
                    }
                }
                if (matchedUrl == null) {
                    matchedUrl = when (mediaType) {
                        "anime" -> "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600"
                        "movie" -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600"
                        "game" -> "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600"
                        "podcast" -> "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=600"
                        else -> "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600"
                    }
                }

                val cv = ContentValues().apply {
                    put(COLUMN_COVER_URL, matchedUrl)
                    put(COLUMN_UPDATED_AT, now)
                }
                db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(id.toString()))
            }
        }
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
                        // yearTag 为占位文案（如「待整理」）时不写入日期字段，避免时间轴出现原始占位日期
                        val seedYear = anime.yearTag.takeIf { Regex("\\d{4}").matches(it) }
                        put(COLUMN_START_DATE, if (anime.status == "finished" && seedYear != null) "$seedYear-01-01" else null)
                        put(COLUMN_FINISH_DATE, if (anime.status == "finished" && seedYear != null) "$seedYear-12-31" else null)
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

    private fun seedUserMovieList(db: SQLiteDatabase) {
        runCatching {
            data class MovieEntry(
                val title: String,
                val author: String,
                val category: String,
                val status: String,
                val tags: List<String>,
                val rating: Double?,
                val shortComment: String?,
                val review: String?,
                val coverUrl: String,
                val mindprint: FloatArray? = null, // depth, art, emo, log, diff, heal
            )

            val movieList = listOf(
                MovieEntry(
                    title = "蜘蛛侠：崭新之日",
                    author = "漫威影业 · 索尼电影",
                    category = "超级英雄",
                    status = "finished",
                    tags = listOf("漫威影业", "超级英雄", "崭新之日", "街头英雄", "成长"),
                    rating = 4.8,
                    shortComment = "能力越大，责任越大。无论世界如何遗忘彼得·帕克，蜘蛛侠永远守护纽约的晨曦。",
                    review = "剥离了斯塔克工业高科技光环，彼得·帕克在简陋公寓中缝制新战衣，重拾街头英雄的坚韧与初心。",
                    coverUrl = "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=600",
                    mindprint = floatArrayOf(8.0f, 8.5f, 9.2f, 8.4f, 4.0f, 8.8f),
                ),
                MovieEntry(
                    title = "哪吒之魔童闹海",
                    author = "饺子 · 可可豆动画",
                    category = "神话国漫",
                    status = "finished",
                    tags = listOf("国漫神作", "神话史诗", "魔童降世续集", "逆天改命", "视觉震撼"),
                    rating = 4.9,
                    shortComment = "我命由我不由天，是魔是仙，我自己说了才算！四海龙族受死！",
                    review = "国产动画电影巅峰巨制。哪吒与敖丙肉身虽灭但魂魄尚存，重塑肉身与四海龙王掀起撼天动地的终极决战。",
                    coverUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
                    mindprint = floatArrayOf(8.8f, 9.2f, 9.6f, 8.6f, 5.0f, 8.8f),
                ),
                MovieEntry(
                    title = "肖申克的救赎",
                    author = "弗兰克·德拉邦特",
                    category = "剧情经典",
                    status = "finished",
                    tags = listOf("影史第一", "自由意志", "希望救赎", "经典神作", "人性史诗"),
                    rating = 5.0,
                    shortComment = "有些鸟儿是关不住的，它们的每一片羽毛都闪耀着自由的光辉。希望是件好东西，也许是最好的东西。",
                    review = "影史无可争议的无冕之王。安迪用一把小石锤在十九年里凿开肖申克监狱的高墙，暴雨中拥抱自由的瞬间成为人类电影史的永恒丰碑。",
                    coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                    mindprint = floatArrayOf(10.0f, 9.8f, 9.8f, 9.7f, 6.0f, 9.6f),
                ),
                MovieEntry(
                    title = "哈尔的移动城堡",
                    author = "宫崎骏 · 吉卜力工作室",
                    category = "奇幻治愈",
                    status = "finished",
                    tags = listOf("宫崎骏", "吉卜力", "浪漫奇幻", "反战治愈", "童话史诗"),
                    rating = 5.0,
                    shortComment = "在茫茫人海中相遇，我已经找了你很久很久。世界这么大，人生这么长，总会有一个人，让你想要温柔对待。",
                    review = "宫崎骏最唯美浪漫的心灵寓言。即使外表衰老如风烛残年，真挚勇敢的心灵也能让沉重钢铁城堡翱翔于澄澈星空与花海。",
                    coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                    mindprint = floatArrayOf(9.0f, 10.0f, 10.0f, 8.5f, 4.0f, 10.0f),
                ),
                MovieEntry(
                    title = "星际穿越",
                    author = "克里斯托弗·诺兰",
                    category = "硬核科幻",
                    status = "finished",
                    tags = listOf("诺兰神作", "硬核科幻", "黑洞时空", "父女深情", "五维空间"),
                    rating = 5.0,
                    shortComment = "不要温和地走进那个良夜。爱是唯一可以超越时间与空间维度的力量。",
                    review = "硬核相对论物理与极致父女亲情的壮丽交响。穿越五维超正方体拨动书架手表的秒针，浩瀚宇宙在人类的情感面前亦化作回音。",
                    coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                    mindprint = floatArrayOf(9.8f, 9.6f, 10.0f, 9.8f, 7.5f, 9.0f),
                ),
                MovieEntry(
                    title = "盗梦空间",
                    author = "克里斯托弗·诺兰",
                    category = "悬疑科幻",
                    status = "finished",
                    tags = listOf("诺兰神作", "潜意识", "梦境架构", "极致烧脑", "哲学悬疑"),
                    rating = 5.0,
                    shortComment = "最坚韧的寄生虫是什么？是想法。一个想法可以筑起城市，也可以改变世界。图腾旋转不息，但我们已回到真实。",
                    review = "多层梦境嵌套与时间差叙事的结构奇迹。旋转的陀螺成为了整个电影史最迷人的哲学隐喻。",
                    coverUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                    mindprint = floatArrayOf(9.6f, 9.5f, 9.0f, 10.0f, 8.0f, 7.5f),
                ),
                MovieEntry(
                    title = "疯狂动物城",
                    author = "拜伦·霍华德 · 迪士尼",
                    category = "动画喜剧",
                    status = "finished",
                    tags = listOf("迪士尼", "乌托邦", "爆笑治愈", "打破偏见", "狐兔CP"),
                    rating = 4.9,
                    shortComment = "生活总会有点不顺心，但无论你是何种动物，改变都从你开始。Try Everything!",
                    review = "迪士尼兼具极致娱乐性与深刻社会多元包容思辨的现代经典。兔朱迪与狐尼克的乌托邦冒险充满灵动与温暖。",
                    coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                    mindprint = floatArrayOf(9.2f, 9.4f, 9.4f, 9.0f, 4.0f, 9.6f),
                ),
                MovieEntry(
                    title = "教父",
                    author = "弗朗西斯·福特·科波拉",
                    category = "黑帮史诗",
                    status = "finished",
                    tags = listOf("影史巅峰", "黑帮史诗", "权力圣经", "柯里昂家族", "教父"),
                    rating = 5.0,
                    shortComment = "伟大的人不是生来就伟大的，而是在成长过程中展现其伟大的。永远不要让别人知道你在想什么。",
                    review = "男人的圣经，电影美学的教科书。柯里昂家族在光影暗调中的沉浮与决断，构筑了人类权力与家庭责任的最冷峻赞歌。",
                    coverUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600",
                    mindprint = floatArrayOf(9.8f, 9.8f, 9.2f, 9.6f, 6.5f, 6.5f),
                ),
                MovieEntry(
                    title = "功夫",
                    author = "周星驰",
                    category = "武侠动作",
                    status = "finished",
                    tags = listOf("周星驰", "武侠巅峰", "动作喜剧", "童年梦想", "如来神掌"),
                    rating = 4.9,
                    shortComment = "想学啊？我教你啊。一曲肝肠断，天涯何处觅知音。",
                    review = "周星驰无厘头与传统武侠浪漫美学的集大成之作。从猪笼城寨的市井烟火到如来神掌化作彩蝶，充满了小人物对纯真童梦的守候。",
                    coverUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=600",
                    mindprint = floatArrayOf(8.8f, 9.4f, 9.2f, 8.6f, 4.0f, 9.5f),
                ),
                MovieEntry(
                    title = "新世纪福音战士新剧场版：终",
                    author = "庵野秀明 · Khara",
                    category = "科幻哲学",
                    status = "finished",
                    tags = listOf("EVA终章", "神作电影", "庵野秀明", "告别EVA", "哲学心智"),
                    rating = 5.0,
                    shortComment = "不能逃避，面对人与人之间的AT力场，向所有的福音战士告别，再见所有的Evangelion。",
                    review = "跨越四分之一个世纪的青春终章。庵野秀明用最真诚的成年人笔触，打破了虚幻的避难所，教我们走出忧郁，拥抱真实的人间与现实世界。",
                    coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                    mindprint = floatArrayOf(9.9f, 9.6f, 9.8f, 8.5f, 6.0f, 9.5f),
                ),
                MovieEntry(
                    title = "给阿嘛的情书",
                    author = "闽南纪实影音",
                    category = "温情纪录",
                    status = "finished",
                    tags = listOf("亲情纪录", "闽南古厝", "阿嘛的爱", "岁月温情", "人间烟火"),
                    rating = 4.9,
                    shortComment = "阿嘛留下的不仅是摇椅与古厝的风，更是流淌在血脉里永远不会褪色的温暖记忆。",
                    review = "真挚动人的代际亲情与乡土记忆。用温柔细腻的镜头记录祖辈的坚韧与慈爱，勾起无数人内心最柔软的归宿感与故土乡愁。",
                    coverUrl = "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600",
                    mindprint = floatArrayOf(9.0f, 9.5f, 10.0f, 8.6f, 3.5f, 10.0f),
                ),
            )

            val now = currentTimestamp()

            movieList.forEach { movie ->
                val cursor = db.query(
                    TABLE_BOOKS,
                    arrayOf(COLUMN_ID, COLUMN_COVER_URL, COLUMN_SHORT_COMMENT),
                    "$COLUMN_TITLE = ? AND $COLUMN_IS_DELETED = 0",
                    arrayOf(movie.title),
                    null,
                    null,
                    null,
                )

                var bookId: Long = -1
                val exists = cursor.use { c ->
                    if (c.moveToFirst()) {
                        bookId = c.getLong(0)
                        true
                    } else false
                }

                if (exists) {
                    val cv = ContentValues().apply {
                        put(COLUMN_MEDIA_TYPE, "movie")
                        if (movie.shortComment != null) put(COLUMN_SHORT_COMMENT, movie.shortComment)
                        if (movie.review != null) put(COLUMN_REVIEW, movie.review)
                        if (movie.rating != null) put(COLUMN_RATING, movie.rating)
                        if (movie.coverUrl.isNotBlank()) put(COLUMN_COVER_URL, movie.coverUrl)
                        put(COLUMN_TAGS, JSONArray(movie.tags).toString())
                    }
                    db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(bookId.toString()))
                } else {
                    val cv = ContentValues().apply {
                        put(COLUMN_TITLE, movie.title)
                        put(COLUMN_AUTHOR, movie.author)
                        put(COLUMN_CATEGORY, movie.category)
                        put(COLUMN_STATUS, movie.status)
                        put(COLUMN_MEDIA_TYPE, "movie")
                        put(COLUMN_SHORT_COMMENT, movie.shortComment)
                        put(COLUMN_REVIEW, movie.review)
                        put(COLUMN_RATING, movie.rating ?: 5.0)
                        put(COLUMN_TAGS, JSONArray(movie.tags).toString())
                        put(COLUMN_COVER_URL, movie.coverUrl)
                        put(COLUMN_START_DATE, "2026-07-01")
                        put(COLUMN_FINISH_DATE, "2026-07-15")
                        put(COLUMN_BUY_CHANNEL, "院线公映 · 影院观影")
                        put(COLUMN_SHELF_LOCATION, "展厅第4层 · 影音光影展区")
                        put(COLUMN_BINDING_TYPE, "IMAX / 杜比影院")
                        put(COLUMN_CREATED_AT, now)
                        put(COLUMN_UPDATED_AT, now)
                        put(COLUMN_IS_DELETED, 0)
                    }
                    bookId = db.insert(TABLE_BOOKS, null, cv)
                }

                // 注入六维心智雷达
                if (movie.mindprint != null && bookId > 0) {
                    val mp = movie.mindprint
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

    private fun seedUserGameList(db: SQLiteDatabase) {
        runCatching {
            data class GameEntry(
                val title: String,
                val author: String,
                val category: String,
                val status: String,
                val tags: List<String>,
                val rating: Double?,
                val shortComment: String?,
                val review: String?,
                val coverUrl: String,
                val mindprint: FloatArray? = null, // depth, art, emo, log, diff, heal
            )

            val gameList = listOf(
                // 1. 魂系与硬核动作神作
                GameEntry("艾尔登法环", "FromSoftware · 宫崎英高 / 乔治·马丁", "魂系神作", "finished", listOf("年度最佳", "魂系开放世界", "交界地", "宫崎英高", "神作"), 5.0, "落叶跳费，黄金树将庇护你我。愿引导之光与你同在，褪色者啊，成为艾尔登之王吧！", "开放世界箱庭设计的最高巅峰。从宁姆格福的晨光到黄金树脚下的律动，交界地的每一寸土地都回荡着神话破灭的史诗挽歌。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(9.8f, 10.0f, 9.5f, 9.2f, 9.8f, 6.0f)),
                GameEntry("黑神话：悟空", "游戏科学 Game Science · 冯骥 / 杨奇", "动作角色扮演", "finished", listOf("国产3A丰碑", "东方神话", "西游后传", "动作RPG", "大圣归来"), 5.0, "踏过三界宝刹，阅尽九洲繁华。若不披上这身袈裟，唯恐负了这世间苍生。", "国产 3A 游戏的划时代里程碑。顶级的东方魔幻美术、深邃的西游后传叙事与酣畅淋漓的棍法变身，圆了无数国人的大圣之梦。", "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600", floatArrayOf(9.6f, 10.0f, 9.8f, 9.0f, 9.2f, 8.0f)),
                GameEntry("只狼：影逝二度", "FromSoftware · 宫崎英高", "硬核动作", "finished", listOf("年度最佳", "拼刀打铁", "苇名弦一郎", "硬核动作", "断绝不死"), 5.0, "犹豫就会败北！苇名一心。忍者的宿命，唯有侍奉唯一之主。", "冷兵器打铁音律的极致快感。弹刀见招拆招的压迫感与唯美苍凉的苇名古国风貌，将武士道与不死宿命展现得淋漓尽致。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.2f, 9.6f, 9.2f, 9.5f, 10.0f, 6.5f)),
                GameEntry("DARK SOULS™ III", "FromSoftware · 宫崎英高", "魂系神作", "finished", listOf("黑魂3", "传火时代", "无火余灰", "薪王们", "魂系巅峰"), 5.0, "灰烬大人，愿初火将您引向终焉。传火的漫长时代在此落幕。", "黑魂三部曲的终极史诗篇章。传火祭祀场中回荡的管风琴声与洛斯里克高墙，将不死人的宿命升华为永恒赞歌。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(9.6f, 9.8f, 9.5f, 9.2f, 9.8f, 6.0f)),
                GameEntry("DARK SOULS™: REMASTERED", "FromSoftware", "魂系始祖", "finished", listOf("魂系鼻祖", "赞美太阳", "罗德兰", "传火祭祀场"), 5.0, "赞美太阳！罗德兰古国的立体地图设计，魂系梦开始的地方。", "传火神话的开山之作。无缝垂直立体的罗德兰地图设计被奉为游戏关卡设计的无上教科书。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.8f, 9.6f, 9.2f, 9.8f, 9.8f, 5.5f)),
                GameEntry("DARK SOULS™ II: Scholar of the First Sin", "FromSoftware", "魂系经典", "finished", listOf("原罪学者", "多兰古雷格", "安迪尔", "黑魂2"), 4.8, "超越光明，超越黑暗。在受诅咒的命运之外，找寻全新的归途。", "多兰古雷格王国的宏大悲剧，探讨人性诅咒与原罪深度的厚重史诗。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(9.4f, 9.2f, 9.0f, 9.2f, 9.6f, 5.5f)),
                GameEntry("Devil May Cry 5", "CAPCOM · 伊津野英昭", "硬核动作", "finished", listOf("动作天花板", "CAPCOM", "SSS连招", "阎魔刀", "华丽摇滚"), 4.9, "抛弃所有平庸，以 SSS 级的华丽姿态撕碎恶魔！但丁、尼禄与维吉尔的宿命合奏。", "动作游戏（ACT）领域的皇冠明珠。顶尖的 RE 引擎画面、深度极高的连招判定与暴风骤雨般的摇滚战斗。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(8.5f, 9.5f, 9.0f, 9.2f, 8.5f, 9.0f)),
                GameEntry("剑星", "SHIFT UP · 金亨泰", "动作冒险", "finished", listOf("剑星", "SHIFT UP", "伊芙", "硬核动作", "末世地球"), 4.8, "伊芙降临荒废地球，以最优雅凌厉的剑技驱散孽奇拔的阴霾，拯救人类残存火种。", "兼具顶尖动作打击反馈与华丽韩系美学的 3A 动作新作。极度爽快的完美招架与处决演出。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(8.5f, 9.6f, 9.0f, 8.8f, 7.5f, 8.5f)),
                GameEntry("明末：渊虚之羽", "灵泽科技", "国风魂系动作", "wishlist", listOf("明末蜀地", "古蜀文明", "国风魂系", "三星堆", "动作RPG"), 4.8, "明末乱世，羽化怪病肆虐蜀地。无常少女拔剑斩妖，探寻古蜀文明与宿命秘辛！", "备受期待的明末古蜀题材国风魂系动作大作。将三星堆、金沙古蜀文明与克苏鲁式羽化异变完美交融。", "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600", floatArrayOf(9.2f, 9.6f, 9.0f, 9.0f, 9.0f, 7.0f)),

                // 2. JRPG / 殿堂叙事与心之物语
                GameEntry("女神异闻录5皇家版", "ATLUS · P-Studio", "日系角色扮演", "finished", listOf("P5天下第一", "JRPG巅峰", "心之怪盗团", "潮酷美学", "神作"), 5.0, "夺取那些腐朽大人的扭曲欲望，在这个无可救药的世界中贯彻属于心之怪盗团的正义！Take Your Heart!", "天下第一的 JRPG 殿堂神作。无与伦比的潮爆 UI 视觉语言、酸爵士配乐与白天高中生活、夜晚怪盗夺心双重人生的完美融合。", "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600", floatArrayOf(9.5f, 10.0f, 9.8f, 9.2f, 6.0f, 9.6f)),
                GameEntry("女神异闻录3 Reload", "ATLUS · P-Studio", "日系角色扮演", "finished", listOf("P3R", "铭记死亡", "影时间", "ATLUS", "神作重制"), 4.9, "Memento Mori（铭记死亡）。在影时间中直面终焉与救赎，化作永恒的迎春花。", "P3 系列的终极完全重制。深邃的生死哲学命题、鞑靼罗斯之塔探索与经典人格面具召唤，催人泪下的灵魂篇章。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.8f, 9.6f, 10.0f, 9.0f, 6.5f, 8.5f)),
                GameEntry("女神异闻录4 黄金版", "ATLUS · P-Studio", "日系角色扮演", "finished", listOf("P4G", "八十稻羽", "深夜电视", "真理追寻", "黄金青春"), 4.9, "拨开深夜电视的迷雾，追寻无可取代的羁绊与唯一的真实。", "充满稻羽市乡村阳光与青春回忆的侦探物语。自称特别搜查队的伙伴们在电视世界中直面真实自我的温暖传奇。", "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600", floatArrayOf(9.2f, 9.4f, 9.8f, 9.0f, 5.5f, 9.8f)),
                GameEntry("暗喻幻想：ReFantazio", "ATLUS · Studio Zero · 桥野桂", "日系角色扮演", "finished", listOf("桥野桂", "ATLUS", "选举之王", "年度RPG", "幻想史诗"), 5.0, "打破血统与种族的枷锁，在没有偏见的幻想世界中，为人民加冕为王！", "桥野桂、副岛成记、目黑将司铁三角打造的全新幻想 RPG 巅峰。探讨焦虑与乌托邦政治选举的哲学巨作。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.6f, 9.8f, 9.5f, 9.4f, 6.5f, 9.0f)),
                GameEntry("神之天平（ASTLIBRA Revision）", "KEIZO · DX Library", "横版动作RPG", "finished", listOf("神作", "神级剧本", "一人十四年", "时间跨越", "热血反转"), 5.0, "跨越十四年的时光执念，与乌鸦嘉隆一同拨动命运的天平，向神明挥剑！", "一人独立开发十四年的传世独立神作。无可挑剔的剧本层层反转、极度爽快的横版刷宝战斗与宏大的宿命交响。", "https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=600", floatArrayOf(9.8f, 9.2f, 10.0f, 9.6f, 7.5f, 9.2f)),
                GameEntry("Granblue Fantasy: Relink", "Cygames", "日系动作共斗", "finished", listOf("骑空团", "Cygames", "奥义连锁", "日系共斗", "王道冒险"), 4.8, "向着星之岛伊斯塔鲁西亚扬帆！骑空团四人连携奥义，在云海之上斩落巨兽！", "Cygames 匠心打磨的日式动作共斗佳作。极具冲击力的奥义连锁 Chain Burst 与王道冒险物语。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(8.5f, 9.6f, 9.4f, 8.8f, 6.5f, 9.0f)),
                GameEntry("莱莎的炼金工房 ～常暗女王与秘密藏身处～", "GUST · 光荣特库摩", "日系炼金RPG", "finished", listOf("莱莎", "炼金工房", "夏日冒险", "治愈日常"), 4.8, "库肯岛的夏日微风与炼金大锅，属于平凡少女不可思议的盛夏冒险！", "炼金工房系列最畅销之作。充满乡村夏日惬意风情的探索采集与合成系统。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(8.2f, 9.2f, 9.2f, 9.0f, 4.0f, 9.8f)),

                // 3. 肉鸽卡牌与独立神作
                GameEntry("杀戮尖塔", "Mega Crit Games", "肉鸽卡牌", "finished", listOf("肉鸽神作", "卡牌构筑", "DBG鼻祖", "策略巅峰", "无尽爬塔"), 5.0, "高塔的心脏仍在跳动，一次又一次构筑你的套牌向顶端发起冲击！", "DBG（卡组构筑）肉鸽游戏的现代鼻祖与黄金标准。四种机制迥异的职业与近乎完美的数值平衡，造就了令人欲罢不能的爬塔体验。", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600", floatArrayOf(8.5f, 8.8f, 8.5f, 10.0f, 8.8f, 7.5f)),
                GameEntry("杀戮尖塔 2", "Mega Crit Games", "肉鸽卡牌", "wishlist", listOf("尖塔续作", "肉鸽期待", "卡牌神作", "死灵术士"), 5.0, "千年之后，高塔再次开启。死灵法师与全新遗物，迎接全新的爬塔传奇！", "万众期待的爬塔正统续作。全新引擎打造，引入全新职业死灵术士、剧毒机制与跨时代卡牌联动。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(8.8f, 9.0f, 8.8f, 10.0f, 9.0f, 8.0f)),
                GameEntry("Hades", "Supergiant Games", "动作肉鸽", "finished", listOf("动作肉鸽巅峰", "希腊神话", "扎格列欧斯", "Supergiant"), 5.0, "在冥界深渊中一次又一次逃脱，哪怕死亡也无法阻止寻找母亲的脚步！", "将希腊神话群像剧本与动作肉鸽爽快打击感无缝融合的典范神作。每一次死亡都会推进全新剧情对话。", "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600", floatArrayOf(9.0f, 9.8f, 9.5f, 9.6f, 8.0f, 9.0f)),
                GameEntry("Dead Cells", "Motion Twin", "横版肉鸽动作", "finished", listOf("横版肉鸽", "爽快打击", "无头宿主", "皇室城堡"), 4.9, "菜就多练！翻滚、格挡、狂暴输出，在不断重生的牢房中杀出一条血路！", "打击感与动作流畅度首屈一指的横版银河恶魔城肉鸽。百种武器搭配与细胞升级体系极具深度。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(8.0f, 9.2f, 8.0f, 9.5f, 9.2f, 8.0f)),
                GameEntry("Hollow Knight", "Team Cherry", "银河恶魔城", "finished", listOf("银河恶魔城巅峰", "圣巢", "小骑士", "丝之歌", "神作"), 5.0, "没有可以思考的心智，没有可以屈服的意志。小骑士穿越圣巢废墟，封印终极瘟疫光芒。", "银河恶魔城类型的无上丰碑。精雕细琢的昆虫王国地下世界、手绘哥特暗黑美学与富有挑战性的 BOSS 战体验。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.6f, 10.0f, 9.5f, 9.5f, 9.5f, 7.0f)),
                GameEntry("Celeste", "Extremely OK Games", "平台跳跃神作", "finished", listOf("TGA最佳影响力", "硬核跳跃", "战胜抑郁", "神级配乐"), 5.0, "深呼吸，你可以战胜心中的恐慌与阴暗面。登上塞莱斯特山顶吧，玛德琳！", "关卡设计与心理叙事完美交融的神作。通过攀登险峻雪山的硬核跳跃，帮助无数抑郁与焦虑症患者战胜心魔。", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600", floatArrayOf(9.2f, 9.8f, 10.0f, 9.8f, 9.6f, 10.0f)),
                GameEntry("挺进地牢", "Dodge Roll", "弹幕射击肉鸽", "finished", listOf("弹幕肉鸽", "消灭过去", "翻滚无敌", "像素射击"), 4.9, "翻滚规避弹幕，消灭过去之枪！在无尽地牢中收集百种枪械狂欢！", "将弹幕射击与武器创意发挥到极致的像素肉鸽。翻滚无敌帧与掀桌掩体的快感。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(8.0f, 9.0f, 8.5f, 9.6f, 9.0f, 8.5f)),
                GameEntry("土豆兄弟(Brotato)", "Blobfish", "爽快割草肉鸽", "finished", listOf("割草神作", "六枪土豆", "上头解压", "构筑狂欢"), 4.8, "手持六把加特林，在疯狂外星虫潮中横扫千军！极致解压的五分钟爽快割草。", "极简画面下蕴含无尽策略深度的自走割草肉鸽神作。", "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600", floatArrayOf(7.5f, 8.5f, 8.0f, 9.8f, 7.5f, 9.5f)),
                GameEntry("苍翼：混沌效应", "91Act", "横版动作肉鸽", "finished", listOf("苍翼默示录", "丝滑动作", "赛博肉鸽", "横版连招"), 4.8, "潜入意识空间，在混沌霓虹中打出无缝连段与潜能质变！", "格斗游戏级丝滑手感的横版动作肉鸽。苍翼默示录经典角色与潜能继承系统。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(8.5f, 9.4f, 8.8f, 9.0f, 8.0f, 8.5f)),
                GameEntry("Library Of Ruina", "Project Moon", "卡牌策略RPG", "finished", listOf("Project Moon", "罗兰", "都市残酷", "卡牌策略"), 4.9, "愿你在此找到想要的书。在都市的残酷齿轮下，司书罗兰与安吉拉的舞台！", "独一无二的脑叶公司世界观衍生神作。极具深度的骰子卡牌拼点与宏大的都市悲喜剧。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.6f, 9.8f, 9.6f, 9.8f, 9.0f, 7.5f)),
                GameEntry("Limbus Company", "Project Moon", "回合制策略RPG", "finished", listOf("边狱公司", "罪人巴士", "但丁", "都市物语"), 4.8, "引导十二位罪人，穿梭于都市二十六个巢区，回收金枝！", "黑暗哥特风格的都市罪人救赎物语。硬核硬派的回合拼点与深度群像叙事。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.4f, 9.6f, 9.4f, 9.6f, 8.5f, 7.5f)),

                // 4. Freebird 系列与灵魂催泪物语
                GameEntry("To the Moon《去月球》", "Freebird Games · 高瞰", "叙事催泪神作", "finished", listOf("催泪天花板", "神级配乐", "去月球", "纯真爱恋", "心灵洗礼"), 5.0, "如果我迷路了，我们就在月亮上相见！折一只纸兔子，跨越记忆的长河奔向你。", "游戏艺术史上最纯粹的心灵震撼。用钢琴声和像素画面谱写了一曲关于阿斯伯格综合征、承诺与跨越生死的至高爱之诗。", "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600", floatArrayOf(9.5f, 10.0f, 10.0f, 8.8f, 2.0f, 10.0f)),
                GameEntry("Finding Paradise《寻找天堂》", "Freebird Games · 高瞰", "叙事催泪神作", "finished", listOf("寻找天堂", "Freebird", "自我和解", "催泪感动"), 5.0, "即便一生充满平淡与遗憾，我也曾拥有过属于自己的幻想之翼与真实人生。", "去月球正统续作。探讨与自我和解、童年幻想朋友菲耶与接受不完美人生的温柔篇章。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.4f, 9.8f, 10.0f, 8.6f, 2.0f, 10.0f)),
                GameEntry("Impostor Factory《影子工厂》", "Freebird Games · 高瞰", "悬疑叙事", "finished", listOf("影子工厂", "Freebird", "悬疑科幻", "时间轮回"), 4.8, "在暴雨古宅与量子蝴蝶翅膀之间，用一杯红茶倒映出宇宙全息生命的悲喜。", "Freebird 系列第三部曲。融入悬疑谋杀与科幻轮回，揭示记忆修改技术的起源与母爱的深邃奇迹。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.2f, 9.5f, 9.6f, 9.0f, 3.0f, 9.2f)),
                GameEntry("A Bird Story", "Freebird Games · 高瞰", "唯美治愈", "finished", listOf("无字短篇", "唯美治愈", "童真梦境", "温暖"), 4.8, "无字的长卷，一个小男孩与一只断翅小鸟的纯真童梦飞行。", "没有任何台词纯靠画面与音乐推动的唯美短篇。连接《去月球》与《寻找天堂》的心灵纽带。", "https://images.unsplash.com/photo-1474932430478-367dbb6832c1?w=600", floatArrayOf(8.5f, 9.5f, 9.5f, 7.5f, 1.5f, 10.0f)),
                GameEntry("ATRI -My Dear Moments-", "Frontwing · 枕社", "视觉小说", "finished", listOf("ATRI", "亚托莉", "沉没末日", "仿生少女", "催泪神作"), 4.9, "即便地表沉入海底，即便机能停止运转，我也要为你献上最后四十五天的璀璨微笑。", "末日与仿生少女相伴的心灵物语。探讨情感与灵魂存在意义的催泪视觉小说巅峰。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.0f, 9.6f, 10.0f, 8.5f, 2.0f, 9.8f)),
                GameEntry("Ori and the Will of the Wisps", "Moon Studios", "唯美动作冒险", "finished", listOf("视听艺术", "奥日2", "唯美动作", "萤火意志", "催泪感动"), 5.0, "为了守护森林与挚友库，微光化作永恒的神圣古树，拥抱光明。", "艺术品级别的视听盛宴。流畅如丝绸的位移手感与直击心灵的交响乐，将光与暗的牺牲救赎演绎到极致。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.0f, 10.0f, 10.0f, 9.0f, 7.5f, 9.8f)),
                GameEntry("Ori and the Blind Forest", "Moon Studios", "唯美动作冒险", "finished", listOf("水彩童话", "尼贝尔森林", "母爱挽歌", "唯美跳跃"), 4.9, "在尼贝尔森林枯萎的黑暗中，小精灵奥日点亮希望的第一缕微光。", "如同在动态水彩画中奔跑的艺术巨作。开场十分钟母子情深的动画让无数玩家潸然泪下。", "https://images.unsplash.com/photo-1474932430478-367dbb6832c1?w=600", floatArrayOf(8.8f, 10.0f, 9.8f, 8.8f, 7.0f, 9.8f)),

                // 5. 叙事开放世界与沉浸模拟
                GameEntry("赛博朋克 2077", "CD PROJEKT RED", "开放世界RPG", "finished", listOf("夜之城", "赛博朋克", "强尼银手", "往日之影", "CDPR"), 4.9, "夜之城没有活着的传奇。大闹一场吧V，把荒坂塔点燃，敬所有的来生酒！", "科幻赛博朋克视觉艺术与沉浸叙事的集大成之作。强尼·银手的意识寄生与夜之城冷血资本下的悲壮挽歌。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(9.5f, 9.8f, 9.8f, 8.8f, 6.5f, 8.0f)),
                GameEntry("巫师 3：狂猎", "CD PROJEKT RED", "开放世界RPG", "finished", listOf("年度最佳", "白狼", "欧美RPG巅峰", "石之心", "血与酒"), 5.0, "恶就是恶，无论是大恶还是小恶，如果要我二选一，我宁愿什么都不选。白狼杰洛特与希里。", "欧美 RPG 叙事不可逾越的高山。从血腥男爵的悲剧到陶森特的阳光葡萄园，展现了最真实的人性灰度与史诗冒险。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.8f, 9.8f, 9.8f, 9.5f, 6.5f, 8.5f)),
                GameEntry("底特律：化身为人类", "Quantic Dream", "互动电影叙事", "finished", listOf("互动电影", "仿生人觉醒", "康纳酱", "自由之声", "蝴蝶效应"), 4.9, "我是一个有感觉的生命，我不是机器。康纳、马库斯与卡拉的觉醒之歌。", "互动电影游戏的巅峰代表作。庞大复杂的蝴蝶效应分支树与探讨仿生人自我意识、人权与自由的深刻命题。", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600", floatArrayOf(9.5f, 9.5f, 9.8f, 9.4f, 4.0f, 9.0f)),
                GameEntry("饥荒", "Klei Entertainment", "生存沙盒", "finished", listOf("硬核生存", "哥特手绘", "San值狂掉", "联机神作"), 4.9, "在永夜与疯癫降临前点燃火把。活下去，在这个充满哥特荒诞的荒野世界！", "暗黑手绘哥特风生存沙盒的巅峰。理智值（San值）与四季生物群系的机制让人沉浸其中数百小时。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(8.8f, 9.5f, 8.8f, 9.5f, 8.8f, 8.0f)),
                GameEntry("缺氧", "Klei Entertainment", "硬核模拟经营", "finished", listOf("硬核科学", "自动化工程", "Klei", "小行星殖民"), 4.9, "热力学定律在小行星内部运作。调配每一口氧气与热量，带领复制人建立太空文明！", "硬核物理热力学与自动化工程模拟的天花板。管道、电路与气体液体的流动构建了无与伦比的工业美学。", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600", floatArrayOf(9.0f, 9.0f, 8.0f, 10.0f, 9.8f, 8.0f)),
                GameEntry("极限竞速：地平线 4", "Playground Games", "竞速汽车文化", "finished", listOf("地平线4", "四季更迭", "英伦漫游", "赛车巅峰"), 4.9, "四季更迭的英伦风光，在爱丁堡的落叶与雪原上肆意漂移，享受纯粹的驾驶浪漫！", "开放世界赛车游戏的殿堂标杆。如画的英伦乡间与上百款超跑的声浪交响。", "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=600", floatArrayOf(7.5f, 9.8f, 8.5f, 8.5f, 4.5f, 10.0f)),
                GameEntry("《猎人：荒野的召唤™》", "Expansive Worlds", "荒野沉浸模拟", "finished", listOf("大自然漫步", "荒野召唤", "极致风景", "沉浸模拟", "治愈养生"), 4.8, "在莱顿湖区的晨雾与林风中驻足，倾听荒野最纯净的呼吸与生命律动。", "大自然风光沉浸感第一的荒野漫步模拟器。极其逼真的植被光影、动物足迹追踪与风向气味系统。", "https://images.unsplash.com/photo-1511497584788-87676104235f?w=600", floatArrayOf(8.0f, 9.8f, 8.5f, 9.0f, 5.0f, 10.0f)),
                GameEntry("房产达人", "Frozen District", "沉浸装修模拟", "finished", listOf("极度解压", "房屋翻新", "室内设计", "模拟经营"), 4.8, "挥动大锤砸碎旧墙，铺设全新地板与暖光。亲手打造独一无二的心灵居所！", "极度解压上头的房屋翻新改造模拟器。从破旧瓦房到奢华别墅的成就感满满。", "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=600", floatArrayOf(7.5f, 9.0f, 8.5f, 8.8f, 3.0f, 10.0f)),
                GameEntry("冒险村物语 (Dungeon Village)", "开罗游戏 Kairosoft", "经典像素模拟", "finished", listOf("开罗游戏", "像素模拟", "勇者冒险", "经典治愈"), 4.9, "在村口建立旅馆与武器店，吸引勇者们定居，打造世界第一的冒险之村！", "开罗像素模拟经营的经典代表作。看着冒险者们升级装备、讨伐巨龙并入住村庄，充满纯粹的养成快乐。", "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600", floatArrayOf(7.5f, 9.0f, 9.0f, 9.2f, 3.0f, 10.0f)),
                GameEntry("Beholder", "Warm Lamp Games", "反乌托邦道德抉择", "finished", listOf("反乌托邦", "道德抉择", "极权阴影", "人性拷问"), 4.8, "在极权公寓的锁孔后窥视住户。服从法令还是拯救良知？每一次告密都在拷问人性。", "冷峻的反乌托邦道德抉择神作。在生存重压与人性善念之间的艰难博弈。", "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=600", floatArrayOf(9.5f, 9.2f, 9.4f, 9.2f, 6.5f, 6.0f)),

                // 6. 中式悬疑与经典恐怖
                GameEntry("烟火", "拾英工作室", "中式悬疑解谜", "finished", listOf("中式恐怖", "拾英工作室", "田芳芳", "催泪悬疑", "时代悲悯"), 5.0, "愿你此去，前程似锦，再无羁绊。送林理洵与田芳芳最后一程烟火。", "最具中式人情味与时代悲悯感的悬疑叙事神作。凄美的水彩国风与直击人心的乡村迷信悲剧。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.5f, 9.6f, 10.0f, 9.2f, 4.0f, 9.5f)),
                GameEntry("黑森町绮谭", "拾英工作室", "日系悬疑解谜", "finished", listOf("昭和物语", "拾英工作室", "妖怪奇谭", "治愈感动"), 4.9, "昭和泡沫时代的电车与妖怪物语。黑森町的樱花树下，告别过往的执念。", "拾英工作室开山之作。浪漫奇幻与现实历史相交融的叙事神作。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(9.0f, 9.5f, 9.8f, 9.0f, 3.5f, 9.5f)),
                GameEntry("小小梦魇 强化版", "Tarsier Studios", "悬疑解谜恐怖", "finished", listOf("心理恐怖", "黄色雨衣小六", "环境叙事", "艺术解谜"), 4.9, "黄色雨衣小六在贪颚号巨轮中穿行。饥饿与深渊之下，逃离扭曲成人的诡异梦魇。", "微缩视角下的心理恐怖神作。精妙绝伦的环境叙事与无台词压迫感，探讨童年恐惧与人性异化。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.2f, 9.8f, 9.2f, 9.2f, 6.0f, 6.0f)),
                GameEntry("小小梦魇2", "Tarsier Studios", "悬疑解谜恐怖", "finished", listOf("小小梦魇2", "纸袋头摩诺", "电视电波", "背叛轮回"), 4.9, "纸袋头少年摩诺牵起小六的手，穿越被信号塔扭曲的苍白之城。", "直击心灵的续作巅峰。令人心碎的终极反转与宿命循环。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.4f, 9.8f, 9.6f, 9.4f, 6.5f, 6.0f)),
                GameEntry("Ib", "kouri", "日系RPG解谜", "finished", listOf("四大名著RPG", "红黄蓝玫瑰", "Garry", "催泪经典"), 5.0, "红黄蓝三色玫瑰，与 Garry 一起在格鲁特纳的美术馆中逃离无尽画布，永恒的约定。", "四大经典 RPG 恐怖解谜之首。充满艺术感的美术馆谜题与深深打动无数人的角色羁绊。", "https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=600", floatArrayOf(9.2f, 9.8f, 10.0f, 9.0f, 4.0f, 9.2f)),
                GameEntry("港诡实录", "Ghostpie Games", "中式民俗恐怖", "finished", listOf("港风恐怖", "九龙城寨", "粤剧民俗", "佳慧"), 4.6, "九龙城寨的狭窄弄堂与粤剧回响。嘉慧，不要回头！", "充满老香港都市传说与九龙城寨民俗文化的沉浸式恐怖解谜。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(8.0f, 8.8f, 8.5f, 8.6f, 6.0f, 5.0f)),
                GameEntry("Dying Light", "Techland", "开放世界丧尸跑酷", "finished", listOf("哈兰跑酷", "夜魔追逐", "抓钩起飞", "丧尸生存"), 4.8, "好夜晚，好运气。在哈兰市的屋顶上飞跃，夜晚降临后与夜魔展开生死追逐！", "丧尸跑酷题材的巅峰代表作。白天搜刮物资夜晚狂奔逃命的极致肾上腺素刺激。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(8.0f, 8.8f, 8.5f, 8.8f, 7.5f, 8.0f)),
                GameEntry("雅皮士精神", "Baroque Decay", "职场像素恐怖", "finished", listOf("职场恐怖", "像素解谜", "辛特拉女巫", "荒诞讽刺"), 4.8, "入职世界第一巨头辛特拉集团的第一天，任务是猎杀盘踞在公司的女巫！", "荒诞职场讽刺与像素心理恐怖的绝妙融合。九十年代复古美学与深层反资本隐喻。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(9.0f, 9.5f, 9.0f, 9.2f, 5.5f, 7.5f)),
                GameEntry("灰烬之棺 Coffin of Ashes", "二律背反", "日系悬疑解谜", "finished", listOf("日系解谜", "暴雨古宅", "多结局", "悬疑微恐"), 4.7, "暴雨夜被困于神秘古宅，在血色与灰烬的记忆中找寻失落的真相。", "氛围极佳的日系微恐 RPG 解谜游戏。优美的立绘配乐与多结局救赎。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(8.8f, 9.2f, 9.4f, 8.8f, 4.0f, 8.0f)),
                GameEntry("白色情人节：恐怖学校", "SONNORI", "第一人称恐怖", "finished", listOf("校园恐怖", "深夜逃生", "东方惊悚", "经典重制"), 4.7, "深夜潜入延斗高中送糖果，却陷入恶灵与疯狂守卫巡逻的致命迷宫！", "经典东方校园恐怖的先驱之作。无武器反抗机制带来的极致惊悚逃生体验。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(8.2f, 8.8f, 8.5f, 8.8f, 7.5f, 5.0f)),
                GameEntry("Lakeview Cabin Collection", "Roope Tamminen", "复古砍杀解谜", "finished", listOf("复古恐怖", "B级片解谜", "脑洞大开", "湖景小屋"), 4.7, "致敬八十年代经典 B 级恐怖电影。用场景中一切荒谬道具反杀面具杀人魔！", "脑洞极大、解法极度自由的像素沙盒恐怖解谜神作。", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", floatArrayOf(8.5f, 9.0f, 8.0f, 9.5f, 8.5f, 7.0f)),

                // 7. 竞技合作与合家欢
                GameEntry("Plants vs. Zombies: Game of the Year", "PopCap Games · 宝开", "经典塔防", "finished", listOf("童年神作", "PopCap巅峰", "戴夫疯狂", "经典塔防"), 5.0, "屋顶上的玉米加农炮与后院泳池的向日葵。There's a Zombie on your Lawn!", "承载整整一代人童年欢笑的塔防艺术极品。无懈可击的节奏平衡与充满创意的植物僵尸设计。", "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600", floatArrayOf(8.0f, 9.5f, 9.5f, 9.5f, 4.0f, 10.0f)),
                GameEntry("Kingdom Rush", "Ironhide Game Studio", "经典策略塔防", "finished", listOf("塔防教科书", "Ironhide", "为了国王", "策略巅峰"), 5.0, "For the King! 弓箭塔、法师塔与圣骑士，誓死捍卫利尼维亚王国的荣耀！", "被誉为传统策略塔防顶峰的乌拉圭独立神作。极具深度的英雄操控与地形战术配置。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(8.2f, 9.2f, 8.8f, 9.8f, 7.5f, 9.0f)),
                GameEntry("Kingdom Rush Frontiers", "Ironhide Game Studio", "经典策略塔防", "finished", listOf("前线突破", "外星异形", "沙漠丛林", "塔防神作"), 5.0, "迎击沙漠强盗与丛林巨怪！全面进阶的英雄技能与防御工事。", "王国保卫战系列集大成之作。更多变的地形危机与更刺激的 BOSS 决战。", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", floatArrayOf(8.2f, 9.2f, 8.8f, 9.8f, 7.5f, 9.0f)),
                GameEntry("雀魂麻将", "猫粮工作室 · 悠星网络", "日麻竞技", "finished", listOf("雀魂", "立直一发", "国士无双", "日麻对局"), 4.8, "立直！一发！自摸！断幺九也能点燃役满的牌桌奇迹。", "将日式立直麻将与二次元角色立绘完美结合的现象级竞技棋牌。", "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600", floatArrayOf(7.5f, 9.0f, 8.8f, 9.8f, 7.0f, 8.5f)),
                GameEntry("人类一败涂地 / Human Fall Flat", "No Brakes Games", "物理合家欢", "finished", listOf("面条人", "沙雕开黑", "物理引擎", "爆笑解压"), 4.8, "摇摇晃晃的面条人，与好友互相拉扯坠落悬崖的欢笑时光。", "软体物理引擎带来的纯粹友谊与欢笑。最适合与朋友一起开黑解谜的解压神作。", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600", floatArrayOf(7.0f, 8.8f, 9.6f, 8.0f, 3.0f, 10.0f)),
                GameEntry("Goose Goose Duck", "Gaggle Studios", "社交推理", "finished", listOf("鹅鸭杀", "鹈鹕吃人", "社交推理", "开黑神器"), 4.8, "我是好鹅！别刀我！充满心机与爆笑的太空飞船社交推理派对。", "丰富职业设定与语音互动的现象级社交推理合家欢神作。", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600", floatArrayOf(7.5f, 8.8f, 9.5f, 9.5f, 5.0f, 9.5f)),
                GameEntry("Among Us", "Innersloth", "社交推理", "finished", listOf("太空狼人杀", "内鬼冒充", "紧急会议", "经典开黑"), 4.8, "内鬼就在我们之中！修理飞船、跳通风管，展开斗智斗勇的紧急会议投票。", "掀起全球太空狼人杀狂潮的经典独立社交推理神作。", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600", floatArrayOf(7.5f, 8.8f, 9.4f, 9.5f, 5.0f, 9.5f)),
                GameEntry("Counter-Strike 2", "Valve", "战术射击竞技", "finished", listOf("CS2", "V社电竞", "急停爆头", "战术竞技"), 4.8, "一颗闪光弹，一次冷静的急停爆头，为了团队拆除炸弹的最后一秒！", "FPS 电子竞技的不朽基石。起源2引擎下的全新烟雾物理与极致硬核枪法对决。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(7.5f, 8.5f, 8.8f, 9.5f, 9.5f, 7.5f)),
                GameEntry("使命召唤®", "Activision", "电影化FPS", "finished", listOf("使命召唤", "普莱斯队长", "现代战争", "电影化射击"), 4.8, "Bravo Six, Going Dark. 普莱斯队长带领第141特遣队深入敌后！", "电影化第一人称射击叙事的教科书巅峰。震撼的宏大战争场面与绝佳手感。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(8.0f, 9.2f, 9.0f, 8.8f, 7.0f, 8.0f)),
                GameEntry("《战地风云 5》", "DICE · EA", "二战战场模拟", "finished", listOf("二战战场", "寒霜引擎", "大战场协同", "破坏物理"), 4.8, "在鹿特丹的断壁残垣与北非沙漠中冲锋，体验二战浩瀚沙场的震撼与残酷。", "寒霜引擎打造的顶级二战战场视听。破坏物理效果与大战场兵种协同作战的极致体验。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(8.0f, 9.2f, 8.5f, 9.0f, 7.5f, 7.5f)),
                GameEntry("Apex Legends", "Respawn Entertainment", "战术英雄大逃杀", "finished", listOf("身法射击", "英雄战术", "诸王峡谷", "丝滑滑铲"), 4.8, "滑铲、滑索、身法起飞！捍卫者的荣耀属于默契无间的诸王峡谷三人小队！", "重生工作室打造的高机动性英雄战术射击巅峰。极度丝滑的身法滑铲与快节奏团战。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(7.8f, 9.0f, 8.8f, 9.4f, 8.8f, 8.0f)),
                GameEntry("彩虹六号：围攻", "Ubisoft Montreal", "室内战术CQB", "finished", listOf("育碧神作", "战术CQB", "信息博弈", "可破坏环境"), 4.8, "爆破加固墙体，小车侦察点位。毫厘之间的信息战与垂直进攻战术！", "CQB 室内近距离战术射击的绝对王者。完全可破坏的墙体物理与干员技能博弈。", "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600", floatArrayOf(8.0f, 8.8f, 8.5f, 10.0f, 9.8f, 7.0f)),
            )

            val now = currentTimestamp()

            gameList.forEach { game ->
                val cursor = db.query(
                    TABLE_BOOKS,
                    arrayOf(COLUMN_ID, COLUMN_COVER_URL, COLUMN_SHORT_COMMENT),
                    "$COLUMN_TITLE = ? AND $COLUMN_IS_DELETED = 0",
                    arrayOf(game.title),
                    null,
                    null,
                    null,
                )

                var bookId: Long = -1
                val exists = cursor.use { c ->
                    if (c.moveToFirst()) {
                        bookId = c.getLong(0)
                        true
                    } else false
                }

                if (exists) {
                    val cv = ContentValues().apply {
                        put(COLUMN_MEDIA_TYPE, "game")
                        if (game.shortComment != null) put(COLUMN_SHORT_COMMENT, game.shortComment)
                        if (game.review != null) put(COLUMN_REVIEW, game.review)
                        if (game.rating != null) put(COLUMN_RATING, game.rating)
                        if (game.coverUrl.isNotBlank()) put(COLUMN_COVER_URL, game.coverUrl)
                        put(COLUMN_TAGS, JSONArray(game.tags).toString())
                    }
                    db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(bookId.toString()))
                } else {
                    val cv = ContentValues().apply {
                        put(COLUMN_TITLE, game.title)
                        put(COLUMN_AUTHOR, game.author)
                        put(COLUMN_CATEGORY, game.category)
                        put(COLUMN_STATUS, game.status)
                        put(COLUMN_MEDIA_TYPE, "game")
                        put(COLUMN_SHORT_COMMENT, game.shortComment)
                        put(COLUMN_REVIEW, game.review)
                        put(COLUMN_RATING, game.rating ?: 5.0)
                        put(COLUMN_TAGS, JSONArray(game.tags).toString())
                        put(COLUMN_COVER_URL, game.coverUrl)
                        put(COLUMN_START_DATE, "2026-07-15")
                        put(COLUMN_FINISH_DATE, "2026-08-10")
                        put(COLUMN_BUY_CHANNEL, "Steam 平台 · 正版入库")
                        put(COLUMN_SHELF_LOCATION, "展厅第5层 · 电子游戏神作馆")
                        put(COLUMN_BINDING_TYPE, "Steam 数字豪华版")
                        put(COLUMN_CREATED_AT, now)
                        put(COLUMN_UPDATED_AT, now)
                        put(COLUMN_IS_DELETED, 0)
                    }
                    bookId = db.insert(TABLE_BOOKS, null, cv)
                }

                // 注入六维心智雷达
                if (game.mindprint != null && bookId > 0) {
                    val mp = game.mindprint
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

    private fun seedUserPodcastMusicList(db: SQLiteDatabase) {
        runCatching {
            data class PodcastMusicEntry(
                val title: String,
                val artist: String,
                val category: String,
                val status: String,
                val year: String,
                val tags: List<String>,
                val rating: Double?,
                val shortComment: String?,
                val review: String?,
                val coverUrl: String,
                val mindprint: FloatArray? = null,
            )

            val podcastMusicList = listOf(
                // 🌙 ヨルシカ (Yorushika / 夜鹿) 2023 - 2024 经典作品
                PodcastMusicEntry(
                    title = "晴る (Haru)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "日系摇滚 / 物哀美学",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "葬送的芙莉莲OP", "夜鹿", "n-buna", "治愈神曲"),
                    rating = 5.0,
                    shortComment = "向着蔚蓝的晴空挥手作别，那滴落在手心的泪水，终会化为滋润大地的春雨。",
                    review = "TV动画《葬送的芙莉莲》第2季度 OP 主题曲。n-buna 标志性的清澈吉他扫弦与 suis 纯净高亢的声线，将千年精灵对漫长时光、生死别离的释然与深情吟唱得淋漓尽致，堪称 2024 年日系摇滚的巅峰之作。",
                    coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                    mindprint = floatArrayOf(9.6f, 9.8f, 10.0f, 9.0f, 3.5f, 10.0f),
                ),
                PodcastMusicEntry(
                    title = "アポリア (Aporia)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "哲学摇滚 / 宇宙浪漫",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "关于地球的运动ED", "地动说", "真理追寻", "夜鹿"),
                    rating = 5.0,
                    shortComment = "即便双脚陷于泥泞，我们依然要仰望并追寻那转动星辰的真理之火。",
                    review = "TV动画《地。-关于地球的运动-》ED 主题曲。歌名 Aporia 意为哲学术语中的‘困惑 / 无路可走’。探讨人类在浩瀚宇宙未知面前的渺小，以及前仆后继为真理献身的壮丽诗篇。",
                    coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                    mindprint = floatArrayOf(9.8f, 9.6f, 9.6f, 9.5f, 5.0f, 8.5f),
                ),
                PodcastMusicEntry(
                    title = "忘れてください (Please Forget)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "日系抒情 / 夏日叙事",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "夏日残响", "suis", "温柔放手", "夜鹿"),
                    rating = 4.9,
                    shortComment = "如果回忆会成为你的负担，那就请你连同我的名字与这个夏夜，一并遗忘吧。",
                    review = "夜鹿经典的夏日与离别物语。低回呢喃的琴键伴奏与渐进的弦乐编制，刻画出极致的物哀之美与温柔的解脱。",
                    coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                    mindprint = floatArrayOf(9.4f, 9.8f, 10.0f, 8.5f, 3.0f, 9.5f),
                ),
                PodcastMusicEntry(
                    title = "ルバート (Rubato)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "灵动轻摇滚 / 爵士切分",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "自由节拍", "漫步曲", "灵动", "夜鹿"),
                    rating = 4.8,
                    shortComment = "在不被定义的拍子中自在漫步，把生活中的每一次停顿写成一首浪漫的散步曲。",
                    review = "轻快跳跃的爵士摇摆律动，如同雨后初霁在湿润的柏油路面上随意踏水前行，自由而充满生命力。",
                    coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                    mindprint = floatArrayOf(8.8f, 9.5f, 9.2f, 9.0f, 2.5f, 9.8f),
                ),
                PodcastMusicEntry(
                    title = "斜陽 (Setting Sun)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "青春摇滚 / 酸甜心境",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "我心里危险的东西OP", "青春心动", "斜阳", "夜鹿"),
                    rating = 5.0,
                    shortComment = "放学后被斜阳染红的走廊里，那心照不宣的对视，是整个青春最滚烫的秘密。",
                    review = "TV动画《我心里危险的东西》第1季 OP 主题曲。轻盈奔放的吉他分解和弦与青涩悸动的歌词，描摹出初恋最纯粹的心动轨迹。",
                    coverUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
                    mindprint = floatArrayOf(9.0f, 9.6f, 9.8f, 8.8f, 2.0f, 10.0f),
                ),
                PodcastMusicEntry(
                    title = "アルジャーノン (Algernon)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "文学摇滚 / 治愈救赎",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "黄昏牵手", "献给阿尔吉侬的花束", "慢热神曲", "夜鹿"),
                    rating = 5.0,
                    shortComment = "慢慢地、慢慢地成长，即便智慧终会退去，也请在我的墓前放上一束鲜花。",
                    review = "TBS电视剧《夕暮れに、手をつなぐ》主题曲。灵感源自丹尼尔·凯斯世界名著《献给阿尔吉侬的花束》，温柔而深邃的生命叹息。",
                    coverUrl = "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600",
                    mindprint = floatArrayOf(9.6f, 9.8f, 10.0f, 9.0f, 3.0f, 10.0f),
                ),
                PodcastMusicEntry(
                    title = "451 (华氏451)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "硬派反乌托邦摇滚",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "画集幻燈", "华氏451", "硬派摇滚", "思想火种"),
                    rating = 4.9,
                    shortComment = "书页在华氏451度燃烧，但思想的火种永远不会在灰烬中熄灭。",
                    review = "画集专辑《幻燈》核心收录曲。致敬科幻大师雷·布拉德伯里的经典反乌托邦巨著，重型吉他 Riff 与极具张力的演唱。",
                    coverUrl = "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=600",
                    mindprint = floatArrayOf(9.6f, 9.5f, 9.2f, 9.6f, 5.0f, 7.0f),
                ),
                PodcastMusicEntry(
                    title = "月光浴 (Moonlight Bath)",
                    artist = "ヨルシカ (Yorushika) · n-buna / suis",
                    category = "唯美抒情 / 静谧夜色",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "大名倒产主题曲", "月光", "静心", "夜鹿"),
                    rating = 4.9,
                    shortComment = "在银白色的月光下洗尽尘世疲惫，时间在夜风里静止，灵魂重归静谧。",
                    review = "电影《大名倒产》主题曲。如同在深夜独自漫步在清凉月色下，琴音与声线如清泉流淌，抚平一切喧嚣与焦虑。",
                    coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
                    mindprint = floatArrayOf(9.2f, 9.8f, 9.8f, 8.5f, 2.0f, 10.0f),
                ),

                // 🌌 ずっと真夜中でいいのに。 (ZUTOMAYO / 永远是深夜有多好。) 2023 - 2024 经典作品
                PodcastMusicEntry(
                    title = "嘘じゃない (No Lie)",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "前卫放克摇滚 / 疾走感",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "我的鬼女孩主题曲", "ACAね", "神级放克", "真夜中"),
                    rating = 5.0,
                    shortComment = "即便把软弱和真心伪装起来，那份为你而战的执念，绝对不是谎言！",
                    review = "动画电影《我的鬼女孩 (My Oni Girl)》主题曲。ACAね 标志性的高速吉他切音与炸裂的 Slap Bass，在疾走感中诉说着少年少女笨拙却炽热的真心。",
                    coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                    mindprint = floatArrayOf(9.4f, 9.8f, 9.8f, 9.2f, 4.0f, 9.0f),
                ),
                PodcastMusicEntry(
                    title = "Blues in the Closet",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "夜光放克 / 都会律动",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "真夜中放克", "秘密衣橱", "都会孤独", "ACAね"),
                    rating = 4.9,
                    shortComment = "把所有的不安塞进衣橱深处，戴上耳机，在蓝调的重低音里独自起舞。",
                    review = "ACAね 极具辨识度的真假音转换与复杂的爵士和弦走向，将都市年轻人在暗夜中的敏感孤独转化为摇摆律动。",
                    coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600",
                    mindprint = floatArrayOf(9.0f, 9.8f, 9.4f, 9.5f, 4.5f, 8.8f),
                ),
                PodcastMusicEntry(
                    title = "海馬成長痛 (Hippocampus Growing Pains)",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "意识流摇滚 / 锐利切分",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "虚仮の一念", "成长阵痛", "高密节奏", "真夜中"),
                    rating = 4.9,
                    shortComment = "海马体中隐隐作痛的记忆回响，是灵魂脱胎换骨的证明。",
                    review = "迷你专辑《虚仮の一念海馬に託す》主打曲。密集的节奏鼓点与天马行空的歌词隐喻，直击现代人的精神内耗与觉醒。",
                    coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                    mindprint = floatArrayOf(9.5f, 9.8f, 9.6f, 9.4f, 6.0f, 8.0f),
                ),
                PodcastMusicEntry(
                    title = "TAIKUTSU (退屈)",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "疾走放克摇滚",
                    status = "finished",
                    year = "2024",
                    tags = listOf("2024年", "超自然当哒当联动", "炸裂贝斯", "音速暴击", "真夜中"),
                    rating = 4.9,
                    shortComment = "用狂暴的贝斯轰碎无聊日常，在超自然的夜色中掀起狂澜！",
                    review = "极速狂飙的贝斯与二胡传统民乐音色奇妙碰撞，真夜中独门的高密度音乐轰炸，打破一切审美疲劳。",
                    coverUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600",
                    mindprint = floatArrayOf(9.0f, 9.6f, 9.2f, 9.5f, 5.0f, 8.5f),
                ),
                PodcastMusicEntry(
                    title = "花一匁 (Hanaichimonme)",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "和风放克摇滚 / 殿堂主打",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "沈香学", "神专主打", "童谣解构", "真夜中"),
                    rating = 5.0,
                    shortComment = "想要那个孩子，不给那个孩子。在世俗的算计与博弈中，夺回属于自己的心跳。",
                    review = "3rd 专辑《沈香学》核心主打神作。将日本古老童谣《花一匁》解构重组为充满朋克反叛精神与精巧律动的殿堂级放克曲。",
                    coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                    mindprint = floatArrayOf(9.6f, 10.0f, 9.8f, 9.6f, 5.5f, 9.0f),
                ),
                PodcastMusicEntry(
                    title = "沈香学 (Jin Kou Gaku 专辑)",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "正规概念专辑 / 殿堂之作",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "公信榜冠军", "沈香学", "年度神专", "真夜中"),
                    rating = 5.0,
                    shortComment = "像沉香一样历经伤口与岁月沉淀，在深夜里散发出幽微而绝美的香气。",
                    review = "真夜中集大成的第3张正规概念专辑。收录《残机》《綺羅キラー》《消えてしまいそうです》等多首殿堂名曲，狂放与细腻并存。",
                    coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                    mindprint = floatArrayOf(9.8f, 10.0f, 9.8f, 9.6f, 6.0f, 9.5f),
                ),
                PodcastMusicEntry(
                    title = "不法侵入 (Trespass)",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "迷幻放克 / 恋爱心境",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "ABEMA恋爱番", "侵入心扉", "律动放克", "真夜中"),
                    rating = 4.8,
                    shortComment = "未经允许便悄然闯入我心中的你，留下了无法抹去的痕迹。",
                    review = "ABEMA 节目主题曲。标志性的键盘敲击与灵动声线，勾勒出恋爱中防不胜防的心动瞬间。",
                    coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                    mindprint = floatArrayOf(9.0f, 9.6f, 9.5f, 9.0f, 3.5f, 9.0f),
                ),
                PodcastMusicEntry(
                    title = "残機 (Time Left)",
                    artist = "ずっと真夜中でいいのに。 (ZUTOMAYO) · ACAね",
                    category = "硬核疾走放克 / 爆裂现场",
                    status = "finished",
                    year = "2023",
                    tags = listOf("2023年", "电锯人ED2", "残机", "血脉贲张", "真夜中"),
                    rating = 5.0,
                    shortComment = "即便剩余的生命只剩一条，也要握紧电锯，在血肉横飞的绝望里杀穿终局！",
                    review = "TV动画《电锯人》ED2。爆裂的切分音与 ACAね 的狂气嘶吼，堪称日系摇滚新浪潮的核弹级现场演绎。",
                    coverUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
                    mindprint = floatArrayOf(9.6f, 9.8f, 9.6f, 9.5f, 6.5f, 8.5f),
                ),
            )

            val now = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(java.time.LocalDateTime.now())

            podcastMusicList.forEach { item ->
                val cursor = db.query(
                    TABLE_BOOKS,
                    arrayOf(COLUMN_ID),
                    "$COLUMN_TITLE = ? AND $COLUMN_IS_DELETED = 0",
                    arrayOf(item.title),
                    null, null, null,
                )
                var bookId = 0L
                val exists = cursor.use {
                    if (it.moveToFirst()) {
                        bookId = it.getLong(0)
                        true
                    } else false
                }

                if (exists) {
                    val cv = ContentValues().apply {
                        put(COLUMN_MEDIA_TYPE, "podcast")
                        if (item.shortComment != null) put(COLUMN_SHORT_COMMENT, item.shortComment)
                        if (item.review != null) put(COLUMN_REVIEW, item.review)
                        if (item.rating != null) put(COLUMN_RATING, item.rating)
                        if (item.coverUrl.isNotBlank()) put(COLUMN_COVER_URL, item.coverUrl)
                        put(COLUMN_TAGS, JSONArray(item.tags).toString())
                    }
                    db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(bookId.toString()))
                } else {
                    val cv = ContentValues().apply {
                        put(COLUMN_TITLE, item.title)
                        put(COLUMN_AUTHOR, item.artist)
                        put(COLUMN_CATEGORY, item.category)
                        put(COLUMN_STATUS, item.status)
                        put(COLUMN_MEDIA_TYPE, "podcast")
                        put(COLUMN_SHORT_COMMENT, item.shortComment)
                        put(COLUMN_REVIEW, item.review)
                        put(COLUMN_RATING, item.rating ?: 5.0)
                        put(COLUMN_TAGS, JSONArray(item.tags).toString())
                        put(COLUMN_COVER_URL, item.coverUrl)
                        put(COLUMN_START_DATE, "${item.year}-01-01")
                        put(COLUMN_FINISH_DATE, "${item.year}-12-31")
                        put(COLUMN_BUY_CHANNEL, "Apple Music / Spotify / 正版专辑")
                        put(COLUMN_SHELF_LOCATION, "声音宇宙 · 夜鹿 & 真夜中回响专区")
                        put(COLUMN_BINDING_TYPE, "Hi-Res 无损黑胶 / 正规专辑")
                        put(COLUMN_CREATED_AT, now)
                        put(COLUMN_UPDATED_AT, now)
                        put(COLUMN_IS_DELETED, 0)
                    }
                    bookId = db.insert(TABLE_BOOKS, null, cv)
                }

                // 注入六维心智雷达
                if (item.mindprint != null && bookId > 0) {
                    val mp = item.mindprint
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
     * 批量导入多维度 CSV 解析记录（包含作品属性与六维心智模型）。
     * 若已存在同名作品，则智能补充完善其评分、短评、长评、封面与六维心智；若不存在则新增插入。
     */
    fun importParsedRecords(records: List<com.example.readtrace.util.BookCsvParser.ParsedBookRecord>): Int {
        if (records.isEmpty()) return 0
        val db = writableDatabase
        var affectedCount = 0
        db.beginTransaction()
        try {
            val now = currentTimestamp()
            for (record in records) {
                val book = record.book
                val cleanTitle = book.title.trim()
                if (cleanTitle.isEmpty()) continue

                val cursor = if (book.author.isNullOrEmpty()) {
                    db.query(
                        TABLE_BOOKS,
                        arrayOf(COLUMN_ID),
                        "$COLUMN_TITLE = ? AND ($COLUMN_AUTHOR IS NULL OR $COLUMN_AUTHOR = '') AND $COLUMN_IS_DELETED = 0",
                        arrayOf(cleanTitle),
                        null, null, null, "1"
                    )
                } else {
                    db.query(
                        TABLE_BOOKS,
                        arrayOf(COLUMN_ID),
                        "$COLUMN_TITLE = ? AND $COLUMN_AUTHOR = ? AND $COLUMN_IS_DELETED = 0",
                        arrayOf(cleanTitle, book.author.trim()),
                        null, null, null, "1"
                    )
                }

                val existingId = cursor.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }

                val targetBookId: Long
                if (existingId != null) {
                    targetBookId = existingId
                    val updateCv = ContentValues().apply {
                        if (!book.coverUrl.isNullOrBlank()) put(COLUMN_COVER_URL, book.coverUrl)
                        if (!book.category.isNullOrBlank()) put(COLUMN_CATEGORY, book.category)
                        if (book.rating != null) put(COLUMN_RATING, book.rating)
                        if (book.tags.isNotEmpty()) put(COLUMN_TAGS, org.json.JSONArray(book.tags).toString())
                        if (!book.shortComment.isNullOrBlank()) put(COLUMN_SHORT_COMMENT, book.shortComment)
                        if (!book.review.isNullOrBlank()) put(COLUMN_REVIEW, book.review)
                        put(COLUMN_MEDIA_TYPE, book.mediaType.databaseValue)
                        put(COLUMN_UPDATED_AT, now)
                    }
                    db.update(TABLE_BOOKS, updateCv, "$COLUMN_ID = ?", arrayOf(targetBookId.toString()))
                    affectedCount++
                } else {
                    val values = book.toContentValues().apply {
                        put(COLUMN_TITLE, cleanTitle)
                        put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
                        put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
                        put(COLUMN_IS_DELETED, 0)
                        putNull(COLUMN_DELETED_AT)
                    }
                    val rowId = db.insert(TABLE_BOOKS, null, values)
                    if (rowId > 0) {
                        targetBookId = rowId
                        affectedCount++
                    } else {
                        targetBookId = -1L
                    }
                }

                // 写入或更新六维心智雷达
                if (targetBookId > 0 && record.mindprint != null) {
                    val mp = record.mindprint
                    val mpCv = ContentValues().apply {
                        put(COLUMN_BOOK_ID, targetBookId)
                        put(COLUMN_DEPTH_SCORE, mp.depthScore)
                        put(COLUMN_ARTISTRY_SCORE, mp.artistryScore)
                        put(COLUMN_EMOTION_SCORE, mp.emotionScore)
                        put(COLUMN_LOGIC_SCORE, mp.logicScore)
                        put(COLUMN_DIFFICULTY_SCORE, mp.difficultyScore)
                        put(COLUMN_HEALING_SCORE, mp.healingScore)
                        put(COLUMN_UPDATED_AT, now)
                    }
                    db.insertWithOnConflict(TABLE_BOOK_MINDPRINTS, null, mpCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return affectedCount
    }

    /**
     * 批量导入书籍列表（用于旧版兼容与快速文本导入）。
     */
    fun importBooks(books: List<Book>): Int {
        if (books.isEmpty()) return 0
        val records = books.map { com.example.readtrace.util.BookCsvParser.ParsedBookRecord(it, null) }
        return importParsedRecords(records)
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

    // 一次查询返回全部未删除的阅读记录，供 Widget 等场景批量统计使用，避免逐书 N+1 查询
    fun getAllReadingSessions(): List<ReadingSession> =
        readableDatabase.query(
            TABLE_READING_SESSIONS,
            null,
            "$COLUMN_IS_DELETED = 0",
            null,
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

    fun getAllMindprints(): Map<Long, BookMindprint> {
        val map = mutableMapOf<Long, BookMindprint>()
        runCatching {
            readableDatabase.query(
                TABLE_BOOK_MINDPRINTS,
                null,
                null,
                null,
                null,
                null,
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val mp = cursor.toBookMindprint()
                    map[mp.bookId] = mp
                }
            }
        }
        return map
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

    // --- 📱 桌面小组件专享数据支持 (AppWidgets Data Providers) ---

    fun getTodayTotalReadingMinutes(): Int {
        val todayStr = currentTimestamp().substringBefore("T")
        val cursor = readableDatabase.rawQuery(
            """
            SELECT SUM($COLUMN_DURATION_MINUTES) 
            FROM $TABLE_READING_SESSIONS 
            WHERE $COLUMN_CREATED_AT LIKE ? AND $COLUMN_IS_DELETED = 0
            """.trimIndent(),
            arrayOf("$todayStr%"),
        )
        return cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else 0 }
    }

    fun getConsecutiveReadingDays(): Int {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT DISTINCT substr($COLUMN_CREATED_AT, 1, 10) as session_date 
            FROM $TABLE_READING_SESSIONS 
            WHERE $COLUMN_IS_DELETED = 0 
            ORDER BY session_date DESC
            """.trimIndent(),
            null,
        )
        val dates = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
        if (dates.isEmpty()) return 0

        val today = LocalDate.now()
        var streak = 0
        var checkDate = today

        val latestDateStr = dates.first()
        val latestDate = runCatching { LocalDate.parse(latestDateStr) }.getOrNull() ?: return 0
        if (latestDate != today && latestDate != today.minusDays(1)) {
            return 0
        }
        checkDate = latestDate

        for (dateStr in dates) {
            val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: break
            if (d == checkDate) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else if (d < checkDate) {
                break
            }
        }
        return streak
    }

    fun getRandomOrNextQuote(excludeQuote: String? = null): Pair<Book?, String> {
        val quotesCursor = readableDatabase.rawQuery(
            """
            SELECT b.$COLUMN_ID, b.$COLUMN_TITLE, b.$COLUMN_AUTHOR, b.$COLUMN_COVER_URL, b.$COLUMN_MEDIA_TYPE,
                   n.$COLUMN_CONTENT
            FROM $TABLE_NOTES n
            JOIN $TABLE_BOOKS b ON n.$COLUMN_BOOK_ID = b.$COLUMN_ID
            WHERE n.$COLUMN_IS_DELETED = 0 AND b.$COLUMN_IS_DELETED = 0
            ORDER BY RANDOM()
            LIMIT 10
            """.trimIndent(),
            null,
        )
        val quotes = quotesCursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val book = Book(
                        id = c.getLong(0),
                        title = c.getString(1),
                        author = c.getString(2),
                        coverUrl = c.getString(3),
                        mediaType = MediaType.fromDatabaseValue(c.getString(4)),
                        status = BookStatus.READING,
                    )
                    val content = c.getString(5)
                    add(book to content)
                }
            }
        }

        val selected = quotes.firstOrNull { it.second != excludeQuote } ?: quotes.firstOrNull()
        if (selected != null) {
            return selected
        }

        val books = getBooks()
        val commentBooks = books.filter { !it.shortComment.isNullOrBlank() || !it.review.isNullOrBlank() }
        val randomCommentBook = commentBooks.shuffled().firstOrNull()
        if (randomCommentBook != null) {
            val quote = randomCommentBook.shortComment?.takeIf { it.isNotBlank() }
                ?: randomCommentBook.review?.takeIf { it.isNotBlank() }
                ?: "每一道心智印记，都是灵魂与文字的永恒交汇。"
            return randomCommentBook to quote
        }

        val defaultQuotes = listOf(
            "生命中真正重要的不是你遭遇了什么，而是你记住了哪些事，又是如何铭记的。",
            "给岁月以文明，而不是给文明以岁月。",
            "世界上只有一种真正的英雄主义，那就是认清生活的真相后依然热爱生活。",
            "一个人并不是生来要给打败的，你尽可以把他消灭掉，可就是打不败他。",
            "你所热爱的，就是你的生活；你所铭记的，就是你的痕迹。",
        )
        val defaultBook = books.firstOrNull()
        val quote = defaultQuotes.filter { it != excludeQuote }.randomOrNull() ?: defaultQuotes.first()
        return defaultBook to quote
    }

    fun getLatestReadingBook(): Book? {
        val books = getBooks(status = BookStatus.READING)
        if (books.isNotEmpty()) {
            return books.maxByOrNull { it.updatedAt } ?: books.first()
        }
        val allBooks = getBooks()
        return allBooks.firstOrNull()
    }

    fun quickRecordReadingSession(bookId: Long, minutes: Int = 15): Long {
        val session = ReadingSession(
            bookId = bookId,
            durationMinutes = minutes,
            thought = "⚡ 桌面小组件快捷打卡 +${minutes}min",
            createdAt = currentTimestamp(),
        )
        return insertReadingSession(session)
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
