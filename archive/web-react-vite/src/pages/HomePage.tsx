import { useCallback, useEffect, useMemo, useState } from "react";

import { listBooks } from "../api/bookApi";
import BookList from "../components/BookList";
import StatusFilter from "../components/StatusFilter";
import TagList from "../components/TagList";
import type { Book, BookStatus } from "../types/book";

interface HomePageProps {
  navigate: (path: string) => void;
}

export default function HomePage({ navigate }: HomePageProps) {
  const [books, setBooks] = useState<Book[]>([]);
  const [status, setStatus] = useState<BookStatus | undefined>();
  const [keyword, setKeyword] = useState("");
  const [tag, setTag] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const loadBooks = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      const data = await listBooks({ status, keyword, tag });
      setBooks(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "无法加载书籍列表");
    } finally {
      setIsLoading(false);
    }
  }, [keyword, status, tag]);

  useEffect(() => {
    void loadBooks();
  }, [loadBooks]);

  const availableTags = useMemo(() => {
    return Array.from(new Set(books.flatMap((book) => book.tags))).sort((left, right) => left.localeCompare(right, "zh-CN"));
  }, [books]);

  return (
    <div className="page">
      <header className="topbar">
        <button className="brand-button" onClick={() => navigate("/")} type="button">
          <span className="brand-title">阅痕</span>
          <span className="brand-subtitle">ReadTrace</span>
        </button>
        <div className="search-box">
          <input
            aria-label="搜索书名或作者"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索书名或作者"
          />
        </div>
        <button className="primary-button" onClick={() => navigate("/books/new")} type="button">
          + 添加书籍
        </button>
      </header>

      <div className="bookshelf-layout">
        <aside className="sidebar">
          <h2>阅读状态</h2>
          <StatusFilter value={status} onChange={setStatus} />
        </aside>

        <section className="content-area">
          <div className="content-header">
            <div>
              <h1>我的书架</h1>
              <p>{isLoading ? "正在读取书籍" : `${books.length} 本未归档书籍`}</p>
            </div>
            {tag ? (
              <button className="secondary-button" onClick={() => setTag("")} type="button">
                清除标签：{tag}
              </button>
            ) : null}
          </div>

          <TagList activeTag={tag} tags={availableTags} onSelect={setTag} />

          {error ? <div className="error-banner">{error}</div> : null}
          {isLoading ? (
            <section className="loading-state">正在整理书架</section>
          ) : (
            <BookList books={books} onOpen={(book) => navigate(`/books/${book.id}`)} />
          )}
        </section>
      </div>
    </div>
  );
}

