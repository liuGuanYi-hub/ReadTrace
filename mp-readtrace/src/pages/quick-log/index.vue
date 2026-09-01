<template>
  <view class="page">
    <text class="title">⚡ 极速速记</text>
    <text class="subtitle">一句话结构化：读完 三体 9分 #科幻</text>

    <input class="input" v-model="raw" placeholder="状态 + 书名 + 评分 + #标签" placeholder-class="ph" />

    <!-- 解析预览 -->
    <view v-if="parsed" class="preview">
      <text class="p-title">《{{ parsed.title }}》</text>
      <text class="p-meta">{{ statusLabel }}{{ parsed.rating ? ' · ⭐' + parsed.rating : '' }}{{ tagLabel }}</text>
    </view>

    <!-- 五态大按键 -->
    <view class="status-row">
      <view
        v-for="(label, key) in STATUS_LABEL"
        :key="key"
        class="status-btn"
        :class="{ active: pickedStatus === key }"
        @tap="pickedStatus = key as BookStatus"
      >{{ label }}</view>
    </view>

    <!-- 保存 & 同步 -->
    <view class="save" @tap="save">⚡ 秒存入库</view>
    <view class="sync" @tap="syncToCloud">🛡️ WebDAV 同步至云端</view>
    <text class="sync-msg">{{ syncMessage }}</text>

    <DioramaCard v-if="lastBook" :book="lastBook" style="margin-top: 30rpx" />
    <VinylPlayer v-if="lastBook" :book="lastBook" style="margin-top: 30rpx" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { Book, BookStatus } from '../../utils/models';
import { STATUS_LABEL, deriveMindprint } from '../../utils/models';
import { loadLocalWorks, loadLocalMindprints, saveLocalWorks, performSync } from '../../utils/sync';

const raw = ref('');
const pickedStatus = ref<BookStatus>('wishlist');
const lastBook = ref<Book | null>(null);
const syncMessage = ref('');

/** 与 Android NaturalQuickAddParser 同构的轻量分词 */
const parsed = computed(() => {
  const text = raw.value.trim();
  if (!text) return null;
  const tags = Array.from(text.matchAll(/#([^\s#，,。]+)/g)).map((m) => m[1]);
  let rest = text.replace(/#[^\s#，,。]+/g, ' ');
  const fen = rest.match(/(\d{1,2}(?:\.\d)?)\s*分/);
  const xing = rest.match(/(\d{1,2})\s*星/);
  let rating: number | null = fen ? Number(fen[1]) : xing ? Number(xing[1]) * 2 : null;
  if (rating != null) rating = Math.min(10, Math.max(1, rating));
  rest = rest.replace(/(\d{1,2}(?:\.\d)?)\s*分|(\d{1,2})\s*星/, ' ');
  const statusMap: Array<[RegExp, BookStatus]> = [
    [/想看|想读/, 'wishlist'], [/在看|在读/, 'reading'], [/读完|看完|已读|已看/, 'finished'],
    [/暂停/, 'paused'], [/弃读|弃坑/, 'dropped'],
  ];
  let status: BookStatus | null = null;
  for (const [re, s] of statusMap) {
    if (re.test(rest)) { status = s; rest = rest.replace(re, ' '); break; }
  }
  const title = rest.replace(/《|》/g, ' ').split(/\s+/).find((t) => t.length > 0) || null;
  return title ? { title, status, rating, tags } : null;
});

const statusLabel = computed(() => STATUS_LABEL[pickedStatus.value]);
const tagLabel = computed(() => (parsed.value?.tags.length ? ' · ' + parsed.value!.tags.map((t) => '#' + t).join(' ') : ''));

function save() {
  const p = parsed.value;
  if (!p) {
    uni.showToast({ title: '先输入一句话速记', icon: 'none' });
    return;
  }
  const now = new Date().toISOString();
  const works = loadLocalWorks();
  const book: Book = {
    id: Date.now(), // 小程序本地临时主键，App 端合入时按标题+创作者去重
    title: p.title,
    author: null,
    coverUrl: null,
    category: p.tags[0] || null,
    status: pickedStatus.value,
    mediaType: 'book',
    rating: p.rating,
    tags: p.tags,
    shortComment: null,
    review: null,
    startDate: pickedStatus.value === 'reading' ? now.slice(0, 10) : null,
    finishDate: pickedStatus.value === 'finished' ? now.slice(0, 10) : null,
    createdAt: now,
    updatedAt: now,
    sourceType: null,
    sourceId: null,
    remoteRating: null,
    description: null,
  };
  works.push(book);
  saveLocalWorks(works);
  // 同步写入智能推导的心智模型（Schema v4 节点）
  const mps = loadLocalMindprints();
  mps.push(deriveMindprint(p.rating ?? 8.0, book.id));
  uni.setStorageSync('rt_local_mindprints', mps);

  lastBook.value = book;
  raw.value = '';
  uni.showToast({ title: '⚡ 已入库', icon: 'none' });
}

async function syncToCloud() {
  uni.showLoading({ title: '同步中…' });
  const result = await performSync();
  uni.hideLoading();
  syncMessage.value = result.message;
  uni.showToast({ title: result.success ? '✅ ' + result.message : '❌ ' + result.message, icon: 'none' });
}
</script>

<style>
.page { min-height: 100vh; background: #05070b; padding: 40rpx; display: flex; flex-direction: column; }
.title { color: #f2efe6; font-size: 40rpx; font-weight: bold; }
.subtitle { color: #667; font-size: 22rpx; margin-top: 8rpx; margin-bottom: 30rpx; }
.input {
  background: rgba(255, 255, 255, 0.06); border-radius: 16rpx; padding: 24rpx;
  color: #fff; font-size: 28rpx;
}
.ph { color: #556; }
.preview { margin-top: 24rpx; background: #3a6348; border-radius: 16rpx; padding: 24rpx; }
.p-title { display: block; color: #fff; font-size: 30rpx; font-weight: bold; }
.p-meta { display: block; color: #d3e8d8; font-size: 22rpx; margin-top: 6rpx; }
.status-row { display: flex; margin-top: 30rpx; gap: 10rpx; }
.status-btn {
  flex: 1; text-align: center; padding: 26rpx 0; border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08); color: #ccd; font-size: 24rpx;
}
.status-btn.active { background: rgba(255, 231, 0, 0.9); color: #2b1a0e; font-weight: bold; }
.save {
  margin-top: 36rpx; text-align: center; padding: 26rpx; border-radius: 44rpx;
  background: #ffe700; color: #2b1a0e; font-size: 30rpx; font-weight: bold;
}
.sync {
  margin-top: 20rpx; text-align: center; padding: 22rpx; border-radius: 44rpx;
  background: rgba(255, 255, 255, 0.08); color: #ccd; font-size: 26rpx;
}
.sync-msg { text-align: center; color: #556; font-size: 22rpx; margin-top: 16rpx; }
</style>
