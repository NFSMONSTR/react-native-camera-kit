package com.rncamerakit.events

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event

class FocusRectChangedEvent(
    surfaceId: Int,
    viewId: Int,
    private val x: Float?,
    private val y: Float?,
    private val width: Float?,
    private val height: Float?,
) : Event<FocusRectChangedEvent>(surfaceId, viewId) {
    override fun getEventName(): String = EVENT_NAME

    override fun getEventData(): WritableMap =
        Arguments.createMap().apply {
            if (x != null && y != null && width != null && height != null) {
                putDouble("x", x.toDouble())
                putDouble("y", y.toDouble())
                putDouble("width", width.toDouble())
                putDouble("height", height.toDouble())
            }
        }

    companion object {
        const val EVENT_NAME = "focusRectChanged"
    }
}
