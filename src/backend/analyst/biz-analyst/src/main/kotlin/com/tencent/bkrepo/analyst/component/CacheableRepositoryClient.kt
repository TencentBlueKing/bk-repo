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

package com.tencent.bkrepo.analyst.component

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class CacheableRepositoryClient(private val repositoryService: RepositoryService) {
    private val repoInfoCache: LoadingCache<String, RepositoryInfo> = CacheBuilder.newBuilder()
        .maximumSize(DEFAULT_REPO_INFO_CACHE_SIZE)
        .expireAfterWrite(DEFAULT_REPO_INFO_CACHE_DURATION_MINUTES, TimeUnit.MINUTES)
        .build(CacheLoader.from { key -> loadRepoInfo(key!!) })

    fun get(projectId: String, repoName: String): RepositoryInfo {
        return repoInfoCache.get(generateKey(projectId, repoName))
    }

    private fun loadRepoInfo(key: String): RepositoryInfo {
        val (projectId, repoName) = fromKey(key)
        val repo = repositoryService.getRepoInfo(projectId, repoName)
        return repo ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, key)
    }

    private fun fromKey(key: String): Pair<String, String> {
        val indexOfRepoSplit = key.indexOf(REPO_SPLIT)
        val projectId = key.substring(0, indexOfRepoSplit)
        val repoName = key.substring(indexOfRepoSplit + REPO_SPLIT.length, key.length)
        return Pair(projectId, repoName)
    }

    private fun generateKey(projectId: String, repoName: String) = "$projectId$REPO_SPLIT$repoName"

    companion object {
        private val logger = LoggerFactory.getLogger(CacheableRepositoryClient::class.java)
        private const val REPO_SPLIT = "::repo::"
        private const val DEFAULT_REPO_INFO_CACHE_SIZE = 1000L
        private const val DEFAULT_REPO_INFO_CACHE_DURATION_MINUTES = 60L
    }
}
