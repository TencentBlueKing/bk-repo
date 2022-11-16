package com.tencent.bkrepo.replication.replica.base.interceptor.progress

import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Okio
import okio.Sink

internal class ProgressRequestBody(
    private val delegate: RequestBody,
    private val listener: ProgressListener,
    private val task: ReplicaTaskInfo,
    private val sha256: String
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink: BufferedSink = Okio.buffer(countingSink)
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            listener.onProgress(task, sha256, byteCount)
        }
    }
}
