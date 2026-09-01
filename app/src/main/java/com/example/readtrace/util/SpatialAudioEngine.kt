package com.example.readtrace.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.math.sin

/**
 * 🔊 双耳空间立体声场与物理微声学引擎 (SpatialAudioEngine)
 *
 * 核心特性：
 * 1. 纯内存高频实时程序化 PCM 音频合成，0 外部冗余资源、0 启动延迟；
 * 2. 陀螺仪重力感应自适应双耳空间定位（Binaural Panning -1.0f ~ +1.0f）；
 * 3. 六大物理拟真微声学：
 *    - 护照盖印：低频沉重落印厚音；
 *    - 电影撕票：清脆齿轮打孔破开声；
 *    - 拟真翻书：轻盈纸张滑动声；
 *    - 黑胶落针：黑胶微爆音与针尖触盘声；
 *    - 卡带插入：金属弹片卡扣声；
 *    - 星系引力：528Hz 治愈空灵泛音。
 */
object SpatialAudioEngine {

    private val audioExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val SAMPLE_RATE = 44100

    /**
     * 1. 🛂 护照盖印沉重钝响
     */
    fun playStampThud(pan: Float = 0f) {
        audioExecutor.execute {
            val durationMs = 120
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples * 2)

            val leftGain = ((1.0f - pan) / 2.0f).coerceIn(0.1f, 1.0f)
            val rightGain = ((1.0f + pan) / 2.0f).coerceIn(0.1f, 1.0f)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val decay = Math.exp(-t * 28.0)
                val freq = 110.0 - 50.0 * (i.toDouble() / numSamples)
                val sampleVal = (sin(2.0 * Math.PI * freq * t) * decay * 28000).toInt()
                val shortVal = sampleVal.coerceIn(-32768, 32767).toShort()

                buffer[i * 2] = (shortVal * leftGain).toInt().toShort()
                buffer[i * 2 + 1] = (shortVal * rightGain).toInt().toShort()
            }
            playPcmStereo(buffer)
        }
    }

    /**
     * 2. 🎟️ 电影票打孔撕开清脆声
     */
    fun playTicketTear(pan: Float = 0f) {
        audioExecutor.execute {
            val durationMs = 90
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples * 2)

            val leftGain = ((1.0f - pan) / 2.0f).coerceIn(0.1f, 1.0f)
            val rightGain = ((1.0f + pan) / 2.0f).coerceIn(0.1f, 1.0f)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val decay = Math.exp(-t * 35.0)
                val noise = (Math.random() * 2.0 - 1.0)
                val click = sin(2.0 * Math.PI * 2400.0 * t) * 0.6 + noise * 0.4
                val sampleVal = (click * decay * 24000).toInt()
                val shortVal = sampleVal.coerceIn(-32768, 32767).toShort()

                buffer[i * 2] = (shortVal * leftGain).toInt().toShort()
                buffer[i * 2 + 1] = (shortVal * rightGain).toInt().toShort()
            }
            playPcmStereo(buffer)
        }
    }

    /**
     * 3. 📖 拟真纸张翻页摩擦沙沙声
     */
    fun playPageTurn(pan: Float = 0f) {
        audioExecutor.execute {
            val durationMs = 140
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples * 2)

            val leftGain = ((1.0f - pan) / 2.0f).coerceIn(0.1f, 1.0f)
            val rightGain = ((1.0f + pan) / 2.0f).coerceIn(0.1f, 1.0f)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = sin(Math.PI * (i.toDouble() / numSamples))
                val noise = (Math.random() * 2.0 - 1.0) * envelope
                val sampleVal = (noise * 14000).toInt()
                val shortVal = sampleVal.coerceIn(-32768, 32767).toShort()

                buffer[i * 2] = (shortVal * leftGain).toInt().toShort()
                buffer[i * 2 + 1] = (shortVal * rightGain).toInt().toShort()
            }
            playPcmStereo(buffer)
        }
    }

    /**
     * 4. 💽 黑胶落针触盘微爆音
     */
    fun playNeedleDrop(pan: Float = 0f) {
        audioExecutor.execute {
            val durationMs = 80
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples * 2)

            val leftGain = ((1.0f - pan) / 2.0f).coerceIn(0.1f, 1.0f)
            val rightGain = ((1.0f + pan) / 2.0f).coerceIn(0.1f, 1.0f)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val decay = Math.exp(-t * 50.0)
                val tone = sin(2.0 * Math.PI * 480.0 * t) * 0.4 + (Math.random() * 2.0 - 1.0) * 0.6
                val sampleVal = (tone * decay * 20000).toInt()
                val shortVal = sampleVal.coerceIn(-32768, 32767).toShort()

                buffer[i * 2] = (shortVal * leftGain).toInt().toShort()
                buffer[i * 2 + 1] = (shortVal * rightGain).toInt().toShort()
            }
            playPcmStereo(buffer)
        }
    }

    /**
     * 5. 🕹️ 游戏卡带插入卡扣清脆声
     */
    fun playCartridgeSnap(pan: Float = 0f) {
        audioExecutor.execute {
            val durationMs = 100
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples * 2)

            val leftGain = ((1.0f - pan) / 2.0f).coerceIn(0.1f, 1.0f)
            val rightGain = ((1.0f + pan) / 2.0f).coerceIn(0.1f, 1.0f)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val decay = Math.exp(-t * 40.0)
                val snapTone = sin(2.0 * Math.PI * 1800.0 * t) * 0.7 + sin(2.0 * Math.PI * 900.0 * t) * 0.3
                val sampleVal = (snapTone * decay * 26000).toInt()
                val shortVal = sampleVal.coerceIn(-32768, 32767).toShort()

                buffer[i * 2] = (shortVal * leftGain).toInt().toShort()
                buffer[i * 2 + 1] = (shortVal * rightGain).toInt().toShort()
            }
            playPcmStereo(buffer)
        }
    }

    /**
     * 6. 🌌 心智星系空灵共鸣泛音 (528Hz 治愈声波)
     */
    fun playCelestialTone(pan: Float = 0f) {
        audioExecutor.execute {
            val durationMs = 280
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples * 2)

            val leftGain = ((1.0f - pan) / 2.0f).coerceIn(0.1f, 1.0f)
            val rightGain = ((1.0f + pan) / 2.0f).coerceIn(0.1f, 1.0f)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val decay = Math.exp(-t * 8.0)
                val chime = sin(2.0 * Math.PI * 528.0 * t) * 0.6 + sin(2.0 * Math.PI * 1056.0 * t) * 0.4
                val sampleVal = (chime * decay * 22000).toInt()
                val shortVal = sampleVal.coerceIn(-32768, 32767).toShort()

                buffer[i * 2] = (shortVal * leftGain).toInt().toShort()
                buffer[i * 2 + 1] = (shortVal * rightGain).toInt().toShort()
            }
            playPcmStereo(buffer)
        }
    }

    private fun playPcmStereo(pcmBuffer: ShortArray) {
        runCatching {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(pcmBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
            audioTrack.play()
            audioTrack.setNotificationMarkerPosition(pcmBuffer.size / 2)
            audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    track?.stop()
                    track?.release()
                }
                override fun onPeriodicNotification(track: AudioTrack?) {}
            })

            // 兜底释放：部分设备 onMarkerReached 可能不回调，按音频时长 + 500ms 延迟兜底 release，
            // 防止高频交互下 AudioTrack 句柄累积耗尽系统上限（32 个）
            val durationMs = pcmBuffer.size / 2 * 1000L / SAMPLE_RATE
            mainHandler.postDelayed({
                runCatching {
                    if (audioTrack.state != AudioTrack.STATE_UNINITIALIZED) {
                        audioTrack.stop()
                        audioTrack.release()
                    }
                }
            }, durationMs + 500L)
        }
    }
}
