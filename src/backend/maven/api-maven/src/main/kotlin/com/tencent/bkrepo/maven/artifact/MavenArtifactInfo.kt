package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
class MavenArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    val fullPath: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val MAVEN_MAPPING_URI = "/{projectId}/{repoName}/**"
    }
}
