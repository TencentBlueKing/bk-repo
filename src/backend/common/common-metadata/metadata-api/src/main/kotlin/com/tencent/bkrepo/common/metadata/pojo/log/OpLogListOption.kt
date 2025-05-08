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

package com.tencent.bkrepo.common.metadata.pojo.log

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.query.model.Sort
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "操作日志列表选项")
data class OpLogListOption(
    @get:Schema(title = "分页数")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @get:Schema(title = "分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @get:Schema(title = "所属项目Id")
    val projectId: String,
    @get:Schema(title = "所属仓库名称")
    val repoName: String,
    @get:Schema(title = "节点完整路径")
    val resourceKey: String,
    @get:Schema(title = "事件类型")
    val eventType: EventType,
    @get:Schema(title = "sha256校验值")
    val sha256: String?,
    @get:Schema(title = "流水线Id")
    val pipelineId: String?,
    @get:Schema(title = "流水线构建Id")
    val buildId: String?,
    @get:Schema(title = "下载用户Id")
    val userId: String?,
    @get:Schema(title = "查询起始时间")
    val startTime: LocalDateTime = LocalDateTime.now().minusMonths(3L),
    @get:Schema(title = "查询截至时间")
    val endTime: LocalDateTime = LocalDateTime.now(),
    @get:Schema(title = "时间排序方向")
    val direction: Sort.Direction = Sort.Direction.DESC
)
