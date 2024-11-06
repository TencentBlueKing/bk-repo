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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.opdata.job.EmptyFolderStatJob
import com.tencent.bkrepo.opdata.job.pojo.EmptyFolderMetric
import com.tencent.bkrepo.opdata.pojo.node.FolderInfo
import com.tencent.bkrepo.opdata.pojo.node.ListOption
import com.tencent.bkrepo.opdata.repository.FolderMetricsRepository
import org.springframework.stereotype.Service

@Service
class NodeService(
    private val emptyFolderService: EmptyFolderStatJob,
    private val folderMetricsRepository: FolderMetricsRepository
    ) {

    fun getEmptyFolder(
        projectId: String,
        repoName: String,
        parentFolder: String = StringPool.SLASH
    ): List<EmptyFolderMetric> {
        return emptyFolderService.statFolderSize(projectId, repoName, parentFolder)
    }

    fun deleteEmptyFolder(projectId: String, repoName: String, parentFolder: String = StringPool.SLASH) {
        val emptyFolders = emptyFolderService.statFolderSize(projectId, repoName, parentFolder)
        emptyFolders.forEach {
            emptyFolderService.deleteEmptyFolder(projectId, repoName, it.objectId)
        }
    }

    fun getFirstLevelFolders(
        projectId: String,
        repoName: String,
        option: ListOption
    ): Page<FolderInfo> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val queryResult = folderMetricsRepository.findByProjectIdAndRepoNameOrderByCapSizeDesc(
                pageable = pageRequest, projectId = projectId, repoName = repoName
            ).map {
                FolderInfo(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    path = it.folderPath,
                    nodeNum = it.nodeNum,
                    capSize = it.capSize
                )
            }
            return Pages.ofResponse(pageRequest, queryResult.totalElements, queryResult.content)
        }
    }
}
