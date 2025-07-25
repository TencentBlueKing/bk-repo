/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.storage

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitorHelper
import com.tencent.bkrepo.fs.server.context.ReactiveArtifactContextHolder
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder

/**
 * 制品文件工厂
 * */
class CoArtifactFileFactory(
    storageProperties: StorageProperties,
    storageHealthMonitorHelper: StorageHealthMonitorHelper
) {

    init {
        Companion.storageProperties = storageProperties
        Companion.storageHealthMonitorHelper = storageHealthMonitorHelper
    }
    companion object {
        lateinit var storageProperties: StorageProperties
        lateinit var storageHealthMonitorHelper: StorageHealthMonitorHelper
        const val ARTIFACT_FILES = "artifact.files"

        suspend fun buildArtifactFile(): CoArtifactFile {
            val storageCredentials = getStorageCredentials()
            return CoArtifactFile(storageCredentials, storageProperties, getMonitor(storageCredentials)).apply {
                track(this)
            }
        }

        fun buildArtifactFileOnNotHttpRequest(storageCredentials: StorageCredentials?): CoArtifactFile {
            val credentials = storageCredentials ?: storageProperties.defaultStorageCredentials()
            return CoArtifactFile(credentials, storageProperties, getMonitor(credentials))
        }

        @Suppress("UNCHECKED_CAST")
        private suspend fun track(artifactFile: ArtifactFile) {
            val artifactFileList = ReactiveRequestContextHolder.getWebExchange()
                .attributes[ARTIFACT_FILES] as? MutableList<ArtifactFile> ?: let {
                val list = mutableListOf<ArtifactFile>()
                ReactiveRequestContextHolder.getWebExchange().attributes[ARTIFACT_FILES] = list
                list
            }
            artifactFileList.add(artifactFile)
        }

        private fun getMonitor(
            storageCredentials: StorageCredentials
        ): StorageHealthMonitor {
            return storageHealthMonitorHelper.getMonitor(storageProperties, storageCredentials)
        }

        private suspend fun getStorageCredentials(): StorageCredentials {
            return ReactiveArtifactContextHolder.getRepoDetail()
                .storageCredentials ?: storageProperties.defaultStorageCredentials()
        }
    }
}
