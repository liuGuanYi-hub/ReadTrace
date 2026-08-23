package com.example.readtrace.gallery3d

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

class GalleryTouchHandler(
    context: Context,
    private val renderer: Gallery3DRenderer,
    private val onFocusChanged: (Int) -> Unit,
) : View.OnTouchListener {

    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var isDragging = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val frontIndex = renderer.getFrontFocusedIndex()
            if (frontIndex >= 0) {
                renderer.smoothFocusTo(frontIndex)
                onFocusChanged(frontIndex)
            }
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            renderer.rotationYaw += distanceX * 0.22f
            renderer.pitchAngle = (renderer.pitchAngle - distanceY * 0.15f).coerceIn(8f, 50f)
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            renderer.cameraDistance = (renderer.cameraDistance / scale).coerceIn(3.0f, 9.0f)
            return true
        }
    })

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val frontIndex = renderer.getFrontFocusedIndex()
                if (frontIndex >= 0) {
                    onFocusChanged(frontIndex)
                }
            }
        }
        return true
    }
}
