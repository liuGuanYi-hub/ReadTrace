package com.example.readtrace.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.R
import com.example.readtrace.model.Book
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class FlipNotesAdapter(
    private val book: Book,
    private val notes: List<Note>,
) : RecyclerView.Adapter<FlipNotesAdapter.FlipNoteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlipNoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flip_note_page, parent, false)
        return FlipNoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FlipNoteViewHolder, position: Int) {
        holder.bind(book, notes[position])
    }

    override fun getItemCount(): Int = notes.size

    class FlipNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeBadge: TextView = itemView.findViewById(R.id.flipNoteTypeBadge)
        private val positionMeta: TextView = itemView.findViewById(R.id.flipNotePosition)
        private val contentText: TextView = itemView.findViewById(R.id.flipNoteContent)
        private val bookRefText: TextView = itemView.findViewById(R.id.flipNoteBookRef)
        private val timeText: TextView = itemView.findViewById(R.id.flipNoteTime)

        fun bind(book: Book, note: Note) {
            val context = itemView.context
            typeBadge.text = note.noteType.displayName
            typeBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (note.noteType == NoteType.QUOTE) R.color.readtrace_accent else R.color.readtrace_muted,
                ),
            )

            // 页码与章节组合
            val metaList = buildList {
                note.page?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(context.getString(R.string.note_page_format, it))
                }
                note.chapter?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
            if (metaList.isEmpty()) {
                positionMeta.visibility = View.GONE
            } else {
                positionMeta.visibility = View.VISIBLE
                positionMeta.text = metaList.joinToString(" · ")
            }

            contentText.text = note.content

            // 书籍出处
            val authorPart = book.author?.trim()?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""
            bookRefText.text = "《${book.title}》$authorPart"

            // 格式化时间
            timeText.text = formatTimestamp(note.createdAt)
        }

        private fun formatTimestamp(isoString: String): String =
            runCatching {
                OffsetDateTime.parse(isoString).format(DISPLAY_TIME_FORMAT)
            }.getOrDefault(isoString)

        companion object {
            private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        }
    }
}
