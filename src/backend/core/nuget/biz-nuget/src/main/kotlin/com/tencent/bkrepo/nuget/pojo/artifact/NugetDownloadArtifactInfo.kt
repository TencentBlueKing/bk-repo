package com.tencent.bkrepo.nuget.pojo.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.constant.MANIFEST
import com.tencent.bkrepo.nuget.util.NugetUtils

class NugetDownloadArtifactInfo(
    projectId: String,
    repoName: String,
    val packageName: String,
    val version: String = StringPool.EMPTY,
    val type: String
) : NugetArtifactInfo(projectId, repoName, StringPool.EMPTY) {

    private val nupkgFullPath = NugetUtils.getNupkgFullPath(packageName, version)
    private val nuspecFullPath = NugetUtils.getNuspecFullPath(packageName, version)

    override fun getArtifactFullPath(): String {
        return if(getArtifactMappingUri().isNullOrEmpty()) {
            if (type == MANIFEST) nuspecFullPath else nupkgFullPath
        } else getArtifactMappingUri()!!
    }

    override fun getArtifactName(): String = packageName

    override fun getArtifactVersion(): String = version
}
