/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.packages

import com.tencent.bkrepo.repository.pojo.download.DetailsQueryRequest
import com.tencent.bkrepo.repository.pojo.download.DownloadsMigrationRequest
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadsDetails
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadsSummary
import com.tencent.bkrepo.repository.pojo.download.SummaryQueryRequest

interface PackageDownloadsService {

    /**
     * 记录包下载
     *
     * @param record 包下载记录
     */
    fun record(record: PackageDownloadRecord)

    /**
     * 数据迁移
     *
     * @param request 迁移请求
     */
    fun migrate(request: DownloadsMigrationRequest)

    /**
     * 查询包下载记录详情
     *
     * @param request 包下载记录查询请求
     */
    fun queryDetails(request: DetailsQueryRequest): PackageDownloadsDetails

    /**
     * 查询包下载记录总览
     *
     * @param request 包下载记录查询请求
     */
    fun querySummary(request: SummaryQueryRequest): PackageDownloadsSummary
}
