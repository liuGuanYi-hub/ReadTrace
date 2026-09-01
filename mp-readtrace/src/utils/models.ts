/**
 * 与 Android 端 100% 同构的数据模型（ReadTrace Kotlin model 镜像）
 * 保持字段名与 JSON 备份 Schema v4 完全一致，确保 WebDAV 双向同步无损。
 */

export type BookStatus =
  | 'wishlist'
  | 'reading'
  | 'finished'
  | 'paused'
  | 'dropped';

export type MediaType = 'book' | 'anime' | 'movie' | 'game' | 'music';

export interface Book {
  id: number;
  title: string;
  author: string | null;
  coverUrl: string | null;
  category: string | null;
  status: BookStatus;
  mediaType: MediaType;
  rating: number | null;          // 1.0 ~ 10.0
  tags: string[];
  shortComment: string | null;
  review: string | null;
  startDate: string | null;       // YYYY-MM-DD
  finishDate: string | null;      // YYYY-MM-DD
  createdAt: string;
  updatedAt: string;
  sourceType: string | null;      // douban / bangumi / steam
  sourceId: string | null;
  remoteRating: number | null;
  description: string | null;
}

export interface Note {
  id: number;
  bookId: number;
  content: string;
  noteType: 'note' | 'quote';
  page: string | null;
  chapter: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Mindprint {
  bookId: number;
  depthScore: number;
  artistryScore: number;
  emotionScore: number;
  logicScore: number;
  difficultyScore: number;
  healingScore: number;
  updatedAt: string;
}

export const MEDIA_LABEL: Record<MediaType, { emoji: string; name: string }> = {
  book: { emoji: '📖', name: '书籍' },
  anime: { emoji: '📺', name: '番剧' },
  movie: { emoji: '🎬', name: '电影' },
  game: { emoji: '🎮', name: '游戏' },
  music: { emoji: '🎵', name: '音乐' },
};

export const STATUS_LABEL: Record<BookStatus, string> = {
  wishlist: '想看',
  reading: '在读',
  finished: '已读',
  paused: '暂停',
  dropped: '弃读',
};

/** 六维心智：主评分等比智能推导（与 Android SmartAssistedHelper 同构，此处取语境中性版） */
export function deriveMindprint(rating: number, bookId = 0): Mindprint {
  const r = Math.min(10, Math.max(1, rating));
  const clamp = (v: number) => Math.round(Math.min(10, Math.max(1, v)) * 10) / 10;
  return {
    bookId,
    depthScore: clamp(r * 0.9 + 0.4),
    artistryScore: clamp(r * 0.9 + 0.4),
    emotionScore: clamp(r * 0.9 + 0.4),
    logicScore: clamp(r * 0.9 + 0.4),
    difficultyScore: clamp(5.0 + (r - 5.0) * 0.3),
    healingScore: clamp(r * 0.8 + 1.0),
    updatedAt: new Date().toISOString(),
  };
}
