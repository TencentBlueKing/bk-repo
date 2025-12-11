package com.tencent.bkrepo.huggingface.artifact

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail

class HuggingfaceArtifactSearchContext(
    repo: RepositoryDetail? = null,
    artifact: HuggingfaceArtifactInfo? = null,
    val recursive: Boolean,
    expand: Boolean,
) : ArtifactSearchContext(repo, artifact)