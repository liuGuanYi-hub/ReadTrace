import BookCard from "./BookCard";
import type { Book } from "../types/book";

interface BookListProps {
  books: Book[];
  onOpen: (book: Book) => void;
}

export default function BookList({ books, onOpen }: BookListProps) {
  if (books.length === 0) {
    return (
      <section className="empty-state">
        <h2>书架还是空的</h2>
        <p>添加第一本书，开始留下你的阅读痕迹。</p>
      </section>
    );
  }

  return (
    <section className="book-grid" aria-label="书籍列表">
      {books.map((book) => (
        <BookCard book={book} key={book.id} onOpen={onOpen} />
      ))}
    </section>
  );
}

