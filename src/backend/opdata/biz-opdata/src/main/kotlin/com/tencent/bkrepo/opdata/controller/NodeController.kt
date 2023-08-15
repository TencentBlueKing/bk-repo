/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.operate.api.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.job.pojo.EmptyFolderMetric
import com.tencent.bkrepo.opdata.pojo.node.FolderInfo
import com.tencent.bkrepo.opdata.pojo.node.ListOption
import com.tencent.bkrepo.opdata.service.NodeService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/nodeOperation")
@Principal(PrincipalType.ADMIN)
class NodeController(
    private val nodeService: NodeService
) {

    /**
     * 获取当前仓库目录下的空目录列表
     */
    @GetMapping("/emptyFolders/{projectId}/{repoName}")
    @LogOperate(type = "EMPTY_FOLDER_LIST")
    fun getEmptyFolder(
        @PathVariable("projectId")projectId: String,
        @PathVariable("repoName")repoName: String,
        @RequestParam parentFolder: String
    ): Response<List<EmptyFolderMetric>> {
        return ResponseBuilder.success(nodeService.getEmptyFolder(projectId, repoName, parentFolder))
    }

    /**
     * 删除当前仓库目录下的空目录列表
     */
    @DeleteMapping("/emptyFolders/{projectId}/{repoName}")
    @LogOperate(type = "EMPTY_FOLDER_DELETE")
    fun deleteEmptyFolders(
        @PathVariable("projectId")projectId: String,
        @PathVariable("repoName")repoName: String,
        @RequestParam parentFolder: String
    ): Response<Void> {
        nodeService.deleteEmptyFolder(projectId, repoName, parentFolder)
        return ResponseBuilder.success()
    }

    /**
     * 获取当前仓库目录下的一级目录统计信息
     */
    @GetMapping("/firstLevelFolder/{projectId}/{repoName}")
    @LogOperate(type = "FIRST_LEVEL_FOLDER_STATISTICS")
    fun getFirstLevelFolders(
        @PathVariable("projectId")projectId: String,
        @PathVariable("repoName")repoName: String,
        option: ListOption
    ): Response<Page<FolderInfo>> {
        return ResponseBuilder.success(nodeService.getFirstLevelFolders(projectId, repoName, option))
    }
}
