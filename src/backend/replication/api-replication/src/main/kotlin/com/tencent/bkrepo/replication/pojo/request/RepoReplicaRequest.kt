package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class RepoReplicaRequest(
    val projectId: String,
    val name: String,
    val type: RepositoryType,
    val category: RepositoryCategory,
    val public: Boolean,
    val description: String? = null,
    val configuration: RepositoryConfiguration,

    override val actionType: ActionType = ActionType.CREATE_OR_UPDATE,
    override val userId: String = SYSTEM_USER
) : ReplicaRequest(actionType, userId)
