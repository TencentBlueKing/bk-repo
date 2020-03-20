package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.replication.api.ReplicaResource
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.util.Base64Utils

class ReplicaJobContext(val task: TReplicaTask, val replicaResource: ReplicaResource) {
    val authToken: String
    lateinit var detailList: MutableList<ReplicationProjectDetail>
    lateinit var projectDetail: ReplicationProjectDetail
    lateinit var repoDetail: ReplicationRepoDetail
    lateinit var remoteProject: ProjectInfo
    lateinit var remoteRepo: RepositoryInfo
    lateinit var selfProject: ProjectInfo
    lateinit var selfRepo: RepositoryInfo
    init {
        with(task.setting.remoteClusterInfo) {
            val byteArray = ("$username:$password").toByteArray(Charsets.UTF_8)
            val encodedValue = Base64Utils.encodeToString(byteArray)
            authToken = "$BASIC_AUTH_HEADER_PREFIX $encodedValue"
        }
    }
}
