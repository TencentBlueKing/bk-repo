package com.tencent.bkrepo.helm.pojo.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.utils.HelmUtils

class HelmDeleteArtifactInfo(
    projectId: String,
    repoName: String,
    val packageName: String,
    val version: String = StringPool.EMPTY
) : HelmArtifactInfo(projectId, repoName, StringPool.EMPTY) {

    private val name = PackageKeys.resolveHelm(packageName)

    private val chartFullPath = HelmUtils.getChartFileFullPath(name, version)

    override fun getArtifactFullPath(): String = chartFullPath

    override fun getArtifactName(): String = name

    override fun getArtifactVersion(): String = version
}
