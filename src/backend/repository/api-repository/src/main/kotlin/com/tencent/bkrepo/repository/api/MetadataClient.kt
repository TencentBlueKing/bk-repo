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

package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.constant.REPOSITORY_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 节点元数据服务接口
 */
@Tag(name = "节点元数据服务接口")
@Primary
@FeignClient(REPOSITORY_SERVICE_NAME, contextId = "MetadataClient")
@Deprecated("replace with MetadataService")
@RequestMapping("/service/metadata")
interface MetadataClient {
    @Operation(summary = "查询节点所有元数据")
    @GetMapping("/list/{projectId}/{repoName}")
    fun listMetadata(
        @Parameter(name = "所属项目", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = true)
        @PathVariable repoName: String,
        @Parameter(name = "节点完整路径", required = true)
        @RequestParam fullPath: String
    ): Response<Map<String, Any>>

    @Operation(summary = "创建/更新元数据列表")
    @PostMapping("/save")
    fun saveMetadata(@RequestBody request: MetadataSaveRequest): Response<Void>

    @Operation(summary = "删除元数据")
    @DeleteMapping("/delete")
    fun deleteMetadata(@RequestBody request: MetadataDeleteRequest): Response<Void>

    @Operation(summary = "添加禁用元数据")
    @PostMapping("/forbid")
    fun addForbidMetadata(@RequestBody request: MetadataSaveRequest): Response<Void>
}
