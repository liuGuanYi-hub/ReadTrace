import { FormEvent, useState } from "react";

import { BOOK_STATUSES, BOOK_STATUS_LABELS, type Book, type BookPayload, type BookStatus } from "../types/book";

interface BookFormProps {
  initialBook?: Book;
  submitLabel: string;
  isSaving: boolean;
  error?: string;
  onCancel: () => void;
  onSubmit: (payload: BookPayload) => Promise<void>;
}

interface BookFormState {
  title: string;
  author: string;
  cover_url: string;
  category: string;
  status: BookStatus;
  rating: string;
  tagsText: string;
  short_comment: string;
  review: string;
  start_date: string;
  finish_date: string;
}

function createInitialState(book?: Book): BookFormState {
  return {
    title: book?.title ?? "",
    author: book?.author ?? "",
    cover_url: book?.cover_url ?? "",
    category: book?.category ?? "",
    status: book?.status ?? "wishlist",
    rating: book?.rating ? String(book.rating) : "",
    tagsText: book?.tags.join("，") ?? "",
    short_comment: book?.short_comment ?? "",
    review: book?.review ?? "",
    start_date: book?.start_date ?? "",
    finish_date: book?.finish_date ?? "",
  };
}

function nullableText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function parseTags(value: string): string[] {
  const tags = value
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean);
  return Array.from(new Set(tags));
}

function parseRating(value: string): number | null {
  const normalized = value.trim();
  if (!normalized) {
    return null;
  }
  const rating = Number(normalized);
  if (!Number.isFinite(rating) || rating < 1 || rating > 10) {
    throw new Error("评分必须在 1 到 10 之间");
  }
  const decimal = normalized.split(".")[1];
  if (decimal && decimal.length > 1) {
    throw new Error("评分最多保留一位小数");
  }
  return rating;
}

export default function BookForm({
  initialBook,
  submitLabel,
  isSaving,
  error,
  onCancel,
  onSubmit,
}: BookFormProps) {
  const [values, setValues] = useState<BookFormState>(() => createInitialState(initialBook));
  const [formError, setFormError] = useState("");

  const updateField = <Field extends keyof BookFormState>(field: Field, value: BookFormState[Field]) => {
    setValues((current) => ({ ...current, [field]: value }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError("");

    const title = values.title.trim();
    if (!title) {
      setFormError("书名不能为空");
      return;
    }

    let rating: number | null;
    try {
      rating = parseRating(values.rating);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "评分格式不正确");
      return;
    }

    await onSubmit({
      title,
      author: nullableText(values.author),
      cover_url: nullableText(values.cover_url),
      category: nullableText(values.category),
      status: values.status,
      rating,
      tags: parseTags(values.tagsText),
      short_comment: nullableText(values.short_comment),
      review: nullableText(values.review),
      start_date: nullableText(values.start_date),
      finish_date: nullableText(values.finish_date),
    });
  };

  return (
    <form className="book-form" onSubmit={handleSubmit}>
      {(formError || error) && <div className="error-banner">{formError || error}</div>}

      <div className="form-grid">
        <label className="field field-wide">
          <span>书名</span>
          <input
            autoFocus
            required
            value={values.title}
            onChange={(event) => updateField("title", event.target.value)}
            placeholder="例如：伊豆的舞女"
          />
        </label>

        <label className="field">
          <span>作者</span>
          <input value={values.author} onChange={(event) => updateField("author", event.target.value)} />
        </label>

        <label className="field">
          <span>分类</span>
          <input value={values.category} onChange={(event) => updateField("category", event.target.value)} />
        </label>

        <label className="field">
          <span>阅读状态</span>
          <select value={values.status} onChange={(event) => updateField("status", event.target.value as BookStatus)}>
            {BOOK_STATUSES.map((status) => (
              <option key={status} value={status}>
                {BOOK_STATUS_LABELS[status]}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span>评分</span>
          <input
            inputMode="decimal"
            max="10"
            min="1"
            step="0.1"
            type="number"
            value={values.rating}
            onChange={(event) => updateField("rating", event.target.value)}
            placeholder="1-10"
          />
        </label>

        <label className="field field-wide">
          <span>封面地址</span>
          <input
            value={values.cover_url}
            onChange={(event) => updateField("cover_url", event.target.value)}
            placeholder="https://..."
          />
        </label>

        <label className="field field-wide">
          <span>标签</span>
          <input
            value={values.tagsText}
            onChange={(event) => updateField("tagsText", event.target.value)}
            placeholder="用逗号分隔，例如：文学，日本文学，短篇"
          />
        </label>

        <label className="field">
          <span>开始阅读日期</span>
          <input type="date" value={values.start_date} onChange={(event) => updateField("start_date", event.target.value)} />
        </label>

        <label className="field">
          <span>读完日期</span>
          <input type="date" value={values.finish_date} onChange={(event) => updateField("finish_date", event.target.value)} />
        </label>

        <label className="field field-wide">
          <span>简短评价</span>
          <input
            value={values.short_comment}
            onChange={(event) => updateField("short_comment", event.target.value)}
            placeholder="一句话记录当时的感受"
          />
        </label>

        <label className="field field-wide">
          <span>读后感</span>
          <textarea
            value={values.review}
            onChange={(event) => updateField("review", event.target.value)}
            rows={8}
          />
        </label>
      </div>

      <div className="form-actions">
        <button className="secondary-button" disabled={isSaving} onClick={onCancel} type="button">
          取消
        </button>
        <button className="primary-button" disabled={isSaving} type="submit">
          {isSaving ? "保存中" : submitLabel}
        </button>
      </div>
    </form>
  );
}
