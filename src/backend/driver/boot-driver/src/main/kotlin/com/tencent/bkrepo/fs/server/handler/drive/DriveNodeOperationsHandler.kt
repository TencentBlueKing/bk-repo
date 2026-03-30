package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.fs.server.readBodyOrNull
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchPayload
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeModifiedPageRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodePageRequest
import com.tencent.bkrepo.fs.server.service.drive.DriveNodeService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
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
) {
    suspend fun batch(request: ServerRequest): ServerResponse {
        val payload = request.readBodyOrNull(DriveNodeBatchPayload::class.java) ?: DriveNodeBatchPayload()
        val batchResult = driveNodeService.batch(DriveNodeBatchRequest(request, payload))
        return ReactiveResponseBuilder.success(batchResult)
    }

    suspend fun listNodesPage(request: ServerRequest): ServerResponse {
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
            return ReactiveResponseBuilder.success(page)
        }
    }

    suspend fun listModifiedNodesPage(request: ServerRequest): ServerResponse {
        with(DriveNodeModifiedPageRequest(request)) {
            val page = driveNodeService.listModifiedNodesPage(
                projectId = projectId,
                repoName = repoName,
                pageSize = pageSize,
                lastModifiedDate = lastModifiedDate,
                lastId = lastId,
                clientId = clientId,
            )
            return ReactiveResponseBuilder.success(page)
        }
    }
}
