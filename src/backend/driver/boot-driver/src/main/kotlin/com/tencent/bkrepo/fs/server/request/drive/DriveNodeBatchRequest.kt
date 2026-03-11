package com.tencent.bkrepo.fs.server.request.drive

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import org.springframework.web.reactive.function.server.ServerRequest
import java.time.LocalDateTime

data class DriveNodeBatchRequest(
    override val projectId: String,
    override val repoName: String,
    val operations: List<DriveNodeBatchOperation> = emptyList(),
) : DriveNodeRequest(projectId, repoName) {
    constructor(request: ServerRequest, operations: List<DriveNodeBatchOperation>) : this(
        projectId = request.pathVariable(PROJECT_ID),
        repoName = request.pathVariable(REPO_NAME),
        operations = operations,
    )
}

data class DriveNodeBatchOperation(
    val op: DriveNodeBatchOp,
    val node: DriveNodeBatchItem,
)

enum class DriveNodeBatchOp {
    @JsonProperty("create")
    CREATE,

    @JsonProperty("delete")
    DELETE,

    @JsonProperty("update")
    UPDATE,

    @JsonProperty("create_hard_link")
    CREATE_HARD_LINK,
}

data class DriveNodeBatchItem(
    val nodeId: String? = null,
    val ino: String? = null,
    val targetIno: String? = null,
    val parent: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val mode: Int? = null,
    val type: Int? = null,
    val nlink: Int? = null,
    val uid: Int? = null,
    val gid: Int? = null,
    val rdev: Int? = null,
    val flags: Int? = null,
    val symlinkTarget: String? = null,
    val lastModifiedDate: LocalDateTime? = null,
    val force: Boolean = false,
)

fun DriveNodeBatchItem.toCreateRequest(projectId: String, repoName: String): DriveNodeCreateRequest {
    return DriveNodeCreateRequest(
        projectId = projectId,
        repoName = repoName,
        parent = requireNotNull(parent) { "parent required" },
        name = requireNotNull(name) { "name required" },
        ino = requireNotNull(ino) { "ino required" },
        targetIno = targetIno,
        size = requireNotNull(size) { "size required" },
        mode = requireNotNull(mode) { "mode required" },
        type = requireNotNull(type) { "type required" },
        nlink = requireNotNull(nlink) { "nlink required" },
        uid = requireNotNull(uid) { "uid required" },
        gid = requireNotNull(gid) { "gid required" },
        rdev = requireNotNull(rdev) { "rdev required" },
        flags = requireNotNull(flags) { "flags required" },
        symlinkTarget = symlinkTarget,
    )
}

fun DriveNodeBatchItem.toUpdateRequest(projectId: String, repoName: String): DriveNodeUpdateRequest {
    return DriveNodeUpdateRequest(
        projectId = projectId,
        repoName = repoName,
        nodeId = requireNotNull(nodeId) { "nodeId required" },
        parent = parent,
        name = name,
        size = size,
        mode = mode,
        nlink = nlink,
        uid = uid,
        gid = gid,
        rdev = rdev,
        flags = flags,
        symlinkTarget = symlinkTarget,
        lastModifiedDate = lastModifiedDate,
        force = force,
    )
}

fun DriveNodeBatchItem.toDeleteRequest(projectId: String, repoName: String): DriveNodeDeleteRequest {
    return DriveNodeDeleteRequest(
        projectId = projectId,
        repoName = repoName,
        nodeId = requireNotNull(nodeId) { "nodeId required" },
        lastModifiedDate = lastModifiedDate,
        force = force,
    )
}
