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

package com.tencent.bkrepo.common.metadata.service.packages.impl

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageRestoreOption
import com.tencent.bkrepo.common.metadata.service.packages.PackageRestoreService
import com.tencent.bkrepo.common.metadata.service.packages.impl.PackageServiceImpl.Companion.convert
import com.tencent.bkrepo.common.metadata.util.PackageQueryHelper
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageDeletedPoint
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageRestoreResult
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageSummary
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageVersion
import com.tencent.bkrepo.common.metadata.pojo.packages.VersionRestoreOption
import org.springframework.stereotype.Service

@Service
class PackageRestoreServiceImpl(
    private val packageDao: PackageDao,
) : PackageRestoreService {

    override fun getDeletedPackage(artifact: ArtifactInfo, key: String): List<PackageSummary> {
        val query = PackageQueryHelper.packageDeletedPointQuery(artifact.projectId, artifact.repoName, key)
        val deletedPackages = packageDao.find(query)
        return deletedPackages.map {
            convert(it)!!
        }
    }

    override fun getDeletedPackageVersions(artifact: ArtifactInfo, key: String, version: String?): List<PackageVersion> {
        TODO("Not yet implemented")
    }


    override fun restorePackage(artifact: ArtifactInfo, packageRestoreOption: PackageRestoreOption): PackageRestoreResult {
        TODO("Not yet implemented")
    }

    override fun restoreVersion(artifact: ArtifactInfo, versionRestoreOption: VersionRestoreOption): PackageRestoreResult {
        TODO("Not yet implemented")
    }

    override fun listDeletedPoint(artifact: ArtifactInfo, key: String, version: String?): List<PackageDeletedPoint> {
        // 查询包删除点
        TODO("Not yet implemented")
    }
}