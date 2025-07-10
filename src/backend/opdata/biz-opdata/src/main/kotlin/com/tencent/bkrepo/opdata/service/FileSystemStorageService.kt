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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.opdata.model.TPathStatMetric
import com.tencent.bkrepo.opdata.pojo.storage.FileStorageListOption
import com.tencent.bkrepo.opdata.pojo.storage.RootPathStorageMetric
import com.tencent.bkrepo.opdata.pojo.storage.SubFolderStorageMetric
import com.tencent.bkrepo.opdata.repository.FileSystemMetricsRepository
import org.springframework.stereotype.Service

/**
 * 统计配置的分布式存储的容量使用情况
 */
@Service
class FileSystemStorageService(
    private val fileSystemMetricsRepository: FileSystemMetricsRepository
) {

    fun getFileSystemStorageMetrics(option: FileStorageListOption): Page<RootPathStorageMetric> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val queryResult = fileSystemMetricsRepository.findByRootPathOrderByTotalSizeDesc(
                pageable = pageRequest, rootPath = rootPath
            ).map { convertToRootPathStorageMetric(it) }
            return Pages.ofResponse(pageRequest, queryResult.totalElements, queryResult.content)
        }
    }

    fun getFileSystemStorageMetrics(): List<String> {
        return fileSystemMetricsRepository.findTPathStatMetricByRootPath()
            .map { it.path }
    }

    fun getFileSystemStorageMetricDetails(option: FileStorageListOption): Page<SubFolderStorageMetric> {
        with(option) {
            if (option.rootPath.isNullOrEmpty())
                throw ErrorCodeException(CommonMessageCode.PARAMETER_EMPTY, "rootPath")
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val queryResult = fileSystemMetricsRepository.findByRootPathOrderByTotalSizeDesc(
                pageable = pageRequest, rootPath = rootPath
            ).map { convertToSubFolderStorageMetric(it) }
            return Pages.ofResponse(pageRequest, queryResult.totalElements, queryResult.content)
        }
    }

    companion object {
        fun convertToRootPathStorageMetric(tMetric: TPathStatMetric): RootPathStorageMetric {
            with(tMetric) {
                return RootPathStorageMetric(
                    path = this.path,
                    totalFileCount = this.totalFileCount,
                    totalFolderCount = this.totalFolderCount,
                    totalSize = this.totalSize,
                    usedPercent = this.usedPercent ?: 0.0,
                    totalSpace = this.totalSpace ?: 0
                )
            }
        }

        fun convertToSubFolderStorageMetric(tMetric: TPathStatMetric): SubFolderStorageMetric {
            with(tMetric) {
                return SubFolderStorageMetric(
                    path = this.path,
                    totalSize = this.totalSize
                )
            }
        }
    }
}
