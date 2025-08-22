/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

import com.github.zafarkhaja.semver.Version
import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.npm.constants.FILE_SUFFIX
import com.tencent.bkrepo.npm.constants.HAR_FILE_EXT
import com.tencent.bkrepo.npm.constants.HSP_FILE_EXT
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_WITH_DOWNLOAD_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_TGZ_TARBALL_PREFIX
import com.tencent.bkrepo.npm.model.metadata.NpmPackageMetaData
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.slf4j.LoggerFactory
import java.io.InputStream

object NpmUtils {

    private val logger = LoggerFactory.getLogger(NpmUtils::class.java)

    fun getPackageMetadataPath(packageName: String): String {
        return NPM_PKG_METADATA_FULL_PATH.format(packageName)
    }

    fun getVersionPackageMetadataPath(name: String, version: String): String {
        return NPM_PKG_VERSION_METADATA_FULL_PATH.format(name, name, version)
    }

    fun getTgzPath(name: String, version: String, pathWithDash: Boolean = true, ext: String = FILE_SUFFIX): String {
        return if (pathWithDash) {
            NPM_PKG_TGZ_FULL_PATH.format(name, name, version, ext)
        } else {
            NPM_PKG_TGZ_WITH_DOWNLOAD_FULL_PATH.format(name, name, version, ext)
        }
    }

    fun analyseVersionFromPackageName(filename: String, name: String): String {
        val unscopedName = name.substringAfterLast("/")
        val ext = if (filename.endsWith(".har")) {
            ".har"
        } else {
            ".tgz"
        }
        return filename.substringBeforeLast(ext).substringAfter("$unscopedName-")
    }

    /**
     * 查看[tarball]里面是否使用 - 分隔符来进行分隔
     */
    fun isDashSeparateInTarball(name: String, version: String, tarball: String): Boolean {
        val tgzPath = "/%s-%s".format(name, version)
        val separate = tarball.substringBeforeLast(tgzPath).substringAfterLast('/')
        return separate == StringPool.DASH
    }

    /**
     * 格式化[tarball]使用 - 来进行分隔
     * http://xxx/helloworld/download/hellworld-1.0.0.tgz  -> http://xxx/helloworld/-/hellworld-1.0.0.tgz
     */
    fun formatTarballWithDash(name: String, version: String, tarball: String): String {
        val tgzPath = "/%s-%s".format(name, version)
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

    fun updateLatestVersion(npmPackageMetaData: NpmPackageMetaData) {
        try {
            npmPackageMetaData.versions.map.keys
                .maxByOrNull { Version.valueOf(it) }
                ?.let { npmPackageMetaData.distTags.set(LATEST, it) }
        } catch (e: Exception) {
            logger.error("update latest failed, current version will be used as the latest", e)
        }
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
        returnRepoId: Boolean,
        name: String,
        artifactInfo: ArtifactInfo
    ): String {
        val tgzSuffix = getTgzSuffix(oldTarball, name).trimStart(SLASH)
        val npmPrefixHeader = HeaderUtils.getHeader(NPM_TGZ_TARBALL_PREFIX)?.trimEnd(SLASH)
        val newTarball = StringBuilder()
        if (npmPrefixHeader != null) {
            newTarball.append(npmPrefixHeader)
            if (returnRepoId) {
                newTarball.append(artifactInfo.getRepoIdentify())
            }
        } else if (tarballPrefix.isEmpty()) {
            // 远程仓库返回的是代理地址
            newTarball.append(UrlFormatter.formatUrl(domain).trimEnd(SLASH))
                .append(artifactInfo.getRepoIdentify())
        } else {
            val formatUrl = UrlFormatter.formatUrl(tarballPrefix)
            newTarball.append(formatUrl.trimEnd(SLASH))
            if (returnRepoId) {
                newTarball.append(artifactInfo.getRepoIdentify())
            }
        }
        newTarball.append(SLASH).append(tgzSuffix)
        return newTarball.toString()
    }

    fun packageKeyByRepoType(
        name: String,
        repoType: RepositoryType = ArtifactContextHolder.getRepoDetail()!!.type
    ): String {
        return if (repoType == RepositoryType.OHPM) {
            PackageKeys.ofOhpm(name)
        } else {
            PackageKeys.ofNpm(name)
        }
    }

    fun packageKey(name: String, ohpm: Boolean = false) = if (ohpm) {
        PackageKeys.ofOhpm(name)
    } else {
        PackageKeys.ofNpm(name)
    }

    fun resolveNameByRepoType(
        packageKey: String,
        repoType: RepositoryType = ArtifactContextHolder.getRepoDetail()!!.type
    ): String {
        return if (repoType == RepositoryType.OHPM) {
            PackageKeys.resolveOhpm(packageKey)
        } else {
            PackageKeys.resolveNpm(packageKey)
        }
    }

    fun getTarballPathByRepoType(
        name: String,
        version: String,
        pathWithDash: Boolean = true,
        repoType: RepositoryType = ArtifactContextHolder.getRepoDetail()!!.type
    ): String {
        val ext = getContentFileExt(repoType == RepositoryType.OHPM)
        return getTgzPath(name, version, pathWithDash, ext)
    }

    /**
     * 转换har tarball url为 hsp url
     *
     * demo-1.0.0.har -> demo-1.0.0.hsp
     */
    fun harPathToHspPath(harTarball: String): String {
        return harTarball.substring(0, harTarball.length - 4) + HSP_FILE_EXT
    }

    fun getContentPath(name: String, version: String, ohpm: Boolean): String {
        return String.format(NPM_PKG_TGZ_FULL_PATH, name, name, version, getContentFileExt(ohpm))
    }

    fun getContentFileExt(ohpm: Boolean) = if (ohpm) {
        HAR_FILE_EXT
    } else {
        FILE_SUFFIX
    }

    /**
     * 从har包中解析出readme与changelog文件数据
     *
     * @param inputStream har文件，由调用方负责关闭
     * @return (readme，changelog)，不存在时为null
     */
    fun getReadmeAndChangeLog(inputStream: InputStream): Pair<ByteArray?, ByteArray?> {
        var changelog: ByteArray? = null
        var readme: ByteArray? = null
        val tar = TarArchiveInputStream(GzipCompressorInputStream(inputStream))
        do {
            val entry = tar.nextEntry
            // package最外层目录未找到readme时放弃搜索
            if (entry == null || entry.name.endsWith("/")) {
                break
            }
            if (entry.name == "package/README.md" || entry.name == "package/readme.md") {
                readme = tar.readBytes()
            }
            if (entry.name == "package/CHANGELOG.md" || entry.name == "package/changelog.md") {
                changelog = tar.readBytes()
            }
            if (changelog != null && readme != null) {
                break
            }
        } while (true)
        return Pair(readme, changelog)
    }

    /**
     * 移除文件后缀作为readme文件存放目录，例：demo-1.0.0.har -> demo-1.0.0
     */
    fun getReadmeDirFromTarballPath(tarballPath: String): String {
        return tarballPath.substring(0, tarballPath.length - 4)
    }
}
