/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.cargo.listener.operation

import com.tencent.bkrepo.cargo.pojo.event.CargoOperationRequest
import com.tencent.bkrepo.cargo.pojo.index.CrateIndex
import com.tencent.bkrepo.cargo.service.impl.CargoCommonService
import com.tencent.bkrepo.cargo.utils.CargoUtils.getCargoIndexFullPath
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil.buildNodeCreateRequest
import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch
import java.io.InputStream

abstract class AbstractCargoOperation(
    private val request: CargoOperationRequest,
    private val cargoCommonService: CargoCommonService
) : Runnable {
    override fun run() {
        with(request) {
            val stopWatch = StopWatch(
                "Handling event for refreshing index of crate $name " +
                    "in repo [$projectId/$repoName] by User [$userId]"
            )
            stopWatch.start()
            val indexFullPath = getCargoIndexFullPath(name)
            cargoCommonService.lockAction(projectId, repoName, indexFullPath) { handleOperation(this, indexFullPath) }
            stopWatch.stop()
            logger.info(
                "Total cost for refreshing index of crate $name " +
                    "in repo [$projectId/$repoName] by User [$userId] is: ${stopWatch.totalTimeSeconds}s"
            )
        }
    }

    /**
     * 处理对应操作用于更新对应index文件
     */
    private fun handleOperation(request: CargoOperationRequest, indexFullPath: String) {
        with(request) {
            try {
                val stopWatch = StopWatch(
                    "Refreshing index of crate $name " +
                        "in repo [$projectId/$repoName] by User [$userId]"
                )
                stopWatch.start()
                val indexInputStream = cargoCommonService.getStreamOfCrate(projectId, repoName, indexFullPath)
                val storageCredentials = cargoCommonService.getStorageCredentials(projectId, repoName)
                logger.info(
                    "query index.json " +
                        "in repo [$projectId/$repoName] by User [$userId] cost: ${stopWatch.totalTimeSeconds}s"
                )
                val artifactFile = buildIndexFile(indexInputStream, storageCredentials)
                logger.info("Index of crate $name in repo [$projectId/$repoName] is ready to upload...")
                val nodeCreateRequest = buildNodeCreateRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = indexFullPath,
                    operator = userId,
                    artifactFile = artifactFile
                )
                cargoCommonService.uploadIndexOfCrate(artifactFile, nodeCreateRequest)
                logger.info(
                    "Index of crate $name has been refreshed by User [$userId] " +
                        "in repo [$projectId/$repoName] !"
                )
                stopWatch.stop()
            } catch (e: Exception) {
                logger.error(
                    "Error [${e.message}] occurred while refreshing index of crate $name by" +
                        " User [$userId] in repo [$projectId/$repoName] !"
                )
                throw e
            }
        }
    }


    private fun buildIndexFile(
        inputStream: InputStream?,
        storageCredentials: StorageCredentials?,
    ): ArtifactFile {
        try {
            var versions = cargoCommonService.convertToCrateIndex(inputStream)
            versions = handleEvent(versions)
            return cargoCommonService.buildIndexArtifactFile(versions, storageCredentials)
        } catch (e: Exception) {
            logger.error("Failed to handle index update event $request: ${e.message}")
            // TODO 如果失败了如何处理？？
            throw e
        }
    }

    /**
     * 处理对应的事件用于更新index.json
     */
    open fun handleEvent(versions: MutableList<CrateIndex>): MutableList<CrateIndex> {
        throw MethodNotAllowedException()
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AbstractCargoOperation::class.java)
    }
}
