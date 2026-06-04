import { useEffect, useState } from "react";

import { archiveBook, getBook } from "../api/bookApi";
import TagList from "../components/TagList";
import { BOOK_STATUS_LABELS, type Book } from "../types/book";

interface BookDetailPageProps {
  bookId: number;
  navigate: (path: string) => void;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function MetadataRow({ label, value }: { label: string; value?: string | number | null }) {
  if (value === undefined || value === null || value === "") {
    return null;
  }
  return (
    <div className="metadata-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export default function BookDetailPage({ bookId, navigate }: BookDetailPageProps) {
  const [book, setBook] = useState<Book | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isArchiving, setIsArchiving] = useState(false);
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

  const handleArchive = async () => {
    if (!book) return;
    const confirmed = window.confirm("归档后不会物理删除数据，默认书架将不再显示这本书。");
    if (!confirmed) return;

    setIsArchiving(true);
    setError("");
    try {
      await archiveBook(book.id);
      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "归档失败");
    } finally {
      setIsArchiving(false);
    }
  };

  return (
    <div className="page narrow-page">
      <header className="page-header">
        <button className="text-button" onClick={() => navigate("/")} type="button">
          返回书架
        </button>
        {book ? (
          <div className="page-actions">
            <button className="secondary-button" onClick={() => navigate(`/books/${book.id}/edit`)} type="button">
              编辑
            </button>
            <button className="danger-button" disabled={isArchiving} onClick={handleArchive} type="button">
              {isArchiving ? "归档中" : "归档"}
            </button>
          </div>
        ) : null}
      </header>

      {error ? <div className="error-banner">{error}</div> : null}
      {isLoading ? <section className="loading-state">正在读取书籍</section> : null}

      {book ? (
        <article className="detail-layout">
          <aside className="detail-cover-panel">
            {book.cover_url ? (
              <img alt={`${book.title} 封面`} className="detail-cover" src={book.cover_url} />
            ) : (
              <div className="detail-cover detail-cover-empty">{book.title.slice(0, 2)}</div>
            )}
          </aside>

          <section className="detail-main">
            <span className="status-pill">{BOOK_STATUS_LABELS[book.status]}</span>
            <h1>{book.title}</h1>
            {book.author ? <p className="detail-author">{book.author}</p> : null}
            <TagList tags={book.tags} />

            <div className="metadata-grid">
              <MetadataRow label="分类" value={book.category} />
              <MetadataRow label="评分" value={book.rating ? book.rating.toFixed(1) : null} />
              <MetadataRow label="开始阅读" value={book.start_date} />
              <MetadataRow label="读完日期" value={book.finish_date} />
              <MetadataRow label="创建时间" value={formatDateTime(book.created_at)} />
              <MetadataRow label="更新时间" value={formatDateTime(book.updated_at)} />
            </div>

            {book.short_comment ? (
              <section className="prose-section">
                <h2>简短评价</h2>
                <p>{book.short_comment}</p>
              </section>
            ) : null}

            {book.review ? (
              <section className="prose-section">
                <h2>读后感</h2>
                <p>{book.review}</p>
              </section>
            ) : null}
          </section>
        </article>
      ) : null}
    </div>
  );
}

