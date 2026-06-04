import { useEffect, useState } from "react";

import { getBook, updateBook } from "../api/bookApi";
import BookForm from "../components/BookForm";
import type { Book, BookPayload } from "../types/book";

interface EditBookPageProps {
  bookId: number;
  navigate: (path: string) => void;
}

export default function EditBookPage({ bookId, navigate }: EditBookPageProps) {
  const [book, setBook] = useState<Book | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let ignore = false;
    async function loadBook() {
      setIsLoading(true);
      setError("");
      try {
        const data = await getBook(bookId);
        if (!ignore) setBook(data);
      } catch (err) {
        if (!ignore) setError(err instanceof Error ? err.message : "无法加载书籍详情");
      } finally {
        if (!ignore) setIsLoading(false);
      }
    }

    void loadBook();
    return () => {
      ignore = true;
    };
  }, [bookId]);

  const handleSubmit = async (payload: BookPayload) => {
    setIsSaving(true);
    setError("");
    try {
      const updated = await updateBook(bookId, payload);
      navigate(`/books/${updated.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="page narrow-page">
      <header className="page-header">
        <button className="text-button" onClick={() => navigate(`/books/${bookId}`)} type="button">
          返回详情
        </button>
        <h1>编辑书籍</h1>
      </header>

      {isLoading ? <section className="loading-state">正在读取书籍</section> : null}
      {!isLoading && !book && error ? <div className="error-banner">{error}</div> : null}
      {book ? (
        <BookForm
          error={error}
          initialBook={book}
          isSaving={isSaving}
          submitLabel="保存修改"
          onCancel={() => navigate(`/books/${bookId}`)}
          onSubmit={handleSubmit}
        />
      ) : null}
    </div>
  );
}

