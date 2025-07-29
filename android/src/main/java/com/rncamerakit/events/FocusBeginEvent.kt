package com.rncamerakit.events

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event

class FocusBeginEvent(
    surfaceId: Int,
    viewId: Int,
) : Event<FocusBeginEvent>(surfaceId, viewId) {
    override fun getEventName(): String = EVENT_NAME

    override fun getEventData(): WritableMap =
        Arguments.createMap().apply {}

    companion object {
        const val EVENT_NAME = "focusBegin"
    }
}
