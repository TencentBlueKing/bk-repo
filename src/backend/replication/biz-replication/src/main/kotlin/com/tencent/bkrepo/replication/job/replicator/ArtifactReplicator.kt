/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.job.replicator

import com.tencent.bkrepo.replication.job.ReplicaContext
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.springframework.stereotype.Component

/**
 * 构件同步器
 * metadata + blob
 */
@Component
class ArtifactReplicator: ScheduledReplicator() {
    override fun replicaProject(context: ReplicaContext) {
        with(context) {
            val localProject = localDataManager.findProjectById(localProjectId)
            require(localProject != null) { "Local project[$localProjectId] does not exist" }
            if (remoteDataManager.findProjectById(remoteProjectId) != null) {
                return
            }
            val request = ProjectCreateRequest(
                name = remoteProjectId,
                displayName = remoteProjectId,
                description = localProject.description,
                operator = localProject.createdBy
            )
            remoteDataManager.replicaProject(context, request)
        }
    }

    override fun replicaRepo(context: ReplicaContext) {
        with(context) {
            val localRepo = localDataManager.findRepoByName(localProjectId, localRepoName, localRepoType.name)
            require(localRepo != null) { "Local repository[$localRepoName] does not exist" }
            if (remoteDataManager.findRepoByName(remoteProjectId, remoteRepoName, remoteRepoType.name) != null) {
                return
            }
            val request = RepoCreateRequest(
                projectId = remoteProjectId,
                name = remoteRepoName,
                type = remoteRepoType,
                category = localRepo.category,
                public = localRepo.public,
                description = localRepo.description,
                configuration = localRepo.configuration,
                operator = localRepo.createdBy
            )
            remoteDataManager.replicaRepo(context, request)
        }
    }
}
