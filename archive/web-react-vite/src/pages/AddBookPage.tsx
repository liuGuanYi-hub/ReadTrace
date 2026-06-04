import { useState } from "react";

import { createBook } from "../api/bookApi";
import BookForm from "../components/BookForm";
import type { BookPayload } from "../types/book";

interface AddBookPageProps {
  navigate: (path: string) => void;
}

export default function AddBookPage({ navigate }: AddBookPageProps) {
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (payload: BookPayload) => {
    setIsSaving(true);
    setError("");
    try {
      const book = await createBook(payload);
      navigate(`/books/${book.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="page narrow-page">
      <header className="page-header">
        <button className="text-button" onClick={() => navigate("/")} type="button">
          返回书架
        </button>
        <h1>添加书籍</h1>
      </header>
      <BookForm
        error={error}
        isSaving={isSaving}
        submitLabel="保存书籍"
        onCancel={() => navigate("/")}
        onSubmit={handleSubmit}
      />
    </div>
  );
}

