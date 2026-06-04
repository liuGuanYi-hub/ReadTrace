export type BookStatus = "wishlist" | "reading" | "finished" | "paused" | "dropped";

export interface Book {
  id: number;
  title: string;
  author?: string | null;
  cover_url?: string | null;
  category?: string | null;
  status: BookStatus;
  rating?: number | null;
  tags: string[];
  short_comment?: string | null;
  review?: string | null;
  start_date?: string | null;
  finish_date?: string | null;
  created_at: string;
  updated_at: string;
  is_deleted: boolean;
  deleted_at?: string | null;
}

export interface BookPayload {
  title: string;
  author?: string | null;
  cover_url?: string | null;
  category?: string | null;
  status: BookStatus;
  rating?: number | null;
  tags: string[];
  short_comment?: string | null;
  review?: string | null;
  start_date?: string | null;
  finish_date?: string | null;
}

export interface BookFilters {
  status?: BookStatus;
  keyword?: string;
  tag?: string;
}

export const BOOK_STATUS_LABELS: Record<BookStatus, string> = {
  wishlist: "想读",
  reading: "在读",
  finished: "已读",
  paused: "暂停",
  dropped: "弃读",
};

export const BOOK_STATUSES: BookStatus[] = [
  "wishlist",
  "reading",
  "finished",
  "paused",
  "dropped",
];

