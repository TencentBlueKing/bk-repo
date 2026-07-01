/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.fs.server.handler.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.fs.server.request.service.DriveBlockListRequest
import com.tencent.bkrepo.fs.server.service.PermissionService
import com.tencent.bkrepo.fs.server.service.drive.DriveFileBlockService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait

@Component
class DriveServiceHandler(
    private val driveFileBlockService: DriveFileBlockService,
    private val permissionService: PermissionService,
) {

    suspend fun listBlocks(request: ServerRequest): ServerResponse {
        with(DriveBlockListRequest(request)) {
            checkDownloadPermission(projectId, repoName)
            val info = driveFileBlockService.listFileBlocks(projectId, repoName, path)
                ?: return ServerResponse.notFound().buildAndAwait()
            return ReactiveResponseBuilder.success(info)
        }
    }

    private suspend fun checkDownloadPermission(projectId: String, repoName: String) {
        val userId = ReactiveSecurityUtils.getUser()
        if (!permissionService.checkPermission(projectId, repoName, PermissionAction.DOWNLOAD, userId)) {
            throw PermissionException()
        }
    }
}
