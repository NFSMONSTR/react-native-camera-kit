package com.rncamerakit

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.MeteringRectangle
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.view.PreviewView

class FocusConverter @OptIn(ExperimentalCamera2Interop::class) constructor(
    private val context: Context,
    private val aspectRatio: Int,
    private val resizeMode: PreviewView.ScaleType,
    private val isFrontCamera: Boolean,
    private val cameraInfo: CameraInfo,
    private val previewView: PreviewView
) {
    private val activeArraySize: Rect
    private val sensorOrientation: Int
    private val density: Float

    init {
        val camera2Info = Camera2CameraInfo.from(cameraInfo)
        activeArraySize = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
        ) ?: Rect(0, 0, 1920, 1080)
        // Physical rotation of the sensor image relative to device natural orientation (degrees CW)
        sensorOrientation = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_ORIENTATION
        ) ?: 90
        density = context.resources.displayMetrics.density
    }

    private fun getDeviceRotationDegrees(): Int {
        // Read from previewView.display so the value is always current even after device rotation
        return when (previewView.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90  -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else                 -> 0  // ROTATION_0
        }
    }

    private fun getEffectiveRotation(deviceDeg: Int): Int {
        // Rear camera:  (sensorOrientation - deviceRotation + 360) % 360
        // Front camera: (sensorOrientation + deviceRotation)       % 360
        return if (isFrontCamera) (sensorOrientation + deviceDeg) % 360
               else (sensorOrientation - deviceDeg + 360) % 360
    }

    private fun normalizeSensorRect(r: MeteringRectangle): FloatArray {
        val sW = activeArraySize.width().toFloat()
        val sH = activeArraySize.height().toFloat()
        return floatArrayOf(
            r.x.toFloat() / sW,
            r.y.toFloat() / sH,
            (r.x + r.width).toFloat()  / sW,
            (r.y + r.height).toFloat() / sH
        )
    }

    private fun rotateNormalizedRect(ltrb: FloatArray, rotation: Int): FloatArray {
        val l = ltrb[0]; val t = ltrb[1]; val r = ltrb[2]; val b = ltrb[3]
        // Each case maps sensor corners to screen corners for the given rotation
        return when (rotation) {
            90  -> floatArrayOf(1f - b, l,       1f - t, r      )
            180 -> floatArrayOf(1f - r, 1f - b,  1f - l, 1f - t )
            270 -> floatArrayOf(t,      1f - r,  b,      1f - l )
            else -> floatArrayOf(l, t, r, b)  // 0 degrees — identity
        }
    }

    private fun flipHorizontal(ltrb: FloatArray): FloatArray {
        val l = ltrb[0]; val t = ltrb[1]; val r = ltrb[2]; val b = ltrb[3]
        return floatArrayOf(1f - r, t, 1f - l, b)
    }

    private fun getContentAspectRatio(effectiveRotation: Int): Float {
        val base = if (aspectRatio == AspectRatio.RATIO_4_3) 4f / 3f else 16f / 9f
        // Sensor axes swapped when rotated 90 or 270 degrees relative to the screen
        return if (effectiveRotation == 90 || effectiveRotation == 270) 1f / base else base
    }

    private fun getContentRectPx(effectiveRotation: Int): RectF {
        val viewW = previewView.width.toFloat()
        val viewH = previewView.height.toFloat()
        val cAR = getContentAspectRatio(effectiveRotation)  // content width / height
        val vAR = viewW / viewH                             // view width / height

        return when (resizeMode) {
            PreviewView.ScaleType.FIT_CENTER -> {
                if (cAR > vAR) {
                    // Content is wider than the view → scale by width, letterbox top/bottom
                    val h = viewW / cAR
                    RectF(0f, (viewH - h) / 2f, viewW, (viewH + h) / 2f)
                } else {
                    // Content is taller than the view → scale by height, pillarbox left/right
                    val w = viewH * cAR
                    RectF((viewW - w) / 2f, 0f, (viewW + w) / 2f, viewH)
                }
            }
            else -> { // FILL_CENTER — content overflows the view; offset values will be negative
                if (cAR > vAR) {
                    // Content is wider → scale by height, crop left/right
                    val w = viewH * cAR
                    RectF((viewW - w) / 2f, 0f, (viewW + w) / 2f, viewH)
                } else {
                    // Content is taller → scale by width, crop top/bottom
                    val h = viewW / cAR
                    RectF(0f, (viewH - h) / 2f, viewW, (viewH + h) / 2f)
                }
            }
        }
    }

    fun sensorToPreview(sensorRect: MeteringRectangle?): FocusRect? {
        if (sensorRect == null) return null

        val effectiveRotation = getEffectiveRotation(getDeviceRotationDegrees())

        // 1. Normalize sensor coordinates to [0, 1]
        var ltrb = normalizeSensorRect(sensorRect)
        // 2. Rotate into screen orientation
        ltrb = rotateNormalizedRect(ltrb, effectiveRotation)
        // 3. Mirror horizontally for front camera (PreviewView shows front camera mirrored)
        if (isFrontCamera) ltrb = flipHorizontal(ltrb)
        // 4. Map [0, 1] coords to view pixels via the content rect
        val cr = getContentRectPx(effectiveRotation)
        // 5. Convert px → dp and return
        return FocusRect(
            x      = (cr.left + ltrb[0] * cr.width())  / density,
            y      = (cr.top  + ltrb[1] * cr.height()) / density,
            width  = (ltrb[2] - ltrb[0]) * cr.width()  / density,
            height = (ltrb[3] - ltrb[1]) * cr.height() / density
        )
    }

    fun getPreviewSizeDp(): Pair<Float, Float> {
        return Pair(
            previewView.width / density,
            previewView.height / density
        )
    }
}

data class FocusRect(val x: Float, val y: Float, val width: Float, val height: Float)
