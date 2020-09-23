package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class RpmArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val RPM = "/{projectId}/{repoName}/**"
        const val RPM_CONFIGURATION = "/configuration/{projectId}/{repoName}/**"
        const val RPM_DEBUG_FLUSH = "/flush/{projectId}/{repoName}/**"
        const val RPM_DEBUG_ALL_FLUSH = "/flushAll/{projectId}/{repoName}/"
    }
}
