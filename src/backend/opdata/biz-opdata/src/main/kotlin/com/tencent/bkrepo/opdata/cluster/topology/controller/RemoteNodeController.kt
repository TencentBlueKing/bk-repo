/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.cluster.topology.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteNodePageVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteNodeSortBy
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteNodeTaskVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteSummaryVO
import com.tencent.bkrepo.opdata.cluster.topology.service.RemoteNodeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * REMOTE 节点列表与详情查询接口。
 */
@RestController
@RequestMapping("/api/cluster/topology/remote")
@Principal(PrincipalType.ADMIN)
class RemoteNodeController(
    private val remoteNodeService: RemoteNodeService
) {

    /**
     * 分页查询 REMOTE 节点。
     */
    @GetMapping("/page")
    fun page(
        @RequestParam(name = "keyword", required = false) keyword: String?,
        @RequestParam(name = "sortBy", required = false) sortBy: String?,
        @RequestParam(name = "sortOrder", required = false, defaultValue = "desc") sortOrder: String,
        @RequestParam(name = "lastUsedAfter", required = false) lastUsedAfter: String?,
        @RequestParam(name = "lastUsedBefore", required = false) lastUsedBefore: String?,
        @RequestParam(name = "pageNumber", required = false, defaultValue = "1") pageNumber: Int,
        @RequestParam(name = "pageSize", required = false, defaultValue = "20") pageSize: Int
    ): Response<RemoteNodePageVO> {
        val result = remoteNodeService.pageQuery(
            keyword = keyword,
            sortBy = RemoteNodeSortBy.parse(sortBy),
            sortOrder = sortOrder,
            lastUsedAfter = parseOrNull(lastUsedAfter),
            lastUsedBefore = parseOrNull(lastUsedBefore),
            pageNumber = pageNumber,
            pageSize = pageSize
        )
        return ResponseBuilder.success(result)
    }

    /**
     * REMOTE 节点全局汇总统计。
     */
    @GetMapping("/summary")
    fun summary(): Response<RemoteSummaryVO> {
        return ResponseBuilder.success(remoteNodeService.summary())
    }

    /**
     * 查询某个 REMOTE 节点关联的所有同步任务。
     *
     * 注意：节点名可能包含 '/' 等特殊字符，使用 query 参数避免路径解析问题。
     */
    @GetMapping("/tasks")
    fun tasks(@RequestParam("name") name: String): Response<List<RemoteNodeTaskVO>> {
        return ResponseBuilder.success(remoteNodeService.getNodeTaskList(name))
    }

    private fun parseOrNull(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse(raw) }
            .getOrElse { LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
    }
}
