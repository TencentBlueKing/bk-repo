/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.repository.controller.cluster

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.cluster.ClusterMetadataClient
import com.tencent.bkrepo.repository.pojo.metadata.DeletedNodeMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import org.springframework.web.bind.annotation.RestController

@RestController
class ClusterMetadataController(
    private val permissionManager: PermissionManager,
    private val metadataService: MetadataService
) : ClusterMetadataClient {
    override fun saveMetadata(request: MetadataSaveRequest): Response<Void> {
        with(request) {
            permissionManager.checkNodePermission(PermissionAction.WRITE, projectId, repoName, fullPath)
            metadataService.saveMetadata(this)
            return ResponseBuilder.success()
        }
    }

    override fun deleteMetadata(request: MetadataDeleteRequest): Response<Void> {
        with(request) {
            permissionManager.checkNodePermission(PermissionAction.DELETE, projectId, repoName, fullPath)
            metadataService.deleteMetadata(this)
            return ResponseBuilder.success()
        }
    }

    override fun addForbidMetadata(request: MetadataSaveRequest): Response<Void> {
        with(request) {
            permissionManager.checkNodePermission(PermissionAction.WRITE, projectId, repoName, fullPath)
            metadataService.addForbidMetadata(this)
            return ResponseBuilder.success()
        }
    }

    override fun saveMetadataForDeletedNode(request: DeletedNodeMetadataSaveRequest): Response<Void> {
        with(request.metadataSaveRequest) {
            permissionManager.checkNodePermission(PermissionAction.WRITE, projectId, repoName, fullPath)
            metadataService.saveMetadataForDeletedNode(request)
            return ResponseBuilder.success()
        }
    }
}
