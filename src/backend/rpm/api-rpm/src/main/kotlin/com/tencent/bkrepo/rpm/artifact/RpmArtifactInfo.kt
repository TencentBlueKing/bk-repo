package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class RpmArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val RPM_DEPLOY = "/{projectId}/{repoName}/**"
    }
}
