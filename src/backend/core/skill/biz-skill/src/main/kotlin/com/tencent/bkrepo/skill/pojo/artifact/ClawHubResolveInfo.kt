package com.tencent.bkrepo.skill.pojo.artifact

class ClawHubResolveInfo(
    projectId: String,
    repoName: String,
    slug: String,
    val hash: String,
) : SkillArtifactInfo(projectId, repoName, slug, null) {

    override fun getArtifactFullPath() = throw UnsupportedOperationException()
    override fun getArtifactVersion() = throw UnsupportedOperationException()
}
