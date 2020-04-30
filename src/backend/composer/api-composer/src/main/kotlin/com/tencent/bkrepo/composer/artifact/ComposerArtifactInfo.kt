package com.tencent.bkrepo.composer.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 * 预先提取压缩包中composer.json中的信息
 * packageName 对应"name" ,实际包含了可能包含组织名
 * version的值取composer.json文件"version"值
 * composerJson 即composer.json 内容
 *
 */
class ComposerArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val COMPOSER_DEPLOY = "/{projectId}/{repoName}/*"
        const val COMPOSER_JSON = "/{projectId}/{repoName}/**/*.json"
        const val COMPOSER_INSTALL = "/{projectId}/{repoName}/direct-dists/**"
        const val COMPOSER_PACKAGES = "/{projectId}/{repoName}/packages.json"
    }
}
