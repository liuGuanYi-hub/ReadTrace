import TagList from "./TagList";
import { BOOK_STATUS_LABELS, type Book } from "../types/book";

interface BookCardProps {
  book: Book;
  onOpen: (book: Book) => void;
}

export default function BookCard({ book, onOpen }: BookCardProps) {
  return (
    <article className="book-card">
      <button className="book-card-cover-button" onClick={() => onOpen(book)} type="button">
        {book.cover_url ? (
          <img alt={`${book.title} 封面`} className="book-card-cover" src={book.cover_url} />
        ) : (
          <div className="book-card-cover book-card-cover-empty">
            <span>{book.title.slice(0, 2)}</span>
          </div>
        )}
      </button>
      <div className="book-card-body">
        <div className="book-card-top">
          <span className="status-pill">{BOOK_STATUS_LABELS[book.status]}</span>
          {book.rating ? <span className="rating-pill">{book.rating.toFixed(1)}</span> : null}
        </div>
        <button className="book-title-button" onClick={() => onOpen(book)} type="button">
          {book.title}
        </button>
        {book.author ? <p className="book-author">{book.author}</p> : null}
        <TagList compact tags={book.tags} />
        {book.short_comment ? <p className="book-comment">{book.short_comment}</p> : null}
      </div>
    </article>
  );
}

