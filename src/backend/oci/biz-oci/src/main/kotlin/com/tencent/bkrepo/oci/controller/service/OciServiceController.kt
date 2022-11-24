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

package com.tencent.bkrepo.oci.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.oci.api.OciClient
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.springframework.web.bind.annotation.RestController

@RestController
class OciServiceController(
    private val ociOperationService: OciOperationService,
    private val nodeClient: NodeClient,
    private val repositoryClient: RepositoryClient
) : OciClient {

    override fun updateManifest(
        projectId: String,
        repoName: String,
        packageName: String,
        tag: String,
        sha256: String,
        sourceType: ArtifactChannel?
    ): Response<Void> {
        val ociArtifactInfo = OciManifestArtifactInfo(
            projectId, repoName, packageName, "", tag, false
        )
        val nodeInfo = nodeClient.getNodeDetail(projectId, repoName, ociArtifactInfo.getArtifactFullPath()).data
            ?: throw NodeNotFoundException(
                "${ociArtifactInfo.getArtifactFullPath()} not found in repo in $projectId|$repoName"
            )
        val ociDigest = OciDigest.fromSha256(sha256)
        val repositoryDetail = repositoryClient.getRepoDetail(projectId, repoName).data
            ?: throw RepoNotFoundException("$projectId|$repoName")
        ociOperationService.updateOciInfo(
            ociArtifactInfo = ociArtifactInfo,
            digest = ociDigest,
            nodeDetail = nodeInfo,
            storageCredentials = repositoryDetail.storageCredentials,
            sourceType = sourceType
        )
        return ResponseBuilder.success()
    }
}
