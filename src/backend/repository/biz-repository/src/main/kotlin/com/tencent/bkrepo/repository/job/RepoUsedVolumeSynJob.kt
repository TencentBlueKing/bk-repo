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

package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.job.base.CenterNodeJob
import com.tencent.bkrepo.repository.service.node.impl.NodeBaseService
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * 仓库已使用容量的同步任务
 */
@Component
class RepoUsedVolumeSynJob(
    private val nodeBaseService: NodeBaseService
) : CenterNodeJob() {

    private val repositoryDao = nodeBaseService.repositoryDao

    override fun run() {
        var pageNum = 1
        val pageSize = 1000
        var querySize: Int
        do {
            val pageRequest = Pages.ofRequest(pageNum, pageSize)
            val repoList = repositoryDao.find(Query().with(pageRequest))
            repoList.forEach {
                val usedVolume = nodeBaseService.computeSize(ArtifactInfo(it.projectId, it.name, PathUtils.ROOT)).size
                it.used = usedVolume
                repositoryDao.save(it)
            }
            querySize = repoList.size
            pageNum ++
        } while (querySize == pageSize)
    }
}
