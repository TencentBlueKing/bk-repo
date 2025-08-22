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
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.project.RepoRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 仓库服务接口
 */
@Tag(name = "仓库服务接口")
@FeignClient(REPOSITORY_SERVICE_NAME, contextId = "RepositoryClient", primary = false)
@RequestMapping("/service/repo")
@Deprecated("replace with RepositoryService")
interface RepositoryClient {

    @Operation(summary = "查询仓库信息")
    @GetMapping("/info/{projectId}/{repoName}")
    fun getRepoInfo(
        @Parameter(name = "所属项目", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = true)
        @PathVariable repoName: String
    ): Response<RepositoryInfo?>

    @Operation(summary = "查询仓库详情")
    @GetMapping("/detail/{projectId}/{repoName}")
    fun getRepoDetail(
        @Parameter(name = "所属项目", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = true)
        @PathVariable repoName: String,
        @Parameter(name = "仓库类型", required = true)
        @RequestParam type: String? = null
    ): Response<RepositoryDetail?>

    @Operation(summary = "列表查询项目所有仓库")
    @GetMapping("/list/{projectId}")
    fun listRepo(
        @Parameter(name = "项目id", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = false)
        @RequestParam name: String? = null,
        @Parameter(name = "仓库类型", required = false)
        @RequestParam type: String? = null
    ): Response<List<RepositoryInfo>>

    @Operation(summary = "仓库分页查询")
    @PostMapping("/rangeQuery")
    fun rangeQuery(@RequestBody request: RepoRangeQueryRequest): Response<Page<RepositoryInfo?>>

    @Operation(summary = "创建仓库")
    @PostMapping("/create")
    fun createRepo(@RequestBody request: RepoCreateRequest): Response<RepositoryDetail>

    @Operation(summary = "修改仓库")
    @PostMapping("/update")
    fun updateRepo(@RequestBody request: RepoUpdateRequest): Response<Void>

    @Operation(summary = "删除仓库")
    @DeleteMapping("/delete")
    fun deleteRepo(@RequestBody request: RepoDeleteRequest): Response<Void>

    @Operation(summary = "分页查询指定类型仓库")
    @GetMapping("/page/repoType/{page}/{size}/{repoType}")
    fun pageByType(
        @Parameter(name = "当前页", required = true, example = "0")
        @PathVariable page: Int,
        @Parameter(name = "分页大小", required = true, example = "20")
        @PathVariable size: Int,
        @Parameter(name = "仓库类型", required = true)
        @PathVariable repoType: String
    ): Response<Page<RepositoryDetail>>

    @Operation(summary = "查询项目下的有权限的仓库列表")
    @PostMapping("/permission/{userId}/{projectId}")
    fun listPermissionRepo(
        @PathVariable userId: String,
        @PathVariable projectId: String,
        @RequestBody option: RepoListOption
    ): Response<List<RepositoryInfo>>

    @Operation(summary = "查询仓库大小信息")
    @GetMapping("/stat/{projectId}/{repoName}")
    fun statRepo(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ): Response<NodeSizeInfo>

    @Operation(summary = "更新仓库存储")
    @PostMapping("/update/storage/{projectId}/{repoName}")
    fun updateStorageCredentialsKey(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam storageCredentialsKey: String?
    ): Response<Void>

    @Operation(summary = "重置仓库旧存储")
    @PostMapping("/unset/storage/{projectId}/{repoName}")
    fun unsetOldStorageCredentialsKey(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ): Response<Void>
}
