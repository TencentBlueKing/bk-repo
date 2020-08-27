package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

/**
 * 构件下载context
 */
class ArtifactDownloadContext(repo: RepositoryInfo? = null) : ArtifactTransferContext(repo)
