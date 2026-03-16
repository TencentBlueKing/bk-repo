package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotCreateRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotPageRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveSnapshotUpdateRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveSnapshot
import com.tencent.bkrepo.fs.server.service.drive.DriveSnapshotService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * Drive 快照操作处理器
 */
@Component
class DriveSnapshotHandler(
    private val driveSnapshotService: DriveSnapshotService,
) {
    suspend fun createSnapshot(request: ServerRequest): ServerResponse {
        val body = request.bodyToMono(DriveSnapshotCreateRequest::class.java).awaitSingle()
        with(DriveSnapshotRequest(request)) {
            val snapshot = driveSnapshotService.createSnapshot(projectId, repoName, body.name, body.description)
            return ReactiveResponseBuilder.success(snapshot)
        }
    }

    suspend fun listSnapshotsPage(request: ServerRequest): ServerResponse {
        with(DriveSnapshotPageRequest(request)) {
            val page = driveSnapshotService.listSnapshotsPage(
                projectId = projectId,
                repoName = repoName,
                pageNumber = pageNum,
                pageSize = pageSize,
            )
            return ReactiveResponseBuilder.success(page)
        }
    }

    suspend fun updateSnapshot(request: ServerRequest): ServerResponse {
        val snapshotId = request.pathVariable(DriveSnapshot::id.name)
        val body = request.bodyToMono(DriveSnapshotUpdateRequest::class.java).awaitSingle()
        with(DriveSnapshotRequest(request)) {
            val snapshot = driveSnapshotService.updateSnapshot(
                projectId = projectId,
                repoName = repoName,
                snapshotId = snapshotId,
                name = body.name,
                description = body.description,
            )
            return ReactiveResponseBuilder.success(snapshot)
        }
    }

    suspend fun deleteSnapshot(request: ServerRequest): ServerResponse {
        val snapshotId = request.pathVariable(DriveSnapshot::id.name)
        with(DriveSnapshotRequest(request)) {
            driveSnapshotService.deleteSnapshot(projectId, repoName, snapshotId)
            return ReactiveResponseBuilder.success()
        }
    }
}
