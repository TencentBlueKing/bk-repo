/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.  
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.npm.utils

import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_TGZ_TARBALL_PREFIX

object NpmUtils {
    fun getPackageMetadataPath(packageName: String): String {
        return NPM_PKG_METADATA_FULL_PATH.format(packageName)
    }

    fun getVersionPackageMetadataPath(name: String, version: String): String {
        return NPM_PKG_VERSION_METADATA_FULL_PATH.format(name, name, version)
    }

    fun getTgzPath(name: String, version: String): String {
        return NPM_PKG_TGZ_FULL_PATH.format(name, name, version)
    }

    fun analyseVersionFromPackageName(name: String): String {
        return name.substringBeforeLast(".tgz").substringAfter('-')
    }

    fun buildPackageTgzTarball(
        oldTarball: String,
        tarballPrefix: String,
        name: String,
        artifactInfo: ArtifactInfo
    ): String {
        val tgzSuffix = name + oldTarball.substringAfter(name)
        val npmPrefixHeader = HeaderUtils.getHeader(NPM_TGZ_TARBALL_PREFIX)
        val newTarball = StringBuilder()
        npmPrefixHeader?.let {
            newTarball.append(it.trimEnd(SLASH)).append(SLASH).append(artifactInfo.getRepoIdentify())
                .append(SLASH).append(tgzSuffix.trimStart(SLASH))
        } ?: newTarball.append(tarballPrefix.trimEnd(SLASH)).append(SLASH).append(artifactInfo.getRepoIdentify())
            .append(SLASH).append(tgzSuffix.trimStart(SLASH))
        return newTarball.toString()
    }
}