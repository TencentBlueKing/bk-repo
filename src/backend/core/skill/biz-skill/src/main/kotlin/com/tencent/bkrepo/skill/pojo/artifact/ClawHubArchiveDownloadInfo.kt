package com.tencent.bkrepo.skill.pojo.artifact

/**
 * clawhub skill下载信息
 */
class ClawHubArchiveDownloadInfo(
    projectId: String,
    repoName: String,
    slug: String,
    override var version: String?,
    val tag: String? = null,
) : SkillArtifactInfo(projectId, repoName, slug, version) {

    fun isVersionInitialized() = version != null

    override fun getArtifactFullPath(): String {
        val v = version ?: error("Version not initialized")
        return "/$slug/$v/$slug-$v.zip"
    }

    override fun getArtifactVersion() = version
}
