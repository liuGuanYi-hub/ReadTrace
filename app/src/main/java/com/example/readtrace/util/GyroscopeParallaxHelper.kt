package com.example.readtrace.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.readtrace.widget.HolographicSpecularOverlayView
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class GyroscopeParallaxHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isRunning = false

    // 平滑低通滤波状态
    private var smoothedPitch = 0f
    private var smoothedRoll = 0f
    private val smoothingFactor = 0.15f // 越小平滑度越高，0.15f 兼顾丝滑与即时响应

    private val parallaxViews = mutableListOf<ParallaxTarget>()
    private val specularViews = mutableListOf<WeakReference<HolographicSpecularOverlayView>>()

    private data class ParallaxTarget(
        val viewRef: WeakReference<View>,
        val maxRotation: Float,
        val maxTranslation: Float,
        val invert: Boolean,
    )

    /**
     * 绑定目标 View 进行 3D 透视视差变换
     */
    fun bind3DParallax(
        view: View,
        maxRotation: Float = 10f,
        maxTranslation: Float = 14f,
        invert: Boolean = false,
    ) {
        // 设置更大的相机透视距离，产生类似 3D 沙盘的深邃空间感
        val density = view.resources.displayMetrics.density
        view.cameraDistance = 8000f * density
        parallaxViews.add(ParallaxTarget(WeakReference(view), maxRotation, maxTranslation, invert))
    }

    /**
     * 绑定全息漫反射高光层
     */
    fun bindHolographicSpecular(specularView: HolographicSpecularOverlayView) {
        specularViews.add(WeakReference(specularView))
    }

    /**
     * 自动绑定 Android 生命周期 (后台自动休眠注销传感器，零额外耗电)
     */
    fun bindLifecycle(lifecycle: Lifecycle) {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                start()
            }

            override fun onPause(owner: LifecycleOwner) {
                stop()
            }
        })
    }

    fun start() {
        if (isRunning || rotationSensor == null || sensorManager == null) return
        isRunning = true
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!isRunning || sensorManager == null) return
        isRunning = false
        sensorManager.unregisterListener(this)
        resetViews()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning) return

        // 部分机型传感器数据异常（如 values 长度不足）会使 getRotationMatrixFromVector 抛出异常，统一兜底避免闪退
        runCatching {
            var rawPitch = 0f
            var rawRoll = 0f

            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // orientation[1] = pitch (x 轴), orientation[2] = roll (y 轴)
                rawPitch = orientation[1] // 弧度
                rawRoll = orientation[2]  // 弧度
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values.getOrNull(0) ?: return@runCatching
                val y = event.values.getOrNull(1) ?: return@runCatching
                val z = event.values.getOrNull(2) ?: return@runCatching
                rawRoll = atan2(x.toDouble(), sqrt((y * y + z * z).toDouble())).toFloat()
                rawPitch = atan2(-y.toDouble(), sqrt((x * x + z * z).toDouble())).toFloat()
            }

            // 归一化到 [-1.0, 1.0]，默认按仰角 45 度为握持中心基准
            val targetPitch = (rawPitch.coerceIn(-1.0f, 1.0f))
            val targetRoll = (rawRoll.coerceIn(-1.0f, 1.0f))

            // 指数平滑滤波
            smoothedPitch += (targetPitch - smoothedPitch) * smoothingFactor
            smoothedRoll += (targetRoll - smoothedRoll) * smoothingFactor

            applyTransform(smoothedPitch, smoothedRoll)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun applyTransform(pitch: Float, roll: Float) {
        // 1. 应用 3D 矩阵视差变换
        val iterator = parallaxViews.iterator()
        while (iterator.hasNext()) {
            val target = iterator.next()
            val v = target.viewRef.get()
            if (v == null) {
                iterator.remove()
                continue
            }

            val sign = if (target.invert) -1f else 1f
            // 俯仰带动 rotationX，翻滚带动 rotationY
            v.rotationX = -pitch * target.maxRotation * sign
            v.rotationY = roll * target.maxRotation * sign
            v.translationX = roll * target.maxTranslation * sign
            v.translationY = -pitch * target.maxTranslation * sign
        }

        // 2. 联动全息漫反射高光流转
        val specularIterator = specularViews.iterator()
        while (specularIterator.hasNext()) {
            val ref = specularIterator.next()
            val specularView = ref.get()
            if (specularView == null) {
                specularIterator.remove()
                continue
            }
            specularView.updateAngles(pitch, roll)
        }
    }

    private fun resetViews() {
        parallaxViews.forEach { target ->
            val v = target.viewRef.get() ?: return@forEach
            v.animate()
                .rotationX(0f)
                .rotationY(0f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(300L)
                .start()
        }
    }
}
