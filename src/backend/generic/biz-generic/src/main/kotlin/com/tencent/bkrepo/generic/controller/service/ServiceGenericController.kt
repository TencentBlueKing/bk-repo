/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.generic.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.api.GenericClient
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.service.DownloadService
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.RestController

@Primary
@RestController
class ServiceGenericController(
    private val downloadService: DownloadService,
) : GenericClient {
    override fun getNodeDetail(projectId: String, repoName: String, fullPath: String): Response<NodeDetail?> {
        prepareRepo(projectId, repoName)
        val nodeDetail = downloadService.query(GenericArtifactInfo(projectId, repoName, fullPath))
        return if (nodeDetail is NodeDetail) {
            ResponseBuilder.success(nodeDetail)
        } else {
            ResponseBuilder.success(null)
        }
    }

    override fun search(projectId: String, repoName: String, queryModel: QueryModel): Response<List<Any>> {
        prepareRepo(projectId, repoName)
        return ResponseBuilder.success(downloadService.search(queryModel))
    }

    private fun prepareRepo(projectId: String, repoName: String) {
        // 设置artifact，避免创建context失败
        HttpContextHolder.getRequest().setAttribute(ARTIFACT_INFO_KEY, ArtifactInfo(projectId, repoName, ""))
        // 填充repo缓存，避免创建context失败
        ArtifactContextHolder.getRepoDetail()
    }
}
