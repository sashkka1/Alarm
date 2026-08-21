package com.sasha.alarm.platform

import com.sasha.alarm.core.Clock

/** Боевая реализация порта [Clock]. Единственное место, где вызываются системные часы. */
object AndroidClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
