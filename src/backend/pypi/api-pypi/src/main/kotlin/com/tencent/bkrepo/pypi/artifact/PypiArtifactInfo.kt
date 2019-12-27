package com.tencent.bkrepo.pypi.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
class PypiArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    name: String,
    version: String,
    filetype: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val PYPI_SIMPLE_MAPPING_URI = "/{projectId}/{repoName}/simple/**"
        const val PYPI_PACKAGES_MAPPING_URI = "/{projectId}/{repoName}/packages/**"
        const val PYPI_ROOT_MAPPING_URI = "/{projectId}/{repoName}"
        const val PYPI_SIMPLE_MAPPING_INSTALL_URI = "/{projectId}/{repoName}/simple/{package}/"
    }
}
