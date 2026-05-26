package com.tencent.bkrepo.common.artifact.stream

import com.tencent.bkrepo.common.artifact.exception.ArtifactInputStreamReadException
import java.io.IOException
import java.io.InputStream

/**
 * 源流标记输入流。
 *
 * 该包装流不会改变数据内容，仅将底层 [InputStream.read] 抛出的 [IOException]
 * 统一包装为 [com.tencent.bkrepo.common.artifact.exception.ArtifactInputStreamReadException]，使下游（例如分片上传、
 * COS HTTP 客户端等）在多层异常包装后仍能通过类型 + cause 链
 * 准确判断"是源流读取失败"。
 *
 * 注意：仅捕获 [IOException]。非 IO 类异常（例如限流过载抛出的
 * `OverloadException : RuntimeException`）会原样穿透，不会被错误地包装为
 * 源流读取异常，从而保证上层基于异常类型分发的处理逻辑（IO 异常 vs 过载异常）
 * 仍然正确。
 */
class SourceMarkerInputStream(delegate: InputStream) : DelegateInputStream(delegate) {

    override fun read(): Int {
        return try {
            super.read()
        } catch (e: IOException) {
            throw wrap(e)
        }
    }

    override fun read(byteArray: ByteArray): Int {
        return try {
            super.read(byteArray)
        } catch (e: IOException) {
            throw wrap(e)
        }
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        return try {
            super.read(byteArray, off, len)
        } catch (e: IOException) {
            throw wrap(e)
        }
    }

    private fun wrap(e: IOException): IOException {
        // 已经是标记异常时不再重复包装，保留原始 cause 链
        return e as? ArtifactInputStreamReadException ?: ArtifactInputStreamReadException(e)
    }
}
