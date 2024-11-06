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

package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.config.properties.RepoJobProperties
import java.time.Duration
import kotlin.reflect.KClass

abstract class DefaultRepoJob(
    properties: RepoJobProperties
) : DefaultContextMongoDbJob<DefaultRepoJob.ProxyRepoData>(properties) {

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun entityClass(): KClass<ProxyRepoData> {
        return ProxyRepoData::class
    }

    override fun mapToEntity(row: Map<String, Any?>): ProxyRepoData {
        return ProxyRepoData(row)
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    /**
     * 只针对remote仓库或者composite代理仓库进行刷新
     */
    fun checkConfigType(configuration: RepositoryConfiguration): Boolean {
        if (configuration is CompositeConfiguration) {
            if (configuration.proxy.channelList.isNotEmpty()) return true
        }
        if (configuration is RemoteConfiguration) return true
        return false
    }

    data class ProxyRepoData(private val map: Map<String, Any?>) {
        val name: String by map
        val projectId: String by map
        val type: String by map
        val configuration: String by map
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME = "repository"
    }
}
