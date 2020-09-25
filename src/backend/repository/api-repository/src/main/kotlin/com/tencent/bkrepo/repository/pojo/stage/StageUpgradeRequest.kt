package com.tencent.bkrepo.repository.pojo.stage

import com.tencent.bkrepo.repository.pojo.ServiceRequest

data class StageUpgradeRequest(
    val projectId: String,
    val repoName: String,
    val packageKey: String,
    val version: String,
    val newTag: String? = null,
    override val operator: String
) : ServiceRequest