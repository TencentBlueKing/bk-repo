/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.separation.service.impl.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.job.separation.pojo.RecoveryNodeInfo
import com.tencent.bkrepo.job.separation.pojo.RecoveryVersionInfo
import com.tencent.bkrepo.job.separation.pojo.VersionSeparationInfo
import com.tencent.bkrepo.job.separation.service.RepoSpecialDataSeparator

object RepoSpecialSeparationMappings {

    private val mappers = mutableMapOf<RepositoryType, RepoSpecialDataSeparator>()

    init {
        addMapper(SpringContextUtils.getBean(MavenRepoSpecialDataSeparatorHandler::class.java))
    }

    private fun addMapper(mapper: RepoSpecialDataSeparator) {
        mappers[mapper.type()] = mapper
        mapper.extraType()?.let { mappers[mapper.extraType()!!] = mapper }
    }

    fun getNodesOfVersion(
        versionSeparationInfo: VersionSeparationInfo, accessCheck: Boolean = true
    ): MutableMap<String, String> {
        val mapper = mappers[versionSeparationInfo.type]
        check(mapper != null) { "mapper[${versionSeparationInfo.type}] not found" }
        return mapper.getNodesOfVersion(versionSeparationInfo, accessCheck)
    }

    fun separateRepoColdData(versionSeparationInfo: VersionSeparationInfo) {
        val mapper = mappers[versionSeparationInfo.type]
        check(mapper != null) { "mapper[${versionSeparationInfo.type}] not found" }
        mapper.separateRepoSpecialData(versionSeparationInfo)
    }

    fun removeRepoColdData(versionSeparationInfo: VersionSeparationInfo) {
        val mapper = mappers[versionSeparationInfo.type]
        check(mapper != null) { "mapper[${versionSeparationInfo.type}] not found" }
        mapper.removeRepoSpecialData(versionSeparationInfo)
    }

    fun getRestoreNodesOfVersion(versionSeparationInfo: VersionSeparationInfo): MutableMap<String, String> {
        val mapper = mappers[versionSeparationInfo.type]
        check(mapper != null) { "mapper[${versionSeparationInfo.type}] not found" }
        return mapper.getRestoreNodesOfVersion(versionSeparationInfo)
    }

    fun restoreRepoColdData(versionSeparationInfo: VersionSeparationInfo) {
        val mapper = mappers[versionSeparationInfo.type]
        check(mapper != null) { "mapper[${versionSeparationInfo.type}] not found" }
        mapper.restoreRepoSpecialData(versionSeparationInfo)
    }

    fun removeRestoredRepoColdData(versionSeparationInfo: VersionSeparationInfo) {
        val mapper = mappers[versionSeparationInfo.type]
        check(mapper != null) { "mapper[${versionSeparationInfo.type}] not found" }
        mapper.removeRestoredRepoSpecialData(versionSeparationInfo)
    }

    fun getRecoveryPackageVersionData(recoveryInfo: RecoveryNodeInfo): RecoveryVersionInfo {
        val type = RepositoryType.ofValueOrDefault(recoveryInfo.repoType)
        val mapper = mappers[type]
        check(mapper != null) { "mapper[${type}] not found" }
        return mapper.getRecoveryPackageVersionData(recoveryInfo)
    }
}
