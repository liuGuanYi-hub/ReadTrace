import type { Book, BookFilters, BookPayload } from "../types/book";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000").replace(
  /\/$/,
  ""
);

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    let message = "请求失败";

    if (typeof body?.detail === "string") {
      message = body.detail;
    } else if (Array.isArray(body?.detail)) {
      message = body.detail.map((item: { msg?: string }) => item.msg).filter(Boolean).join("；");
    }

    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

function buildQuery(filters: BookFilters): string {
  const params = new URLSearchParams();
  if (filters.status) params.set("status", filters.status);
  if (filters.keyword?.trim()) params.set("keyword", filters.keyword.trim());
  if (filters.tag?.trim()) params.set("tag", filters.tag.trim());
  const query = params.toString();
  return query ? `?${query}` : "";
}

export function listBooks(filters: BookFilters = {}): Promise<Book[]> {
  return request<Book[]>(`/api/books${buildQuery(filters)}`);
}

export function getBook(bookId: number): Promise<Book> {
  return request<Book>(`/api/books/${bookId}`);
}

export function createBook(payload: BookPayload): Promise<Book> {
  return request<Book>("/api/books", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateBook(bookId: number, payload: BookPayload): Promise<Book> {
  return request<Book>(`/api/books/${bookId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function archiveBook(bookId: number): Promise<Book> {
  return request<Book>(`/api/books/${bookId}/archive`, {
    method: "PATCH",
  });
}

