package com.example.readtrace.data.migrator

import android.database.sqlite.SQLiteDatabase
import com.example.readtrace.data.*

/**
 * 数据库建表与版本升级迁移执行器。
 * 从 BookDatabaseHelper 中完全抽离，负责所有 DDL 建表与 v1~v15 增量升级逻辑。
 */
object DatabaseMigrator {

    fun onCreate(database: SQLiteDatabase) {
        createBooksTable(database)
        createNotesTable(database)
        createReadingSessionsTable(database)
        createCharactersTable(database)
        createOutlinesTable(database)
        createLocationsTable(database)
        createMindprintsTable(database)
        createAudioTracksTable(database)
        createFavoritesTable(database)
    }

    fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
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
        if (oldVersion < 6) {
            // v6：播客分类并入音乐，夜鹿/真夜中曲目统一迁移为 'music'
            runCatching {
                database.execSQL("UPDATE $TABLE_BOOKS SET $COLUMN_MEDIA_TYPE = 'music' WHERE $COLUMN_MEDIA_TYPE = 'podcast'")
            }
        }
        if (oldVersion < 9) {
            // v9：新增本地音频曲目表（黑胶音乐馆真实播放）
            createAudioTracksTable(database)
        }
        if (oldVersion < 7) {
            // v7：无表结构变更；预置封面由外网链接/打包资产统一改写为内网封面键，
            // 实际改写在 runPresetSeedsOnce 的 migrateCoversToLanKeys 中完成。
        }
        if (oldVersion < 10) {
            // v10 (4.2.14)：外部导入四列——来源类型/来源 ID/远程评分/简介；老数据全部视为手动录入。
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_SOURCE_TYPE TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_SOURCE_ID TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_REMOTE_RATING REAL") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_DESCRIPTION TEXT") }
            runCatching {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_books_source ON $TABLE_BOOKS " +
                        "($COLUMN_SOURCE_TYPE, $COLUMN_SOURCE_ID)",
                )
            }
        }
        if (oldVersion < 11) {
            createFavoritesTable(database)
        }
        if (oldVersion < 12) {
            // v12：无表结构变更；新增两款预设游戏（魔兽争霸3：冰封王座 / 虐杀原形），
            // 并让 rich_content 的章节大纲（outline）参与播种，实际写入在 runPresetSeedsOnce 中完成。
        }
        if (oldVersion < 13) {
            // v13：移除两部会因搜索三级匹配播错歌的预设单曲（TAIKUTSU/沈香学 专辑条目）。
            // 仅限预置/手动条目（source_type 为空）：网易云歌单导入的同名曲目（source_type='netease'）不得误删；
            // 删除作品前先级联清理各子表，避免 notes/打卡/心智/足迹/角色谱/大纲/收藏留下指向已删 bookId 的孤儿行。
            val removedTitles = arrayOf("TAIKUTSU (退屈)", "沈香学 (Jin Kou Gaku 专辑)")
            val presetMusicWhere = "$COLUMN_TITLE IN (?, ?) AND $COLUMN_MEDIA_TYPE = 'music' " +
                "AND ($COLUMN_SOURCE_TYPE IS NULL OR $COLUMN_SOURCE_TYPE = '')"
            database.execSQL(
                "DELETE FROM $TABLE_AUDIO_TRACKS WHERE $COLUMN_AUDIO_BOOK_ID IN " +
                    "(SELECT $COLUMN_ID FROM $TABLE_BOOKS WHERE $presetMusicWhere)",
                removedTitles,
            )
            listOf(
                TABLE_NOTES,
                TABLE_READING_SESSIONS,
                TABLE_BOOK_LOCATIONS,
                TABLE_BOOK_MINDPRINTS,
                TABLE_BOOK_CHARACTERS,
                TABLE_BOOK_OUTLINES,
            ).forEach { table ->
                database.execSQL(
                    "DELETE FROM $table WHERE $COLUMN_BOOK_ID IN " +
                        "(SELECT $COLUMN_ID FROM $TABLE_BOOKS WHERE $presetMusicWhere)",
                    removedTitles,
                )
            }
            database.execSQL(
                "DELETE FROM $TABLE_FAVORITES WHERE $COLUMN_FAVORITE_BOOK_ID IN " +
                    "(SELECT $COLUMN_ID FROM $TABLE_BOOKS WHERE $presetMusicWhere)",
                removedTitles,
            )
            database.execSQL(
                "DELETE FROM $TABLE_BOOKS WHERE $presetMusicWhere",
                removedTitles,
            )
        }
        if (oldVersion < 14) {
            // v14：级联移除《451 (华氏451)》与《Blues in the Closet》两部音乐作品
            val removedTitles = arrayOf("451 (华氏451)", "Blues in the Closet")
            val presetMusicWhere = "$COLUMN_TITLE IN (?, ?) AND $COLUMN_MEDIA_TYPE = 'music'"
            database.execSQL(
                "DELETE FROM $TABLE_AUDIO_TRACKS WHERE $COLUMN_AUDIO_BOOK_ID IN " +
                    "(SELECT $COLUMN_ID FROM $TABLE_BOOKS WHERE $presetMusicWhere)",
                removedTitles,
            )
            listOf(
                TABLE_NOTES,
                TABLE_READING_SESSIONS,
                TABLE_BOOK_LOCATIONS,
                TABLE_BOOK_MINDPRINTS,
                TABLE_BOOK_CHARACTERS,
                TABLE_BOOK_OUTLINES,
            ).forEach { table ->
                database.execSQL(
                    "DELETE FROM $table WHERE $COLUMN_BOOK_ID IN " +
                        "(SELECT $COLUMN_ID FROM $TABLE_BOOKS WHERE $presetMusicWhere)",
                    removedTitles,
                )
            }
            database.execSQL(
                "DELETE FROM $TABLE_FAVORITES WHERE $COLUMN_FAVORITE_BOOK_ID IN " +
                    "(SELECT $COLUMN_ID FROM $TABLE_BOOKS WHERE $presetMusicWhere)",
                removedTitles,
            )
            database.execSQL(
                "DELETE FROM $TABLE_BOOKS WHERE $presetMusicWhere",
                removedTitles,
            )
        }
        if (oldVersion < 15) {
            // v15: 音乐、影视、游戏三类作品从旧的 5 分制迁移为 7.0 ~ 8.0 离散分布
            database.execSQL(
                "UPDATE $TABLE_BOOKS SET $COLUMN_RATING = ROUND(7.0 + (ABS(RANDOM()) % 11) * 0.1, 1) " +
                    "WHERE $COLUMN_IS_DELETED = 0 " +
                    "AND $COLUMN_MEDIA_TYPE IN ('music', 'movie', 'game') " +
                    "AND ($COLUMN_RATING IS NULL OR $COLUMN_RATING <= 6.0)",
            )
        }
    }

    private fun createBooksTable(database: SQLiteDatabase) {
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
                $COLUMN_DELETED_AT TEXT,
                $COLUMN_SOURCE_TYPE TEXT,
                $COLUMN_SOURCE_ID TEXT,
                $COLUMN_REMOTE_RATING REAL,
                $COLUMN_DESCRIPTION TEXT
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX index_books_status_deleted ON $TABLE_BOOKS " +
                "($COLUMN_STATUS, $COLUMN_IS_DELETED)",
        )
        // 外部导入防重复：来源 + 来源条目 ID 联合索引
        database.execSQL(
            "CREATE INDEX index_books_source ON $TABLE_BOOKS " +
                "($COLUMN_SOURCE_TYPE, $COLUMN_SOURCE_ID)",
        )
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

    private fun createAudioTracksTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AUDIO_TRACKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_AUDIO_BOOK_ID INTEGER NOT NULL,
                $COLUMN_AUDIO_ORDER INTEGER NOT NULL DEFAULT 0,
                $COLUMN_AUDIO_TITLE TEXT NOT NULL,
                $COLUMN_AUDIO_URI TEXT NOT NULL,
                $COLUMN_AUDIO_DURATION INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY ($COLUMN_AUDIO_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
    }

    private fun createFavoritesTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FAVORITE_BOOK_ID INTEGER NOT NULL UNIQUE,
                $COLUMN_FAVORITE_MEDIA_TYPE TEXT NOT NULL,
                $COLUMN_FAVORITE_RANK_ORDER INTEGER NOT NULL DEFAULT 0,
                $COLUMN_FAVORITE_CUSTOM_TAGLINE TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                FOREIGN KEY ($COLUMN_FAVORITE_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_favorites_media ON $TABLE_FAVORITES " +
                "($COLUMN_FAVORITE_MEDIA_TYPE, $COLUMN_FAVORITE_RANK_ORDER)",
        )
    }
}
