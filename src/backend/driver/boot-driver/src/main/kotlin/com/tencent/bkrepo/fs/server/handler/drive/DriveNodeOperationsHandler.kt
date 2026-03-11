package com.tencent.bkrepo.fs.server.handler.drive

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
    suspend fun listNodesPage(request: ServerRequest): ServerResponse {
        with(DriveNodePageRequest(request)) {
            val page =
                driveNodeService.listNodesPage(projectId, repoName, parent, pageNum, pageSize, includeTotalRecords)
            return ReactiveResponseBuilder.success(page)
        }
    }

    suspend fun listModifiedNodesPage(request: ServerRequest): ServerResponse {
        with(DriveNodeModifiedPageRequest(request)) {
            val page = driveNodeService.listModifiedNodesPage(
                projectId = projectId,
                repoName = repoName,
                lastModifiedDate = lastModifiedDate,
                pageNumber = pageNum,
                pageSize = pageSize,
                includeTotalRecords = includeTotalRecords
            )
            return ReactiveResponseBuilder.success(page)
        }
    }
}
