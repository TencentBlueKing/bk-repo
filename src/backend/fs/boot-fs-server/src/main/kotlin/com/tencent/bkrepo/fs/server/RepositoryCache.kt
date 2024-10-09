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

package com.tencent.bkrepo.fs.server

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.metadata.client.RRepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.reactor.awaitSingle

/**
 * 仓库缓存
 *
 * 因为仓库需要通过请求获取，并且一般情况下没有变化，所以这里进行缓存
 * */
class RepositoryCache(
    rRepositoryClient: RRepositoryClient
) {

    init {
        Companion.rRepositoryClient = rRepositoryClient
    }

    companion object {
        private lateinit var rRepositoryClient: RRepositoryClient
        private val repositoryDetailCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<RepositoryId, RepositoryDetail>()

        suspend fun getRepoDetail(projectId: String, repoName: String): RepositoryDetail {
            val repositoryId = RepositoryId(projectId, repoName)
            return repositoryDetailCache.getIfPresent(repositoryId) ?: run {
                queryRepoDetail(repositoryId).apply { repositoryDetailCache.put(repositoryId, this) }
            }
        }

        private suspend fun queryRepoDetail(repositoryId: RepositoryId): RepositoryDetail {
            with(repositoryId) {
                return rRepositoryClient.getRepoDetail(projectId, repoName).awaitSingle().data
                    ?: throw RepoNotFoundException(repositoryId.toString())
            }
        }
    }

    private data class RepositoryId(val projectId: String, val repoName: String) {
        override fun toString(): String {
            return "$projectId/$repoName"
        }
    }
}
