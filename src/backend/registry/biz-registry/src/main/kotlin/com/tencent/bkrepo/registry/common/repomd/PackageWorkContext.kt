package com.tencent.bkrepo.registry.common.repomd

import com.tencent.bkrepo.registry.papi.repo.RepoPath

open class PackageWorkContext(protected val repoPath: RepoPath) : ArtifactoryWorkContext(repoPath.getPath())
