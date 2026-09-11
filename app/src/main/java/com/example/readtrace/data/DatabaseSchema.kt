package com.example.readtrace.data

/**
 * 阅痕数据库架构与字段常量全量契约。
 * 包含顶级常量与 DatabaseSchema 契约门面，解耦自原 BookDatabaseHelper 巨石。
 */
const val DATABASE_NAME = "readtrace.db"
const val DATABASE_VERSION = 15

// 预设播种持久化标记
const val SEED_PREF = "readtrace_seed"
const val KEY_SEED_VERSION = "seed_version"

// 表名常量
const val TABLE_BOOKS = "books"
const val TABLE_NOTES = "notes"
const val TABLE_READING_SESSIONS = "reading_sessions"
const val TABLE_BOOK_CHARACTERS = "book_characters"
const val TABLE_BOOK_OUTLINES = "book_outlines"
const val TABLE_BOOK_LOCATIONS = "book_locations"
const val TABLE_BOOK_MINDPRINTS = "book_mindprints"
const val TABLE_AUDIO_TRACKS = "audio_tracks"
const val TABLE_FAVORITES = "curator_favorites"

// 通用列
const val COLUMN_ID = "id"
const val COLUMN_BOOK_ID = "book_id"
const val COLUMN_CREATED_AT = "created_at"
const val COLUMN_UPDATED_AT = "updated_at"
const val COLUMN_IS_DELETED = "is_deleted"
const val COLUMN_DELETED_AT = "deleted_at"

// 书籍表列 (books)
const val COLUMN_TITLE = "title"
const val COLUMN_AUTHOR = "author"
const val COLUMN_COVER_URL = "cover_url"
const val COLUMN_CATEGORY = "category"
const val COLUMN_STATUS = "status"
const val COLUMN_MEDIA_TYPE = "media_type"
const val COLUMN_RATING = "rating"
const val COLUMN_TAGS = "tags"
const val COLUMN_SHORT_COMMENT = "short_comment"
const val COLUMN_REVIEW = "review"
const val COLUMN_START_DATE = "start_date"
const val COLUMN_FINISH_DATE = "finish_date"
const val COLUMN_BUY_CHANNEL = "buy_channel"
const val COLUMN_SHELF_LOCATION = "shelf_location"
const val COLUMN_BINDING_TYPE = "binding_type"
const val COLUMN_BUY_PRICE = "buy_price"
const val COLUMN_SOURCE_TYPE = "source_type"
const val COLUMN_SOURCE_ID = "source_id"
const val COLUMN_REMOTE_RATING = "remote_rating"
const val COLUMN_DESCRIPTION = "description"

// 笔记表列 (notes)
const val COLUMN_CONTENT = "content"
const val COLUMN_NOTE_TYPE = "note_type"
const val COLUMN_PAGE = "page"
const val COLUMN_CHAPTER = "chapter"

// 阅读打卡表列 (reading_sessions)
const val COLUMN_DURATION_MINUTES = "duration_minutes"
const val COLUMN_PAGES_READ = "pages_read"
const val COLUMN_THOUGHT = "thought"

// 角色谱表列 (book_characters)
const val COLUMN_NAME = "name"
const val COLUMN_ROLE_TITLE = "role_title"
const val COLUMN_AVATAR_EMOJI = "avatar_emoji"
const val COLUMN_RELATIONSHIP = "relationship"

// 大纲脑图表列 (book_outlines)
const val COLUMN_CHAPTER_ORDER = "chapter_order"
const val COLUMN_SUMMARY = "summary"
const val COLUMN_KEY_TAKEAWAYS = "key_takeaways"

// 空间地标表列 (book_locations)
const val COLUMN_LOCATION_TYPE = "location_type"
const val COLUMN_SIGNIFICANCE = "significance"
const val COLUMN_COORDINATES = "coordinates"

// 心智六维表列 (book_mindprints)
const val COLUMN_DEPTH_SCORE = "depth_score"
const val COLUMN_ARTISTRY_SCORE = "artistry_score"
const val COLUMN_EMOTION_SCORE = "emotion_score"
const val COLUMN_LOGIC_SCORE = "logic_score"
const val COLUMN_DIFFICULTY_SCORE = "difficulty_score"
const val COLUMN_HEALING_SCORE = "healing_score"

// 音频曲目表列 (audio_tracks)
const val COLUMN_AUDIO_BOOK_ID = "book_id"
const val COLUMN_AUDIO_ORDER = "track_order"
const val COLUMN_AUDIO_TITLE = "title"
const val COLUMN_AUDIO_URI = "file_uri"
const val COLUMN_AUDIO_DURATION = "duration_ms"

// 策展最爱表列 (curator_favorites)
const val COLUMN_FAVORITE_BOOK_ID = "book_id"
const val COLUMN_FAVORITE_MEDIA_TYPE = "media_type"
const val COLUMN_FAVORITE_RANK_ORDER = "rank_order"
const val COLUMN_FAVORITE_CUSTOM_TAGLINE = "custom_tagline"

object DatabaseSchema {
    const val DATABASE_NAME = com.example.readtrace.data.DATABASE_NAME
    const val DATABASE_VERSION = com.example.readtrace.data.DATABASE_VERSION
    const val SEED_PREF = com.example.readtrace.data.SEED_PREF
    const val KEY_SEED_VERSION = com.example.readtrace.data.KEY_SEED_VERSION
    const val TABLE_BOOKS = com.example.readtrace.data.TABLE_BOOKS
    const val TABLE_NOTES = com.example.readtrace.data.TABLE_NOTES
    const val TABLE_READING_SESSIONS = com.example.readtrace.data.TABLE_READING_SESSIONS
    const val TABLE_BOOK_CHARACTERS = com.example.readtrace.data.TABLE_BOOK_CHARACTERS
    const val TABLE_BOOK_OUTLINES = com.example.readtrace.data.TABLE_BOOK_OUTLINES
    const val TABLE_BOOK_LOCATIONS = com.example.readtrace.data.TABLE_BOOK_LOCATIONS
    const val TABLE_BOOK_MINDPRINTS = com.example.readtrace.data.TABLE_BOOK_MINDPRINTS
    const val TABLE_AUDIO_TRACKS = com.example.readtrace.data.TABLE_AUDIO_TRACKS
    const val TABLE_FAVORITES = com.example.readtrace.data.TABLE_FAVORITES
}
