package de.fgna.androidllmservice

import android.content.Context

internal object LlmRuntimeProvider {
    @Volatile
    private var runtime: LiteRtRuntime? = null

    fun get(context: Context): LiteRtRuntime =
        runtime ?: synchronized(this) {
            runtime ?: LiteRtRuntime(context.applicationContext).also { runtime = it }
        }
}
