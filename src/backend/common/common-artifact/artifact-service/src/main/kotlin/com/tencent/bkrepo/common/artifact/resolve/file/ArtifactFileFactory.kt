/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.file.bksync.BkSyncArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.chunk.RandomAccessArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.stream.StreamArtifactFile
import com.tencent.bkrepo.common.bksync.BlockChannel
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitorHelper
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.InputStream

/**
 * ArtifactFile工厂方法
 */
@Component
class ArtifactFileFactory(
    storageProperties: StorageProperties,
    storageHealthMonitorHelper: StorageHealthMonitorHelper,
    limitCheckService: RequestLimitCheckService,
    registry: ObservationRegistry
) {

    init {
        monitorHelper = storageHealthMonitorHelper
        properties = storageProperties
        requestLimitCheckService = limitCheckService
        observationRegistry = registry
    }

    companion object {

        private lateinit var monitorHelper: StorageHealthMonitorHelper
        private lateinit var properties: StorageProperties
        private lateinit var requestLimitCheckService: RequestLimitCheckService
        private lateinit var observationRegistry: ObservationRegistry

        const val ARTIFACT_FILES = "artifact.files"

        /**
         * 构建使用BkSync的增量传输文件
         * */
        fun buildBkSync(
            blockChannel: BlockChannel,
            deltaInputStream: InputStream,
            blockSize: Int,
        ): BkSyncArtifactFile {
            return BkSyncArtifactFile(
                blockChannel,
                deltaInputStream,
                blockSize,
            ).apply {
                track(this)
            }
        }

        /**
         * 构造分块接收数据的artifact file
         */
        fun buildChunked(): ChunkedArtifactFile {
            return ChunkedArtifactFile(
                monitor = getMonitor(),
                storageProperties = properties,
                storageCredentials = getStorageCredentials(),
                registry = observationRegistry
            ).apply {
                track(this)
            }
        }

        fun buildChunked(storageCredentials: StorageCredentials): ChunkedArtifactFile {
            return ChunkedArtifactFile(
                monitor = getMonitor(storageCredentials),
                storageProperties = properties,
                storageCredentials = storageCredentials,
                registry = observationRegistry
            ).apply {
                track(this)
            }
        }

        fun buildDfsArtifactFile(): RandomAccessArtifactFile {
            return RandomAccessArtifactFile(
                monitor = getMonitor(),
                storageCredentials = getStorageCredentials(),
                storageProperties = properties,
                registry = observationRegistry
            ).apply {
                track(this)
            }
        }

        /**
         * 通过输入流构造artifact file, 主要针对上传请求对其做限流操作
         * @param inputStream 输入流
         */
        fun buildWithRateLimiter(inputStream: InputStream, contentLength: Long? = null): ArtifactFile {
            return StreamArtifactFile(
                source = inputStream,
                monitor = getMonitor(),
                storageProperties = properties,
                storageCredentials = getStorageCredentials(),
                contentLength = contentLength,
                requestLimitCheckService = requestLimitCheckService,
                registry = observationRegistry
            ).apply {
                track(this)
            }
        }

        /**
         * 通过输入流构造artifact file，服务内部输入流转换成文件使用
         * @param inputStream 输入流
         */
        fun build(
            inputStream: InputStream, contentLength: Long? = null, storageCredentials: StorageCredentials? = null
        ): ArtifactFile {
            val realStorageCredentials = storageCredentials ?: getStorageCredentials()
            return StreamArtifactFile(
                source = inputStream,
                monitor = getMonitor(),
                storageProperties = properties,
                storageCredentials = realStorageCredentials,
                contentLength = contentLength,
                registry = observationRegistry
            ).apply {
                track(this)
            }
        }

        /**
         * 通过表单文件构造artifact file，使用默认存储凭证
         * @param multipartFile 表单文件
         */
        fun build(multipartFile: MultipartFile): ArtifactFile {
            return build(multipartFile, getStorageCredentials())
        }

        /**
         * 通过表单文件构造artifact file，指定存储凭证
         * @param multipartFile 表单文件
         * @param storageCredentials 存储凭证
         */
        fun build(multipartFile: MultipartFile, storageCredentials: StorageCredentials): ArtifactFile {
            return MultipartArtifactFile(
                multipartFile, getMonitor(storageCredentials), properties, storageCredentials,
                requestLimitCheckService = requestLimitCheckService, observationRegistry
            ).apply {
                track(this)
            }
        }

        /**
         * 通过表单文件构造artifact file，存放临时目录
         * @param multipartFile 表单文件
         * @param filePath 文件临时存储路径
         */
        fun build(file: MultipartFile, filePath: String): ArtifactFile {
            val artifactFile = File(filePath)
            file.transferTo(artifactFile)
            return artifactFile.toArtifactFile().apply {
                track(this)
            }
        }

        /**
         * 获取当前仓库的存储凭证
         */
        private fun getStorageCredentials(): StorageCredentials {
            return ArtifactContextHolder.getRepoDetail()?.storageCredentials ?: properties.defaultStorageCredentials()
        }

        /**
         * 记录文件到request session中，用于请求结束时清理文件
         * @param artifactFile 构件文件
         */
        @Suppress("UNCHECKED_CAST")
        private fun track(artifactFile: ArtifactFile) {
            val attributes = RequestContextHolder.getRequestAttributes() ?: return
            var artifactFileList = attributes.getAttribute(ARTIFACT_FILES, SCOPE_REQUEST) as? MutableList<ArtifactFile>
            if (artifactFileList == null) {
                artifactFileList = mutableListOf()
                attributes.setAttribute(ARTIFACT_FILES, artifactFileList, SCOPE_REQUEST)
            }
            artifactFileList.add(artifactFile)
        }

        private fun getMonitor(
            storageCredentials: StorageCredentials? = null,
        ): StorageHealthMonitor {
            val credentials = storageCredentials ?: getStorageCredentials()
            return monitorHelper.getMonitor(properties, credentials)
        }
    }
}
