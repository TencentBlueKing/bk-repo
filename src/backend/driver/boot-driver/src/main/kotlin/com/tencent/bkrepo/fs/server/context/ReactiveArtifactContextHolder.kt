/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.context

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.fs.server.RepositoryCache
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail

object ReactiveArtifactContextHolder {

    suspend fun getRepoDetail(): RepositoryDetail {
        val exchange = ReactiveRequestContextHolder.getWebExchange()
        val attributes = exchange.attributes
        val repoDetail = attributes[REPO_KEY]
        repoDetail?.let {
            require(repoDetail is RepositoryDetail)
            return repoDetail
        }
        val projectId = attributes[PROJECT_ID].toString()
        val repoName = attributes[REPO_NAME].toString()
        return RepositoryCache.getRepoDetail(projectId, repoName).apply {
            attributes[REPO_KEY] = this
        }
    }

    suspend fun getArtifactInfo(): ArtifactInfo? {
        val exchange = ReactiveRequestContextHolder.getWebExchange()
        return exchange.attributes[ARTIFACT_INFO_KEY] as? ArtifactInfo
    }
}
