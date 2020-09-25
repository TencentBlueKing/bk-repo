package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail

/**
 * 构件下载context
 */
open class ArtifactDownloadContext(
    repo: RepositoryDetail? = null,
    artifact: ArtifactInfo? = null,
    val useDisposition: Boolean = true
) : ArtifactContext(repo, artifact)
