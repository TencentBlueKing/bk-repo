package com.tencent.bkrepo.helm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class HelmArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri){
    companion object{
        const val CHARTS_LIST = "/{projectId}/{repoName}/api/charts/**"
    }
}
