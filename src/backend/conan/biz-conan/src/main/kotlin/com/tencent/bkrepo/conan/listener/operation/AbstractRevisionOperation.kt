/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.conan.listener.operation

import com.tencent.bkrepo.conan.pojo.IndexInfo
import com.tencent.bkrepo.conan.pojo.RevisionOperationRequest
import com.tencent.bkrepo.conan.service.impl.CommonService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch

abstract class AbstractRevisionOperation(
    private val request: RevisionOperationRequest,
    private val commonService: CommonService
) : Runnable {
    override fun run() {
        with(request) {
            val stopWatch = StopWatch(
                "Handling event for refreshing index.json " +
                    "in repo [$projectId/$repoName] by User [$operator]"
            )
            stopWatch.start()
            commonService.lockAction(projectId, repoName, revPath) { handleOperation(this) }
            stopWatch.stop()
            logger.info(
                "Total cost for refreshing index.json" +
                    "in repo [$projectId/$repoName] by User [$operator] is: ${stopWatch.totalTimeSeconds}s"
            )
        }
    }

    /**
     * 处理对应操作用于更新index.json
     */
    private fun handleOperation(request: RevisionOperationRequest) {
        with(request) {
            try {
                val stopWatch = StopWatch(
                    "Refreshing index.json " +
                        "in repo [$projectId/$repoName] by User [$operator]"
                )
                stopWatch.start()
                val (tempRevPath, tempRefStr) = if (pRevPath.isNullOrEmpty()) {
                    Pair(revPath, refStr)
                } else {
                    Pair(pRevPath!!, pRefStr!!)
                }
                val indexInfo = commonService.getRevisionsList(projectId, repoName, tempRevPath, tempRefStr)
                stopWatch.stop()
                logger.info(
                    "query index.json " +
                        "in repo [$projectId/$repoName] by User [$operator] cost: ${stopWatch.totalTimeSeconds}s"
                )
                handleEvent(indexInfo)
                logger.info("index.json in repo [$projectId/$repoName] is ready to upload...")
                commonService.uploadIndexJson(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = "/$tempRevPath",
                    indexInfo = indexInfo
                )
                logger.info(
                    "Index.json has been refreshed by User [$operator] " +
                        "in repo [$projectId/$repoName] !"
                )
            } catch (e: Exception) {
                logger.error(
                    "Error [${e.message}] occurred while refreshing index.json by" +
                        " User [$operator] in repo [$projectId/$repoName] !"
                )
                throw e
            }
        }
    }

    /**
     * 处理对应的事件用于更新index.json
     */
    open fun handleEvent(indexInfo: IndexInfo) {}

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AbstractRevisionOperation::class.java)
    }
}
