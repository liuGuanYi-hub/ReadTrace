<template>
  <view
    class="stage"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
  >
    <view class="card" :style="cardStyle">
      <image v-if="cover" class="cover layer-3" :src="cover" mode="aspectFill" />
      <view v-else class="cover cover-empty layer-3">{{ emoji }}</view>
      <view class="title layer-2">{{ book.title }}</view>
      <view class="meta layer-1">[{{ mediaLabel }} · {{ book.author || '佚名' }}]</view>
      <view class="quote layer-2" v-if="book.shortComment">“{{ book.shortComment }}”</view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 🔮 2.5D 空间视差标本微卡 (DioramaCard)
 * 微信端 wx.onDeviceMotionChange 陀螺仪 + 触控拖拽双通道，4 层差速视差。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import type { Book } from '../utils/models';
import { MEDIA_LABEL } from '../utils/models';

const props = defineProps<{ book: Book }>();

const tiltX = ref(0);
const tiltY = ref(0);
let startTouch: { x: number; y: number } | null = null;
let motionHandler: ((res: { beta: number; gamma: number }) => void) | null = null;

const cover = computed(() => props.book.coverUrl || '');
const emoji = computed(() => MEDIA_LABEL[props.book.mediaType]?.emoji || '📖');
const mediaLabel = computed(() => MEDIA_LABEL[props.book.mediaType]?.name || '');

const cardStyle = computed(() => ({
  transform: `rotateX(${tiltX.value}deg) rotateY(${tiltY.value}deg)`,
}));

function onTouchStart(e: TouchEvent) {
  const t = e.touches[0];
  startTouch = { x: t.clientX, y: t.clientY };
}

function onTouchMove(e: TouchEvent) {
  if (!startTouch) return;
  const t = e.touches[0];
  // 触控拖拽 → ±8° 微倾角（visionOS 空间悬浮手感）
  tiltY.value = ((t.clientX - startTouch.x) / 24).toFixed(1) as unknown as number;
  tiltX.value = ((t.clientY - startTouch.y) / -24).toFixed(1) as unknown as number;
}

function onTouchEnd() {
  startTouch = null;
  tiltX.value = 0;
  tiltY.value = 0;
}

onMounted(() => {
  // 陀螺仪通道：手机倾斜时封面悬浮位移
  motionHandler = (res) => {
    tiltY.value = Math.max(-8, Math.min(8, res.gamma || 0));
    tiltX.value = Math.max(-8, Math.min(8, (res.beta || 0) - 45));
  };
  if (typeof wx !== 'undefined' && wx.onDeviceMotionChange) {
    wx.startDeviceMotionListening({ interval: 'ui' });
    wx.onDeviceMotionChange(motionHandler);
  }
});

onUnmounted(() => {
  if (typeof wx !== 'undefined' && wx.offDeviceMotionChange && motionHandler) {
    wx.offDeviceMotionChange(motionHandler);
    wx.stopDeviceMotionListening();
  }
});
</script>

<style>
.stage { perspective: 900px; display: flex; justify-content: center; }
.card {
  width: 560rpx; padding: 32rpx; border-radius: 24rpx; position: relative;
  background: linear-gradient(145deg, #0c111c, #111a2b);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 30rpx 80rpx rgba(0, 0, 0, 0.6);
  transform-style: preserve-3d;
  transition: transform 0.15s ease-out;
}
.cover { width: 100%; height: 640rpx; border-radius: 16rpx; }
.cover-empty {
  display: flex; align-items: center; justify-content: center;
  font-size: 96rpx; background: #131b2c; color: #ffe700;
}
.layer-3 { transform: translateZ(46px); }
.layer-2 { transform: translateZ(28px); }
.layer-1 { transform: translateZ(16px); }
.title { font-size: 36rpx; margin-top: 24rpx; color: #f2efe6; }
.meta { font-size: 20rpx; color: #99a; margin-top: 8rpx; font-family: monospace; color: #99aabb; }
.quote { font-size: 24rpx; font-style: italic; color: #d8d2c4; margin-top: 16rpx;
  border-left: 4rpx solid rgba(255, 231, 0, 0.5); padding-left: 16rpx; }
</style>
