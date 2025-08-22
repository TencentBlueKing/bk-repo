/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.maven.service.impl.edge

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.maven.api.MavenMetadataClient
import com.tencent.bkrepo.maven.dao.MavenMetadataDao
import com.tencent.bkrepo.maven.model.TMavenMetadataRecord
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.pojo.MavenMetadataSearchPojo
import com.tencent.bkrepo.maven.pojo.metadata.MavenMetadataRequest
import com.tencent.bkrepo.maven.service.MavenMetadataService
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime

@Service
@Conditional(CommitEdgeEdgeCondition::class)
class EdgeMavenMetadataService(
    clusterProperties: ClusterProperties,
    private val mavenMetadataDao: MavenMetadataDao
) : MavenMetadataService(mavenMetadataDao) {
    private val centerMavenMetadataClient: MavenMetadataClient by lazy {
        FeignClientFactory.create(clusterProperties.center, "maven", clusterProperties.self.name)
    }

    override fun update(node: NodeCreateRequest) {
        with(node) {
            val metadata = node.nodeMetadata?.associate { Pair(it.key, it.value) }
            val (criteria, mavenVersion) = nodeCriteria(
                projectId = node.projectId,
                repoName = node.repoName,
                metadata = metadata,
                fullPath = node.fullPath
            )
            if (criteria == null || mavenVersion == null) return

            // 更新center节点数据
            val request = MavenMetadataRequest(
                projectId = projectId,
                repoName = repoName,
                groupId = metadata?.get("groupId") as String,
                artifactId = mavenVersion.artifactId,
                version = mavenVersion.version,
                extension = mavenVersion.packaging,
                buildNo = mavenVersion.buildNo,
                timestamp = mavenVersion.timestamp,
                classifier = mavenVersion.classifier
            )
            centerMavenMetadataClient.update(request)
            update(request)
        }
    }

    override fun delete(mavenArtifactInfo: ArtifactInfo, node: NodeDetail?, mavenGavc: MavenGAVC?) {
        node?.let {
            val (criteria, mavenVersion) = nodeCriteria(
                projectId = node.projectId,
                repoName = node.repoName,
                metadata = node.metadata,
                fullPath = node.fullPath
            )
            if (criteria == null || mavenVersion == null) return
            val groupId = it.metadata["groupId"] as String
            val request = MavenMetadataRequest(
                projectId = it.projectId,
                repoName = it.repoName,
                groupId = groupId,
                artifactId = mavenVersion.artifactId,
                version = mavenVersion.version,
                extension = mavenVersion.packaging,
                timestamp = mavenVersion.timestamp,
                classifier = mavenVersion.classifier,
            )
            centerMavenMetadataClient.delete(request)
            delete(request)
        }

        mavenGavc?.let {
            val request = MavenMetadataRequest(
                projectId = mavenArtifactInfo.projectId,
                repoName = mavenArtifactInfo.repoName,
                groupId = mavenGavc.groupId,
                artifactId = mavenGavc.artifactId,
                version = mavenGavc.version
            )

            centerMavenMetadataClient.delete(request)
            delete(request)
        }
    }

    override fun findAndModify(mavenMetadataSearchPojo: MavenMetadataSearchPojo): TMavenMetadataRecord {
        with(mavenMetadataSearchPojo) {
            val request = MavenMetadataRequest(
                projectId = projectId,
                repoName = repoName,
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                extension = extension,
                classifier = classifier,
                timestamp = ZonedDateTime.now(ZoneId.of("UTC")).format(formatter)
            )
            val result = mavenMetadataDao.findAndModify(request, incBuildNo = true, upsert = true, returnNew = true)
            centerMavenMetadataClient.update(request.copy(buildNo = result!!.buildNo))
            return result
        }
    }
}
