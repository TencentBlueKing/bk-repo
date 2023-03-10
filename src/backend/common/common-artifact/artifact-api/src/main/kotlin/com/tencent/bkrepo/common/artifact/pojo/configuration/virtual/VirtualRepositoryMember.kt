package com.tencent.bkrepo.common.artifact.pojo.configuration.virtual

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory

data class VirtualRepositoryMember(
    val name: String,
    var category: RepositoryCategory?
)
