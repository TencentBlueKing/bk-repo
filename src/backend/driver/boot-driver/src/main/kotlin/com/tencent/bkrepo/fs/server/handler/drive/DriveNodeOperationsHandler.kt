package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.fs.server.readBodyOrNull
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchPayload
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeModifiedPageRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodePageRequest
import com.tencent.bkrepo.fs.server.service.drive.DriveNodeService
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * Drive 节点操作处理器
 *
 * 仅处理节点分页与增量变更查询
 */
@Component
class DriveNodeOperationsHandler(
    private val driveNodeService: DriveNodeService,
    private val driveOperateLogService: DriveOperateLogService,
) {
    suspend fun batch(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        val payload = request.readBodyOrNull(DriveNodeBatchPayload::class.java) ?: DriveNodeBatchPayload()
        val batchRequest = DriveNodeBatchRequest(request, payload)
        val batchResult = driveNodeService.batch(batchRequest)
        driveOperateLogService.recordBatchResults(batchRequest, batchResult, userId, clientAddress)
        return ReactiveResponseBuilder.success(batchResult)
    }

    suspend fun listNodesPage(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        with(DriveNodePageRequest(request)) {
            val page = driveNodeService.listNodesPage(
                projectId = projectId,
                repoName = repoName,
                parent = parent,
                pageSize = pageSize,
                lastName = lastName,
                lastId = lastId,
                snapSeq = snapSeq,
            )
            driveOperateLogService.record(
                type = EventType.DRIVE_NODE_LIST.name,
                userId = userId,
                clientAddress = clientAddress,
                projectId = projectId,
                repoName = repoName,
                resourceKey = parent?.toString().orEmpty(),
                description = mapOf(
                    "parent" to parent,
                    "pageSize" to pageSize,
                    "lastName" to lastName,
                    "lastId" to lastId,
                    "snapSeq" to snapSeq,
                ).filterValues { it != null } as Map<String, Any>,
            )
            return ReactiveResponseBuilder.success(page)
        }
    }

    suspend fun listModifiedNodesPage(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        with(DriveNodeModifiedPageRequest(request)) {
            val page = driveNodeService.listModifiedNodesPage(
                projectId = projectId,
                repoName = repoName,
                pageSize = pageSize,
                lastModifiedDate = lastModifiedDate,
                lastId = lastId,
                clientId = clientId,
            )
            driveOperateLogService.record(
                type = EventType.DRIVE_NODE_MODIFIED_LIST.name,
                userId = userId,
                clientAddress = clientAddress,
                projectId = projectId,
                repoName = repoName,
                resourceKey = clientId,
                description = mapOf(
                    "clientId" to clientId,
                    "pageSize" to pageSize,
                    "lastModifiedDate" to lastModifiedDate.toString(),
                    "lastId" to lastId,
                ),
            )
            return ReactiveResponseBuilder.success(page)
        }
    }
}
