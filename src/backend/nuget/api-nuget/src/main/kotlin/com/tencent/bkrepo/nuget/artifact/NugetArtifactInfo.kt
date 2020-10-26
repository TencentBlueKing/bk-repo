package com.tencent.bkrepo.nuget.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class NugetArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val NUGET_RESOURCE = "/{projectId}/{repoName}/**"
    }

    override fun getArtifactFullPath(): String {
        return super.getArtifactFullPath()
    }
}
