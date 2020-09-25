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