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

package com.tencent.bkrepo.npm.utils

import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_WITH_DOWNLOAD_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_TGZ_TARBALL_PREFIX
import com.tencent.bkrepo.npm.model.metadata.NpmPackageMetaData

object NpmUtils {

    fun getPackageMetadataPath(packageName: String): String {
        return NPM_PKG_METADATA_FULL_PATH.format(packageName)
    }

    fun getVersionPackageMetadataPath(name: String, version: String): String {
        return NPM_PKG_VERSION_METADATA_FULL_PATH.format(name, name, version)
    }

    fun getTgzPath(name: String, version: String, pathWithDash: Boolean = true): String {
        return if (pathWithDash) {
            NPM_PKG_TGZ_FULL_PATH.format(name, name, version)
        } else {
            NPM_PKG_TGZ_WITH_DOWNLOAD_FULL_PATH.format(name, name, version)
        }
    }

    fun analyseVersionFromPackageName(filename: String, name: String): String {
        return filename.substringBeforeLast(".tgz").substringAfter("$name-")
    }

    /**
     * 查看[tarball]里面是否使用 - 分隔符来进行分隔
     */
    fun isDashSeparateInTarball(name: String, version: String, tarball: String): Boolean {
        val tgzPath = "/%s-%s.tgz".format(name, version)
        val separate = tarball.substringBeforeLast(tgzPath).substringAfterLast('/')
        return separate == StringPool.DASH
    }

    /**
     * 格式化[tarball]使用 - 来进行分隔
     * http://xxx/helloworld/download/hellworld-1.0.0.tgz  -> http://xxx/helloworld/-/hellworld-1.0.0.tgz
     */
    fun formatTarballWithDash(name: String, version: String, tarball: String): String {
        val tgzPath = "/%s-%s.tgz".format(name, version)
        val separate = tarball.substringBeforeLast(tgzPath).substringAfterLast('/')
        return tarball.replace("$name/$separate/$name", "$name/-/$name")
    }

    fun getLatestVersionFormDistTags(distTags: NpmPackageMetaData.DistTags): String {
        val iterator = distTags.getMap().iterator()
        if (iterator.hasNext()) {
            return iterator.next().value
        }
        return distTags.getMap()[LATEST]!!
    }

    fun parseNameAndVersionFromFullPath(artifactFullPath: String): Pair<String, String> {
        val splitList = artifactFullPath.split('/').filter { it.isNotBlank() }.map { it.trim() }.toList()
        val name = if (splitList.size == 3) {
            splitList.first()
        } else {
            "${splitList.first()}/${splitList[1]}"
        }
        val version = analyseVersionFromPackageName(artifactFullPath, name)
        return Pair(name, version)
    }

    private fun isScopeName(name: String): Boolean {
        return name.startsWith('@') && name.indexOf('/') != -1
    }

    /**
     * name with scope tarball like this: http://domain/@scope/demo/-/demo-1.0.0.tgz
     * name without scope tarball like this: http://domain/demo/-/demo-1.0.0.tgz
     */
    private fun getTgzSuffix(oldTarball: String, name: String): String {
        return if (isScopeName(name)) {
            name + oldTarball.substringAfter(name)
        } else {
            val list = oldTarball.split(name).map { it.trim() }
            name + list[list.lastIndex - 1] + name + list.last()
        }
    }

    /**
     * 如果[tarballPrefix]不为空则采用tarballPrefix,否则采用自定义上传上来的tarball
     */
    fun buildPackageTgzTarball(
        oldTarball: String,
        domain: String,
        tarballPrefix: String,
        name: String,
        artifactInfo: ArtifactInfo
    ): String {
        val tgzSuffix = getTgzSuffix(oldTarball, name)
        val npmPrefixHeader = HeaderUtils.getHeader(NPM_TGZ_TARBALL_PREFIX)
        val newTarball = StringBuilder()
        npmPrefixHeader?.let {
            newTarball.append(it.trimEnd(SLASH))
                // .append(SLASH).append(artifactInfo.getRepoIdentify())
                .append(SLASH).append(tgzSuffix.trimStart(SLASH))
        } ?: if (tarballPrefix.isEmpty()) {
            // 远程仓库返回的是代理地址
            newTarball.append(UrlFormatter.formatUrl(domain).trimEnd(SLASH))
                .append(artifactInfo.getRepoIdentify())
                .append(SLASH)
                .append(tgzSuffix)
        } else {
            val formatUrl = UrlFormatter.formatUrl(tarballPrefix)
            newTarball.append(formatUrl.trimEnd(SLASH))
                .append(artifactInfo.getRepoIdentify())
                .append(SLASH).append(tgzSuffix.trimStart(SLASH))
        }
        return newTarball.toString()
    }
}
