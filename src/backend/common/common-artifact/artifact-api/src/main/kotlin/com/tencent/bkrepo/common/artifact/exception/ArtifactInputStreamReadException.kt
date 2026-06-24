package com.tencent.bkrepo.common.artifact.exception

import java.io.IOException

/**
 * 标记型异常：表示在读取上游/客户端源输入流时发生的 IO 异常。
 *
 * 用于在异常被多层包装（例如 HTTP 客户端将其包装成自定义异常）之后，
 * 调用方仍可通过类型 + cause 链准确判断"是源流读取失败"而非"下游处理失败"
 */
class ArtifactInputStreamReadException(cause: IOException) : IOException(cause.message, cause) {

    companion object {
        /**
         * 在 cause 链中查找 [ArtifactInputStreamReadException]，找到则返回，否则返回 null。
         */
        fun findIn(throwable: Throwable?): ArtifactInputStreamReadException? {
            var current: Throwable? = throwable
            val visited = HashSet<Throwable>()
            while (current != null && visited.add(current)) {
                if (current is ArtifactInputStreamReadException) {
                    return current
                }
                current = current.cause
            }
            return null
        }
    }
}
