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

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.pojo.storage.FileStorageListOption
import com.tencent.bkrepo.opdata.pojo.storage.RootPathStorageMetric
import com.tencent.bkrepo.opdata.pojo.storage.SubFolderStorageMetric
import com.tencent.bkrepo.opdata.service.FileSystemStorageService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fileSystem")
@Principal(PrincipalType.ADMIN)
class FileStorageController(
    private val fileSystemStorageService: FileSystemStorageService
) {

    /**
     * 挂载分布式文件系统节点统计功能
     */
    @GetMapping("/storage/metrics")
    @LogOperate(type = "FILE_SYSTEM_METRICS")
    fun list(option: FileStorageListOption): Response<Page<RootPathStorageMetric>> {
        return ResponseBuilder.success(fileSystemStorageService.getFileSystemStorageMetrics(option))
    }

    /**
     * 挂载分布式文件系统节点详情下拉列表
     */
    @GetMapping("/storage/metrics/list")
    fun list(): Response<List<String>> {
        return ResponseBuilder.success(fileSystemStorageService.getFileSystemStorageMetrics())
    }

    /**
     * 统计某个挂载路径下子目录文件大小
     */
    @GetMapping("/storage/metricsDetail")
    @LogOperate(type = "FILE_SYSTEM_METRICS_DETAIL")
    fun listDetails(option: FileStorageListOption): Response<Page<SubFolderStorageMetric>> {
        return ResponseBuilder.success(fileSystemStorageService.getFileSystemStorageMetricDetails(option))
    }
}
