/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.project

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.pojo.project.ProjectUsageStatistics
import com.tencent.bkrepo.common.metadata.pojo.project.ProjectUsageStatisticsListOption

interface ProjectUsageStatisticsService {
    /**
     * 增加上传次数
     *
     * @param projectId 项目ID
     * @param reqCount 增加的请求次数
     * @param receivedBytes 增加上传流量大小
     * @param responseBytes 增加下载流量大小
     */
    fun inc(projectId: String, reqCount: Long = 0L, receivedBytes: Long = 0L, responseBytes: Long = 0L)

    /**
     * 获取项目使用情况列表
     *
     * @param options 查询参数
     *
     * @return 项目使用情况统计数据
     */
    fun page(options: ProjectUsageStatisticsListOption): Page<ProjectUsageStatistics>

    /**
     * 删除指定时间范围内的数据数据
     *
     * @param start 起始时间， 包含
     * @param end 结束时间，不包含
     */
    fun delete(start: Long? = null, end: Long)

    /**
     * 查询一段时间内的使用量总和
     *
     * @param start 统计的起始时间,inclusive
     * @param end 统计的结束时间,exclusive
     */
    fun sum(
        start: Long,
        end: Long = System.currentTimeMillis()
    ): Map<String, ProjectUsageStatistics>
}
