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

package com.tencent.bkrepo.fs.server.filter

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.fs.server.storage.ReactiveArtifactFile
import com.tencent.bkrepo.fs.server.storage.ReactiveArtifactFileFactory
import kotlin.system.measureTimeMillis
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

class ArtifactFileCleanupFilter : CoHandlerFilterFunction {

    override suspend fun filter(
        request: ServerRequest,
        next: suspend (ServerRequest) -> ServerResponse
    ): ServerResponse {
        try {
            return next(request)
        } finally {
            cleanup(request)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun cleanup(request: ServerRequest) {
        try {
            val artifactFileList = request.exchange()
                .attributes[ReactiveArtifactFileFactory.ARTIFACT_FILES] as? MutableList<ArtifactFile>
            artifactFileList?.filter {
                // 理论上走到这里handler的方法已经执行完毕，但是这里会出现handler里面的方法未完成，
                // 就已经走到过滤器这里了，怀疑跟协程的挂起，恢复有关。走到这里，表示请求已经结束，则
                // 临时文件可以删除，删除前需要finish一下
                if (it is ReactiveArtifactFile && !it.hasInitialized()) {
                    it.finish()
                }
                !it.isInMemory()
            }?.forEach {
                val absolutePath = it.getFile()!!.absolutePath
                measureTimeMillis { it.delete() }.apply {
                    logger.info("Delete temp artifact file [$absolutePath] success, elapse $this ms")
                }
            }
        } catch (exception: Exception) {
            logger.warn("Failed to clean temp artifact file.", exception)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactFileCleanupFilter::class.java)
    }
}
