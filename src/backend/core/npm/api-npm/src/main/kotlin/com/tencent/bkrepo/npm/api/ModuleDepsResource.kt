/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.npm.pojo.module.des.ModuleDepsInfo
import com.tencent.bkrepo.npm.pojo.module.des.service.DepsCreateRequest
import com.tencent.bkrepo.npm.pojo.module.des.service.DepsDeleteRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "包依赖信息接口")
@RequestMapping("/service/module/deps")
interface ModuleDepsResource {
    @Operation(summary = "创建资源依赖关系")
    @PostMapping("/create")
    fun create(
        @RequestBody depsCreateRequest: DepsCreateRequest
    ): Response<ModuleDepsInfo>

    @Operation(summary = "批量创建资源依赖关系")
    @PostMapping("/batch/create")
    fun batchCreate(
        @RequestBody depsCreateRequest: List<DepsCreateRequest>
    ): Response<Void>

    @Operation(summary = "删除单个资源的单个依赖关系")
    @DeleteMapping("/delete")
    fun delete(
        @RequestBody depsDeleteRequest: DepsDeleteRequest
    ): Response<Void>

    @Operation(summary = "删除单个资源的所有资源依赖关系")
    @DeleteMapping("/delete/all")
    fun deleteAllByName(
        @RequestBody depsDeleteRequest: DepsDeleteRequest
    ): Response<Void>

    @Operation(summary = "查询某个资源节点被依赖的单个资源名称")
    @GetMapping("/find/{projectId}/{repoName}")
    fun find(
        @Parameter(name = "所属项目", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = true)
        @PathVariable repoName: String,
        @Parameter(name = "资源名称", required = true)
        @RequestParam name: String,
        @Parameter(name = "被依赖资源名称", required = true)
        @RequestParam deps: String
    ): Response<ModuleDepsInfo>

    @Operation(summary = "列表查询某个资源节点被依赖的所有资源名称")
    @GetMapping("/list/{projectId}/{repoName}")
    fun list(
        @Parameter(name = "所属项目", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = true)
        @PathVariable repoName: String,
        @Parameter(name = "资源名称", required = true)
        @RequestParam name: String
    ): Response<List<ModuleDepsInfo>>

    @Operation(summary = "分页查询某个资源节点被依赖的所有资源名称")
    @GetMapping("/page/{projectId}/{repoName}/{page}/{size}")
    fun page(
        @Parameter(name = "所属项目", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = true)
        @PathVariable repoName: String,
        @Parameter(name = "当前页", required = true, example = "0")
        @PathVariable page: Int,
        @Parameter(name = "分页大小", required = true, example = "20")
        @PathVariable size: Int,
        @Parameter(name = "资源名称", required = true)
        @RequestParam name: String
    ): Response<Page<ModuleDepsInfo>>
}
