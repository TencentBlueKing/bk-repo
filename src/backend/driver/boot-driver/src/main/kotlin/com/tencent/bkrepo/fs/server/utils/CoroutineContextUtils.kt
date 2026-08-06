package com.tencent.bkrepo.fs.server.utils

import io.micrometer.context.ContextSnapshotFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

object CoroutineContextUtils {
    private val contextSnapshotFactory = ContextSnapshotFactory.builder().build()

    suspend fun <T> withTraceContext(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
        val snapshot = contextSnapshotFactory.captureAll()
        return withContext(context) {
            snapshot.setThreadLocals().use { block.invoke(this) }
        }
    }
}
