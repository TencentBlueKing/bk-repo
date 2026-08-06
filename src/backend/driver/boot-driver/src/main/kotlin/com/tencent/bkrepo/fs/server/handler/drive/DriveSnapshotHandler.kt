package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.fs.server.readBody
import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotCreateRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotPageRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotUpdateRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveSnapshot
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogService
import com.tencent.bkrepo.fs.server.service.drive.DriveSnapshotService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * Drive 快照操作处理器
 */
@Component
class DriveSnapshotHandler(
    private val driveSnapshotService: DriveSnapshotService,
    private val driveOperateLogService: DriveOperateLogService,
) {
    suspend fun createSnapshot(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        val body = request.readBody(DriveSnapshotCreateRequest::class.java)
        with(DriveSnapshotRequest(request)) {
            val snapshot = driveSnapshotService.createSnapshot(projectId, repoName, body.name, body.description)
            driveOperateLogService.record(
                type = EventType.DRIVE_SNAPSHOT_CREATE.name,
                userId = userId,
                clientAddress = clientAddress,
                projectId = projectId,
                repoName = repoName,
                resourceKey = snapshot.id,
                description = snapshotDescription(snapshot),
            )
            return ReactiveResponseBuilder.success(snapshot)
        }
    }

    suspend fun listSnapshotsPage(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        with(DriveSnapshotPageRequest(request)) {
            val page = driveSnapshotService.listSnapshotsPage(
                projectId = projectId,
                repoName = repoName,
                pageNumber = pageNum,
                pageSize = pageSize,
            )
            driveOperateLogService.record(
                type = EventType.DRIVE_SNAPSHOT_LIST.name,
                userId = userId,
                clientAddress = clientAddress,
                projectId = projectId,
                repoName = repoName,
                resourceKey = repoName,
                description = mapOf(
                    "pageNum" to pageNum,
                    "pageSize" to pageSize,
                ),
            )
            return ReactiveResponseBuilder.success(page)
        }
    }

    suspend fun updateSnapshot(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        val snapshotId = request.pathVariable(DriveSnapshot::id.name)
        val body = request.readBody(DriveSnapshotUpdateRequest::class.java)
        with(DriveSnapshotRequest(request)) {
            val snapshot = driveSnapshotService.updateSnapshot(
                projectId = projectId,
                repoName = repoName,
                snapshotId = snapshotId,
                name = body.name,
                description = body.description,
            )
            driveOperateLogService.record(
                type = EventType.DRIVE_SNAPSHOT_UPDATE.name,
                userId = userId,
                clientAddress = clientAddress,
                projectId = projectId,
                repoName = repoName,
                resourceKey = snapshot.id,
                description = snapshotDescription(snapshot),
            )
            return ReactiveResponseBuilder.success(snapshot)
        }
    }

    suspend fun deleteSnapshot(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        val snapshotId = request.pathVariable(DriveSnapshot::id.name)
        with(DriveSnapshotRequest(request)) {
            driveSnapshotService.deleteSnapshot(projectId, repoName, snapshotId)
            driveOperateLogService.record(
                type = EventType.DRIVE_SNAPSHOT_DELETE.name,
                userId = userId,
                clientAddress = clientAddress,
                projectId = projectId,
                repoName = repoName,
                resourceKey = snapshotId,
                description = mapOf("snapshotId" to snapshotId),
            )
            return ReactiveResponseBuilder.success()
        }
    }

    private fun snapshotDescription(snapshot: DriveSnapshot): Map<String, Any> {
        return mapOf(
            "snapshotId" to snapshot.id,
            "name" to snapshot.name,
            "snapSeq" to snapshot.snapSeq
        ).filterValues { it != null } as Map<String, Any>
    }
}
