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

package com.tencent.bkrepo.rpm.servcie

import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.rpm.pojo.Basic
import com.tencent.bkrepo.rpm.pojo.RpmArtifactVersionData
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class RpmService(
    private val packageClient: PackageClient,
    @Qualifier("com.tencent.bkrepo.repository.api.NodeClient")
    private val nodeClient: NodeClient,
    private val stageClient: StageClient
) {
    fun versionDetail(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String
    ): RpmArtifactVersionData? {
        val trueVersion = packageClient.findVersionByName(
            projectId,
            repoName,
            packageKey,
            version
        ).data ?: return null
        val artifactPath = trueVersion.contentPath ?: return null
        val node = nodeClient.getNodeDetail(projectId, repoName, artifactPath).data ?: return null
        val stageTag = stageClient.query(projectId, repoName, packageKey, version).data
        val packageVersion = packageClient.findVersionByName(
            projectId, repoName, packageKey, version
        ).data
        val count = packageVersion?.downloads ?: 0
        val rpmArtifactBasic = Basic(
            node.path,
            node.name,
            version,
            node.size, node.fullPath,
            node.createdBy, node.createdDate,
            node.lastModifiedBy, node.lastModifiedDate,
            count,
            node.sha256,
            node.md5,
            stageTag,
            null
        )
        return RpmArtifactVersionData(rpmArtifactBasic, packageVersion?.packageMetadata)
    }
}
