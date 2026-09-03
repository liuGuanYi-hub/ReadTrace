package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.MediaType
import com.example.readtrace.ui.dialog.WorkPickerDialog
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import java.io.File
import java.io.FileOutputStream

/**
 * 🏆 我的最爱 (CuratorFavoritesActivity)
 * 跨媒介分类收藏展厅：按 5 大类独立存放，支持已有作品导入、金标序号、为什么喜欢推荐语与长图导出。
 */
class CuratorFavoritesActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private var currentMediaType: MediaType = MediaType.BOOK

    private lateinit var tabFavBook: TextView
    private lateinit var tabFavAnime: TextView
    private lateinit var tabFavMovie: TextView
    private lateinit var tabFavGame: TextView
    private lateinit var tabFavMusic: TextView

    private lateinit var favRecyclerView: RecyclerView
    private lateinit var favEmptyContainer: View
    private lateinit var favEmptyTitle: TextView
    private lateinit var btnEmptyAddWork: Button
    private lateinit var btnBottomAddWork: Button

    private lateinit var adapter: FavoritesAdapter
    private var currentFavorites: List<BookDatabaseHelper.CuratorFavoriteItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_curator_favorites)

        // 项目标准状态栏补偿：edge-to-edge 下内容不再顶格进状态栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.favoritesRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)

        val targetMediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)?.let {
            runCatching { MediaType.valueOf(it.uppercase()) }.getOrNull()
        } ?: MediaType.BOOK
        currentMediaType = targetMediaType

        bindViews()
        setupListeners()
        selectCategoryTab(currentMediaType)
    }

    private fun bindViews() {
        tabFavBook = findViewById(R.id.tabFavBook)
        tabFavAnime = findViewById(R.id.tabFavAnime)
        tabFavMovie = findViewById(R.id.tabFavMovie)
        tabFavGame = findViewById(R.id.tabFavGame)
        tabFavMusic = findViewById(R.id.tabFavMusic)

        favRecyclerView = findViewById(R.id.favRecyclerView)
        favEmptyContainer = findViewById(R.id.favEmptyContainer)
        favEmptyTitle = findViewById(R.id.favEmptyTitle)
        btnEmptyAddWork = findViewById(R.id.btnEmptyAddWork)
        btnBottomAddWork = findViewById(R.id.btnBottomAddWork)

        adapter = FavoritesAdapter(
            items = currentFavorites,
            onItemClicked = { item ->
                startActivity(BookDetailActivity.createIntent(this, item.book.id))
            },
            onRemoveClicked = { item ->
                confirmRemoveFavorite(item)
            }
        )

        favRecyclerView.layoutManager = LinearLayoutManager(this)
        favRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnFavoritesBack).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            finish()
        }

        tabFavBook.setOnClickListener { switchCategory(MediaType.BOOK) }
        tabFavAnime.setOnClickListener { switchCategory(MediaType.ANIME) }
        tabFavMovie.setOnClickListener { switchCategory(MediaType.MOVIE) }
        tabFavGame.setOnClickListener { switchCategory(MediaType.GAME) }
        tabFavMusic.setOnClickListener { switchCategory(MediaType.MUSIC) }

        btnEmptyAddWork.setOnClickListener { openWorkPicker() }
        btnBottomAddWork.setOnClickListener { openWorkPicker() }

        findViewById<View>(R.id.btnExportFavoritesPoster).setOnClickListener {
            exportFavoritesPoster()
        }
    }

    private fun switchCategory(mediaType: MediaType) {
        if (currentMediaType == mediaType) return
        HapticFeedbackEngine.lightClick(this)
        currentMediaType = mediaType
        selectCategoryTab(mediaType)
    }

    private fun selectCategoryTab(mediaType: MediaType) {
        val tabs = listOf(
            MediaType.BOOK to tabFavBook,
            MediaType.ANIME to tabFavAnime,
            MediaType.MOVIE to tabFavMovie,
            MediaType.GAME to tabFavGame,
            MediaType.MUSIC to tabFavMusic,
        )

        tabs.forEach { (type, textView) ->
            if (type == mediaType) {
                textView.setBackgroundResource(R.drawable.bg_status_chip_selected)
                textView.setTextColor(Color.WHITE)
            } else {
                textView.setBackgroundResource(R.drawable.bg_status_chip)
                textView.setTextColor(getColor(R.color.readtrace_ink))
            }
        }

        loadFavorites()
    }

    private fun loadFavorites() {
        currentFavorites = databaseHelper.getFavoritesByMediaType(currentMediaType)
        adapter.updateItems(currentFavorites)

        if (currentFavorites.isEmpty()) {
            favEmptyContainer.visibility = View.VISIBLE
            favRecyclerView.visibility = View.GONE
            btnBottomAddWork.visibility = View.GONE
            favEmptyTitle.text = "还没有添加最爱的${currentMediaType.displayName}"
        } else {
            favEmptyContainer.visibility = View.GONE
            favRecyclerView.visibility = View.VISIBLE
            btnBottomAddWork.visibility = View.VISIBLE
        }
    }

    private fun openWorkPicker() {
        HapticFeedbackEngine.lightClick(this)
        val alreadyIds = currentFavorites.map { it.book.id }.toSet()
        WorkPickerDialog.show(
            activity = this,
            mediaType = currentMediaType,
            alreadyFavoriteBookIds = alreadyIds,
        ) { selectedBooks ->
            val currentCount = currentFavorites.size
            selectedBooks.forEachIndexed { index, book ->
                databaseHelper.addFavorite(
                    bookId = book.id,
                    mediaType = currentMediaType,
                    rankOrder = currentCount + index + 1,
                    tagline = null,
                )
            }
            HapticFeedbackEngine.stampImpact(this)
            Toast.makeText(this, "✨ 已添加 ${selectedBooks.size} 部作品到最爱！", Toast.LENGTH_SHORT).show()
            loadFavorites()
        }
    }

    private fun confirmRemoveFavorite(item: BookDatabaseHelper.CuratorFavoriteItem) {
        com.example.readtrace.util.ElegantConfirmDialog.show(
            activity = this,
            title = "从最爱移除",
            message = "确定要将《${item.book.title}》从最爱列表中移除吗？",
            confirmText = "移除",
            cancelText = "保留",
            isDanger = true,
            onConfirm = {
                databaseHelper.removeFavorite(item.book.id)
                HapticFeedbackEngine.lightClick(this)
                Toast.makeText(this, "已移除《${item.book.title}》", Toast.LENGTH_SHORT).show()
                loadFavorites()
            }
        )
    }

    private fun exportFavoritesPoster() {
        if (currentFavorites.isEmpty()) {
            Toast.makeText(this, "当前分类暂无最爱作品，先添加几部吧", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            HapticFeedbackEngine.stampImpact(this)
            val bitmap = createFavoritesBitmap()
            val file = File(cacheDir, "readtrace_favorites_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "✨ 我的${currentMediaType.displayName}心选最爱 · 来自《阅痕 ReadTrace》")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享我的最爱"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "导出长图失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFavoritesBitmap(): Bitmap {
        favRecyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(favRecyclerView.width.coerceAtLeast(1080), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val width = favRecyclerView.measuredWidth.coerceAtLeast(1080)
        val height = favRecyclerView.measuredHeight.coerceAtLeast(1920)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(getColor(R.color.readtrace_background))
        favRecyclerView.layout(0, 0, width, height)
        favRecyclerView.draw(canvas)
        return bitmap
    }

    companion object {
        private const val EXTRA_MEDIA_TYPE = "extra_media_type"

        fun createIntent(context: Context, mediaType: MediaType = MediaType.BOOK): Intent {
            return Intent(context, CuratorFavoritesActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_TYPE, mediaType.name)
            }
        }
    }

    private class FavoritesAdapter(
        private var items: List<BookDatabaseHelper.CuratorFavoriteItem>,
        private val onItemClicked: (BookDatabaseHelper.CuratorFavoriteItem) -> Unit,
        private val onRemoveClicked: (BookDatabaseHelper.CuratorFavoriteItem) -> Unit,
    ) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

        fun updateItems(newItems: List<BookDatabaseHelper.CuratorFavoriteItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_curator_favorite, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val book = item.book

            holder.rankBadge.text = "No.${position + 1} 最爱"
            holder.title.text = book.title

            val authorCategory = buildString {
                if (!book.author.isNullOrBlank()) append(book.author)
                if (!book.category.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(book.category)
                }
            }
            holder.author.text = authorCategory.ifBlank { "未知创作者" }

            val ratingVal = book.rating
            if (ratingVal != null && ratingVal > 0) {
                holder.rating.visibility = View.VISIBLE
                holder.rating.text = String.format("★ %.1f 分", ratingVal)
            } else {
                holder.rating.visibility = View.GONE
            }

            holder.status.text = book.status.displayName
            CoverImageHelper.loadCover(holder.cover, book.coverUrl)

            holder.btnRemove.setOnClickListener {
                onRemoveClicked(item)
            }

            holder.itemView.setOnClickListener {
                onItemClicked(item)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val rankBadge: TextView = view.findViewById(R.id.favRankBadge)
            val btnRemove: TextView = view.findViewById(R.id.btnRemoveFavorite)
            val cover: ImageView = view.findViewById(R.id.favCover)
            val title: TextView = view.findViewById(R.id.favTitle)
            val author: TextView = view.findViewById(R.id.favAuthor)
            val rating: TextView = view.findViewById(R.id.favRating)
            val status: TextView = view.findViewById(R.id.favStatus)
        }
    }
}
