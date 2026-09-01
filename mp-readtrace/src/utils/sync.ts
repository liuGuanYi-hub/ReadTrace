/**
 * 🛡️ Local-First WebDAV 增量同步（小程序端）
 *
 * 与 Android WebDavSyncEngine 完全同构：
 * - 远端路径：/readtrace/backup.json + /readtrace/manifest.json
 * - 备份格式：BackupHelper Schema v4（works[] 内含 notes/sessions/… 高阶节点）
 * - 合并策略：内容级去重（作品按标题+创作者、笔记按内容）后拉推收敛
 *
 * 微信小程序无标准 DOMParser/fetch，统一走 wx.request；密码仅存本地 storage。
 */
import type { Book, Note, Mindprint } from './models';

export interface WebDavConfig {
  serverUrl: string;   // 例如 https://dav.jianguoyun.com/dav/
  username: string;
  password: string;    // 坚果云请使用「应用密码」
}

export interface SyncResult {
  success: boolean;
  pulledWorks: number;
  pushedWorks: number;
  message: string;
}

const REMOTE_BACKUP = 'readtrace/backup.json';
const REMOTE_MANIFEST = 'readtrace/manifest.json';

const STORAGE_KEYS = {
  config: 'rt_webdav_config',
  works: 'rt_local_works',
  notes: 'rt_local_notes',
  mindprints: 'rt_local_mindprints',
};

// ---------------------------------------------------------------- 本地存储（Local-First）

export function loadLocalWorks(): Book[] {
  return uni.getStorageSync(STORAGE_KEYS.works) || [];
}

export function saveLocalWorks(works: Book[]) {
  uni.setStorageSync(STORAGE_KEYS.works, works);
}

export function loadLocalNotes(): Note[] {
  return uni.getStorageSync(STORAGE_KEYS.notes) || [];
}

export function loadLocalMindprints(): Mindprint[] {
  return uni.getStorageSync(STORAGE_KEYS.mindprints) || [];
}

// ---------------------------------------------------------------- WebDAV 请求层

function request(method: 'GET' | 'PUT' | 'MKCOL', path: string, config: WebDavConfig, data?: string): Promise<{ status: number; body: string | null }> {
  const url = config.serverUrl.replace(/\/+$/, '') + '/' + path;
  const header: Record<string, string> = {
    Authorization: 'Basic ' + uni.arrayBufferToBase64(
      new Uint8Array(Array.from(config.username + ':' + config.password).map((c) => c.charCodeAt(0))).buffer,
    ),
  };
  if (data !== undefined) header['Content-Type'] = 'application/json; charset=utf-8';

  return new Promise((resolve) => {
    uni.request({
      url,
      method: method as any,
      header,
      data: data as any,
      success: (res) => resolve({ status: Number(res.statusCode || 0), body: typeof res.data === 'string' ? res.data : JSON.stringify(res.data) }),
      fail: (err) => resolve({ status: -1, body: null, ...({ msg: err.errMsg } as any) }),
    });
  });
}

export function loadConfig(): WebDavConfig | null {
  return uni.getStorageSync(STORAGE_KEYS.config) || null;
}

export function saveConfig(config: WebDavConfig) {
  uni.setStorageSync(STORAGE_KEYS.config, config);
}

// ---------------------------------------------------------------- 增量同步

/** 双向增量同步：拉远端 → 合并去重 → 推回全量。 Local-First：离线不受任何影响。 */
export async function performSync(): Promise<SyncResult> {
  const config = loadConfig();
  if (!config) return { success: false, pulledWorks: 0, pushedWorks: 0, message: '尚未配置 WebDAV 服务器' };

  // 1. 拉取远端备份（404 = 首次）
  const remote = await request('GET', REMOTE_BACKUP, config);
  if (remote.status !== -1 && remote.status !== 200 && remote.status !== 404) {
    return { success: false, pulledWorks: 0, pushedWorks: 0, message: `远端不可达: HTTP ${remote.status}` };
  }
  let pulledWorks = 0;
  const localWorks = loadLocalWorks();
  const localNotes = loadLocalNotes();

  if (remote.status === 200 && remote.body) {
    try {
      const root = JSON.parse(remote.body);
      const works: Book[] = root.works || [];
      const keyOf = (b: Book) => `${b.title.trim()}::${(b.author || '').trim()}`;
      const localKeys = new Set(localWorks.map(keyOf));
      const localNoteKeys = new Set(localNotes.map((n) => n.content.trim()));
      let newNotes = 0;

      works.forEach((w) => {
        if (!localKeys.has(keyOf(w.book ?? w))) {
          localWorks.push(w.book ?? w);
          pulledWorks++;
        }
        const notes: Note[] = (w as any).notes || [];
        notes.forEach((n) => {
          if (n.content && !localNoteKeys.has(n.content.trim())) {
            localNotes.push({ ...n, id: 0, bookId: 0 });
            newNotes++;
          }
        });
      });
      saveLocalWorks(localWorks);
      uni.setStorageSync(STORAGE_KEYS.notes, localNotes);
      void newNotes;
    } catch {
      return { success: false, pulledWorks: 0, pushedWorks: 0, message: '远端备份解析失败' };
    }
  }

  // 2. 推送全量（Schema v4：works[] 每项含 notes/mindprint 等节点）
  const mindprints = loadLocalMindprints();
  const payload = {
    app: 'ReadTrace',
    version: '3.0',
    schemaVersion: 4,
    exportedAt: new Date().toISOString(),
    worksCount: localWorks.length,
    works: localWorks.map((book) => ({
      ...book,
      tags: book.tags || [],
      notes: localNotes.filter((n) => n.bookId === book.id),
      mindprint: mindprints.find((m) => m.bookId === book.id) || undefined,
    })),
  };

  await request('MKCOL', 'readtrace/', config);
  const put = await request('PUT', REMOTE_BACKUP, config, JSON.stringify(payload));
  if (put.status < 200 || put.status > 299) {
    return { success: false, pulledWorks, pushedWorks: 0, message: `上传失败: HTTP ${put.status}` };
  }
  await request('PUT', REMOTE_MANIFEST, config, JSON.stringify({ app: 'ReadTrace', device: 'mp-weixin', lastSyncAt: new Date().toISOString(), worksCount: localWorks.length }));

  return {
    success: true,
    pulledWorks,
    pushedWorks: localWorks.length,
    message: `同步完成：拉入 ${pulledWorks} 部，云端共 ${localWorks.length} 部`,
  };
}
