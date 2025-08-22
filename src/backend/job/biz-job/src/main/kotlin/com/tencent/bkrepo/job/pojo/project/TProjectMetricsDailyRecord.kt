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

package com.tencent.bkrepo.job.pojo.project

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 项目用量每日采点记录表
 */
@Document(collection = "project_metrics_daily_record")
@CompoundIndexes(
    CompoundIndex(
        name = "project_created_idx",
        def = "{'projectId': 1,'createdDate': 1}",
        background = true,
        unique = true
    ),
    CompoundIndex(
        name = "created_day_idx",
        def = "{'createdDay': 1}",
        background = true
    ),
    )
data class TProjectMetricsDailyRecord(
    var projectId: String,
    var nodeNum: Long,
    var capSize: Long,
    var pipelineCapSize: Long = 0,
    var customCapSize: Long = 0,
    var helmRepoCapSize: Long = 0,
    var dockerRepoCapSize: Long = 0,
    val active: Boolean = true,
    var enabled: Boolean?,
    val createdDate: LocalDateTime,
    var createdDay: String? = null
)
