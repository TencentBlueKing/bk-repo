package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.job.ReplicationArtifactContext
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest

interface ReplicationArtifactService {
    fun replicaFile(context: ReplicationArtifactContext, request: NodeCreateRequest)

    fun replicaPackageVersionCreatedRequest(context: ReplicationArtifactContext, request: PackageVersionCreateRequest)

    fun replicaNodeCreateRequest(context: ReplicationArtifactContext, request: NodeCreateRequest)

    fun checkNodeExistRequest(
        context: ReplicationArtifactContext,
        projectId: String,
        repoName: String,
        fullPath: String
    ): Boolean

    fun replicaNodeRenameRequest(context: ReplicationArtifactContext, request: NodeRenameRequest)

    fun replicaNodeUpdateRequest(context: ReplicationArtifactContext, request: NodeUpdateRequest)

    fun replicaNodeCopyRequest(context: ReplicationArtifactContext, request: NodeMoveCopyRequest)

    fun replicaNodeMoveRequest(context: ReplicationArtifactContext, request: NodeMoveCopyRequest)

    fun replicaNodeDeleteRequest(context: ReplicationArtifactContext, request: NodeDeleteRequest)

    fun replicaRepoCreateRequest(context: ReplicationArtifactContext, request: RepoCreateRequest)

    fun replicaRepoUpdateRequest(context: ReplicationArtifactContext, request: RepoUpdateRequest)

    fun replicaRepoDeleteRequest(context: ReplicationArtifactContext, request: RepoDeleteRequest)

    fun replicaProjectCreateRequest(context: ReplicationArtifactContext, request: ProjectCreateRequest)

    fun replicaMetadataSaveRequest(context: ReplicationArtifactContext, request: MetadataSaveRequest)

    fun replicaMetadataDeleteRequest(context: ReplicationArtifactContext, request: MetadataDeleteRequest)
}
