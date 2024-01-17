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

package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.job.batch.ProjectRepoMetricsStatJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.pojo.project.TProjectMetrics
import com.tencent.bkrepo.job.pojo.project.TRepoMetrics
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

data class ProjectRepoMetricsStatJobContext(
    var metrics: ConcurrentHashMap<String, ProjectMetrics> = ConcurrentHashMap(),
    var statDate: LocalDateTime,
    var activeProjects: Set<String> = emptySet()
) : JobContext() {

    data class ProjectMetrics(
        val projectId: String,
        var nodeNum: LongAdder = LongAdder(),
        var capSize: LongAdder = LongAdder(),
        val repoMetrics: ConcurrentHashMap<String, RepoMetrics> = ConcurrentHashMap()
    ) {
        fun addRepoMetrics(row: ProjectRepoMetricsStatJob.Node, repoType: String, credentialsKey: String) {

            val repo = repoMetrics.getOrPut(row.repoName) { RepoMetrics(row.repoName, credentialsKey, type = repoType) }
            if (!row.folder) {
                repo.num.increment()
            } else {
                val nodeNum = row.nodeNum ?: 0
                repo.num.add(nodeNum)
            }
            repo.size.add(row.size)
        }

        fun toDO(statDate: LocalDateTime = LocalDateTime.now()): TProjectMetrics {
            val repoMetrics = ArrayList<TRepoMetrics>(repoMetrics.size)
            this.repoMetrics.values.forEach { repo ->
                val num = repo.num.toLong()
                val size = repo.size.toLong()
                // 有效仓库的统计数据
                if (num != 0L && size != 0L) {
                    repoMetrics.add(repo.toDO())
                }
            }

            return TProjectMetrics(
                projectId = projectId,
                nodeNum = nodeNum.toLong(),
                capSize = capSize.toLong(),
                repoMetrics = repoMetrics,
                createdDate = statDate
            )
        }
    }

    data class RepoMetrics(
        val repoName: String,
        val credentialsKey: String = "default",
        var size: LongAdder = LongAdder(),
        var num: LongAdder = LongAdder(),
        var type: String,
    ) {
        fun toDO(): TRepoMetrics {
            return TRepoMetrics(
                repoName = repoName,
                credentialsKey = credentialsKey,
                size = size.toLong(),
                num = num.toLong(),
                type = type
            )
        }
    }
}
