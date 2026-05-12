package com.tencent.bkrepo.fs.server.request.drive

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import org.springframework.web.reactive.function.server.ServerRequest
import java.time.LocalDateTime

data class DriveNodeBatchRequest(
    override val projectId: String,
    override val repoName: String,
    val clientId: String,
    val operations: List<DriveNodeBatchOperation> = emptyList(),
) : DriveNodeRequest(projectId, repoName) {
    constructor(request: ServerRequest, payload: DriveNodeBatchPayload) : this(
        projectId = request.pathVariable(PROJECT_ID),
        repoName = request.pathVariable(REPO_NAME),
        clientId = payload.clientId.ifBlank {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "clientId")
        },
        operations = payload.operations,
    )
}

data class DriveNodeBatchPayload(
    val clientId: String = "",
    val operations: List<DriveNodeBatchOperation> = emptyList(),
)

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

    @JsonProperty("rename")
    RENAME,
}

data class DriveNodeBatchItem(
    val nodeId: String? = null,
    val ino: Long? = null,
    val targetIno: Long? = null,
    val parent: Long? = null,
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
    val mtime: Long? = null,
    val ctime: Long? = null,
    val atime: Long? = null,
    val ifMatch: LocalDateTime? = null,
    val overwrite: Boolean = false,
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
        mtime = mtime,
        ctime = ctime,
        atime = atime,
    )
}

fun DriveNodeBatchItem.toUpdateRequest(projectId: String, repoName: String): DriveNodeUpdateRequest {
    return DriveNodeUpdateRequest(
        projectId = projectId,
        repoName = repoName,
        ino = requireNotNull(ino) { "ino required" },
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
        mtime = mtime,
        ctime = ctime,
        atime = atime,
        ifMatch = ifMatch,
    )
}

fun DriveNodeBatchItem.toDeleteRequest(projectId: String, repoName: String): DriveNodeDeleteRequest {
    return DriveNodeDeleteRequest(
        projectId = projectId,
        repoName = repoName,
        ino = requireNotNull(ino) { "ino required" },
        ifMatch = ifMatch,
    )
}

fun DriveNodeBatchItem.toMoveRequest(projectId: String, repoName: String): DriveNodeMoveRequest {
    return DriveNodeMoveRequest(
        projectId = projectId,
        repoName = repoName,
        ino = requireNotNull(ino) { "ino required" },
        destParent = requireNotNull(parent) { "parent required" },
        destName = requireNotNull(name) { "name required" },
        mtime = mtime,
        ctime = ctime,
        atime = atime,
        ifMatch = ifMatch,
        overwrite = overwrite,
    )
}
