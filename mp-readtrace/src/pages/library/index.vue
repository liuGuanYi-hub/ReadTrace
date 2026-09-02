<template>
  <scroll-view scroll-y class="page">
    <view class="header">
      <text class="title">📚 阅痕 · 库藏</text>
      <text class="subtitle">微信轻量漫游端 · 与 App 数据互通</text>
    </view>

    <!-- 搜索与媒介/状态筛选 -->
    <view class="filters">
      <input class="search" v-model="keyword" placeholder="🔍 搜索书名 / 创作者" placeholder-class="ph" />
      <scroll-view scroll-x class="media-row">
        <view
          class="chip"
          :class="{ active: mediaFilter === '' }"
          @tap="mediaFilter = ''"
        >🌐 全部媒介</view>
        <view
          v-for="(m, key) in MEDIA_LABEL"
          :key="key"
          class="chip"
          :class="{ active: mediaFilter === key }"
          @tap="mediaFilter = key as MediaType"
        >{{ m.emoji }} {{ m.name }}</view>
      </scroll-view>

      <scroll-view scroll-x class="status-row-filter">
        <view
          class="chip status-chip"
          :class="{ active: statusFilter === '' }"
          @tap="statusFilter = ''"
        >全部状态</view>
        <view
          v-for="(label, key) in STATUS_LABEL"
          :key="key"
          class="chip status-chip"
          :class="{ active: statusFilter === key }"
          @tap="statusFilter = key as BookStatus"
        >{{ label }}</view>
      </scroll-view>
    </view>

    <!-- 库藏列表 -->
    <view v-for="book in filtered" :key="book.id" class="card" @tap="openDetail(book)">
      <image v-if="book.coverUrl" class="cover" :src="book.coverUrl" mode="aspectFill" />
      <view class="info">
        <text class="book-title">《{{ book.title }}》</text>
        <text class="meta">{{ MEDIA_LABEL[book.mediaType].emoji }} {{ book.author || '佚名' }} · {{ STATUS_LABEL[book.status] }}{{ book.rating ? ' · ⭐' + book.rating : '' }}</text>
        <text class="quote" v-if="book.shortComment">“{{ book.shortComment }}”</text>
      </view>
    </view>

    <view v-if="filtered.length === 0" class="empty">
      <text>馆藏为空 · 在 App 端收录作品并同步后即可漫游</text>
    </view>

    <!-- 悬浮：极速速记 -->
    <view class="fab" @tap="goQuickLog">⚡</view>
  </scroll-view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Book, BookStatus, MediaType } from '../../utils/models';
import { MEDIA_LABEL, STATUS_LABEL } from '../../utils/models';
import { loadLocalWorks } from '../../utils/sync';

const keyword = ref('');
const mediaFilter = ref<MediaType | ''>(uni.getStorageSync('rt_mp_media_filter') || '');
const statusFilter = ref<BookStatus | ''>(uni.getStorageSync('rt_mp_status_filter') || '');
const works = ref<Book[]>([]);

watch(mediaFilter, (val) => uni.setStorageSync('rt_mp_media_filter', val));
watch(statusFilter, (val) => uni.setStorageSync('rt_mp_status_filter', val));

// @ts-expect-error uni-app 页面参数获取与生命周期
onLoad((options: any) => {
  if (options?.media) mediaFilter.value = options.media;
  if (options?.status) statusFilter.value = options.status;
});

// onShow 时刷新本地缓存（Local-First，离线可用）
// @ts-expect-error uni-app 生命周期
onShow(() => { works.value = loadLocalWorks(); });

const filtered = computed(() =>
  works.value.filter((b) => {
    const okMedia = !mediaFilter.value || b.mediaType === mediaFilter.value;
    const okStatus = !statusFilter.value || b.status === statusFilter.value;
    const kw = keyword.value.trim().toLowerCase();
    const okKw = !kw || b.title.toLowerCase().includes(kw) || (b.author || '').toLowerCase().includes(kw);
    return okMedia && okStatus && okKw;
  }),
);

function openDetail(book: Book) {
  uni.showModal({
    title: `《${book.title}》`,
    content: `${MEDIA_LABEL[book.mediaType].name} · ${book.author || '佚名'}\n${book.description?.slice(0, 120) || '暂无简介'}`,
    showCancel: false,
  });
}

function goQuickLog() {
  uni.navigateTo({ url: '/pages/quick-log/index' });
}
</script>

<style>
.page { min-height: 100vh; background: #05070b; padding: 24rpx; }
.header { padding: 30rpx 8rpx 20rpx; }
.title { display: block; color: #f2efe6; font-size: 40rpx; font-weight: bold; }
.subtitle { display: block; color: #667; font-size: 22rpx; margin-top: 8rpx; }
.filters { margin-bottom: 20rpx; }
.search {
  background: rgba(255, 255, 255, 0.06); border-radius: 16rpx; padding: 18rpx 24rpx;
  color: #fff; font-size: 26rpx;
}
.ph { color: #556; }
.media-row { white-space: nowrap; margin-top: 16rpx; }
.status-row-filter { white-space: nowrap; margin-top: 12rpx; }
.chip {
  display: inline-block; padding: 12rpx 24rpx; margin-right: 12rpx; border-radius: 40rpx;
  background: rgba(255, 255, 255, 0.08); color: #99aabb; font-size: 24rpx;
}
.chip.active { background: #3a6348; color: #fff; }
.status-chip.active { background: rgba(255, 231, 0, 0.85); color: #2b1a0e; font-weight: bold; }
.card {
  display: flex; margin-bottom: 20rpx; padding: 20rpx; border-radius: 20rpx;
  background: linear-gradient(145deg, #0c111c, #111a2b); border: 1rpx solid rgba(255, 255, 255, 0.06);
}
.cover { width: 110rpx; height: 150rpx; border-radius: 12rpx; background: #131b2c; }
.info { flex: 1; margin-left: 20rpx; display: flex; flex-direction: column; }
.book-title { color: #f2efe6; font-size: 28rpx; font-weight: bold; }
.meta { color: #889; font-size: 22rpx; margin-top: 8rpx; }
.quote { color: #998f7c; font-size: 22rpx; font-style: italic; margin-top: 8rpx; overflow: hidden;
  text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.empty { text-align: center; color: #556; font-size: 24rpx; padding: 120rpx 0; }
.fab {
  position: fixed; right: 40rpx; bottom: 60rpx; width: 96rpx; height: 96rpx; border-radius: 50%;
  background: #ffe700; color: #2b1a0e; font-size: 40rpx; font-weight: bold;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 12rpx 30rpx rgba(255, 231, 0, 0.3);
}
</style>
