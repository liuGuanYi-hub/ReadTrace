<template>
  <view class="vinyl-wrap" @tap="togglePlay">
    <view class="disc" :class="{ spinning: playing }">
      <view class="groove" v-for="n in 3" :key="n" :style="{ width: `${n * 30}%` }" />
      <view class="label">{{ trackLabel }}</view>
    </view>
    <view class="tone">{{ playing ? '🎧 ' + freqText + 'Hz 播放中' : '▶︎ 轻触聆听 ' + freqText + 'Hz' }}</view>
  </view>
</template>

<script setup lang="ts">
/**
 * 🎵 悬浮黑胶播放器 (VinylPlayer)
 * WebAudio 实时合成 432/528Hz 空灵泛音；黑胶慢速自转视觉。
 * 微信小程序运行时若无 WebAudio 实现，自动降级为纯视觉模式。
 */
import { computed, onUnmounted, ref } from 'vue';
import type { Book } from '../utils/models';

const props = defineProps<{ book: Book }>();

const playing = ref(false);
let audioCtx: any = null;
let oscillators: any[] = [];

const freqText = computed(() => {
  const rating = props.book.remoteRating ?? props.book.rating ?? 5;
  return String(Math.round(432 + (Math.min(10, Math.max(1, rating)) / 10) * 96));
});
const trackLabel = computed(() => (props.book.title || '').slice(0, 4));

function togglePlay() {
  if (playing.value) {
    stop();
    return;
  }
  const Ctx = (typeof wx !== 'undefined' && (wx as any).WebAudioContext) || (globalThis as any).AudioContext;
  if (!Ctx) {
    // 纯视觉降级
    playing.value = true;
    return;
  }
  audioCtx = new Ctx();
  const freq = Number(freqText.value);
  [1, 2].forEach((m) => {
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.frequency.value = freq * m;
    osc.type = 'sine';
    gain.gain.setValueAtTime(0.12 / (m * 2), audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + 8);
    osc.connect(gain).connect(audioCtx.destination);
    osc.start();
    oscillators.push(osc);
  });
  playing.value = true;
}

function stop() {
  oscillators.forEach((o) => {
    try { o.stop(); } catch { /* already stopped */ }
  });
  oscillators = [];
  if (audioCtx) {
    try { audioCtx.close(); } catch { /* already closed */ }
    audioCtx = null;
  }
  playing.value = false;
}

onUnmounted(stop);
</script>

<style>
.vinyl-wrap { display: flex; flex-direction: column; align-items: center; }
.disc {
  width: 420rpx; height: 420rpx; border-radius: 50%;
  background: radial-gradient(circle, #1a2233 18%, #0a0e18 19%, #101726 100%);
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  display: flex; align-items: center; justify-content: center;
  position: relative; box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.6);
}
.disc.spinning { animation: spin 3.2s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.groove { position: absolute; height: 2rpx; background: rgba(255, 255, 255, 0.06); border-radius: 50%; }
.label {
  width: 180rpx; height: 180rpx; border-radius: 50%;
  background: radial-gradient(circle, #ffe700 0%, #c96a38 100%);
  display: flex; align-items: center; justify-content: center;
  font-size: 24rpx; color: #2b1a0e; font-weight: bold;
}
.tone { margin-top: 20rpx; font-size: 22rpx; color: #99aabb; }
</style>
