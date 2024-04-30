/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.fs.server

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.storage.CoArtifactFile
import com.tencent.bkrepo.fs.server.storage.CoArtifactFileFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.bodyToFlow
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain

suspend fun WebFilterChain.filterAndAwait(exchange: ServerWebExchange) {
    this.filter(exchange).awaitSingleOrNull()
}

suspend fun ServerRequest.bodyToArtifactFile(): CoArtifactFile {
    val reactiveArtifactFile = CoArtifactFileFactory.buildArtifactFile()
    this.bodyToFlow<DataBuffer>().onCompletion {
        reactiveArtifactFile.finish()
    }.collect {
        try {
            reactiveArtifactFile.write(it)
        } finally {
            DataBufferUtils.release(it)
        }
    }
    return reactiveArtifactFile
}

fun ServerRequest.useRequestParam(param: String, consumer: (x: String) -> Unit) {
    this.queryParam(param).ifPresent { consumer(it) }
}

/**
 * 处理文件范围请求
 * @param request http server request
 * @param total 文件总长度
 * @return range 文件请求范围
 * @throws IllegalArgumentException
 * */
fun ServerRequest.resolveRange(total: Long): Range {
    val httpRange = headers().range().firstOrNull()
    return if (httpRange != null) {
        val startPosition = httpRange.getRangeStart(total)
        val endPosition = httpRange.getRangeEnd(total)
        Range(startPosition, endPosition, total)
    } else {
        Range.full(total)
    }
}
