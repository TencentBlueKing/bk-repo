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

package com.tencent.bkrepo.pypi.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.artifact.util.version.SemVersion
import com.tencent.bkrepo.common.artifact.util.version.SemVersionParser
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PypiWebService(
    private val nodeClient: NodeClient,
    private val packageClient: PackageClient
) : ArtifactService() {

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun deletePackage(pypiArtifactInfo: PypiArtifactInfo, packageKey: String) {
        val context = ArtifactRemoveContext()
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun delete(pypiArtifactInfo: PypiArtifactInfo, packageKey: String, version: String?, contentPath: String?) {
        val context = ArtifactRemoveContext()
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun artifactDetail(pypiArtifactInfo: PypiArtifactInfo, packageKey: String, version: String?): Any? {
        val context = ArtifactQueryContext()
        return repository.query(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun versionListPage(
        pypiArtifactInfo: PypiArtifactInfo,
        packageKey: String,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageVersion> {
        val data = nodeClient.listNodePage(
            projectId = pypiArtifactInfo.projectId,
            repoName = pypiArtifactInfo.repoName,
            path = PathUtils.normalizePath(PackageKeys.resolvePypi(packageKey)),
            option = NodeListOption(pageNumber, pageSize, includeFolder = false, deep = true)
        ).data!!
        val packageVersionList = data.records.map {
            val version = parseSemVersion(it.path).toString()
            val packageVersion = packageClient.findVersionByName(it.projectId, it.repoName, packageKey, version).data
            buildPackageVersion(it, version, packageVersion)
        }.sortedWith(compareByDescending<PackageVersion> { it.name }.thenByDescending { it.createdDate })
        return Page(pageNumber, pageSize, data.totalRecords, packageVersionList)
    }

    /**
     * python存在相同版本号，但是语言版本、系统不同的包
     */
    private fun buildPackageVersion(
        it: NodeInfo,
        version: String,
        packageVersion: PackageVersion?
    ) = PackageVersion(
        createdBy = it.createdBy,
        createdDate = LocalDateTime.parse(it.createdDate),
        lastModifiedBy = it.lastModifiedBy,
        lastModifiedDate = LocalDateTime.parse(it.lastModifiedDate),
        name = version,
        size = it.size,
        downloads = packageVersion?.downloads ?: 0,
        stageTag = packageVersion?.stageTag ?: emptyList(),
        metadata = it.metadata ?: emptyMap(),
        packageMetadata = it.nodeMetadata ?: emptyList(),
        tags = packageVersion?.tags ?: emptyList(),
        extension = packageVersion?.extension ?: emptyMap(),
        contentPath = it.fullPath,
        clusterNames = it.clusterNames
    )

    /**
     * 解析版本, path格式 /packageName/packageVersion/packageFilename
     */
    private fun parseSemVersion(path: String) = try {
        SemVersionParser.parse(path.split("/")[2])
    } catch (ignore: IndexOutOfBoundsException) {
        SemVersion(0, 0, 0)
    } catch (ignore: IllegalArgumentException) {
        SemVersion(0, 0, 0)
    }
}
