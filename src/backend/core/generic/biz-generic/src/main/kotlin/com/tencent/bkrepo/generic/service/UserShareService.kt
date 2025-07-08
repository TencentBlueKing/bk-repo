/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.generic.service

import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent.PROJECT_CODE_TEMPLATE
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent.REPO_NAME_TEMPLATE
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.generic.dao.UserShareRecordDao
import com.tencent.bkrepo.generic.exception.UserShareExpiredException
import com.tencent.bkrepo.generic.exception.UserShareNoPermitsException
import com.tencent.bkrepo.generic.model.TUserShareApproval
import com.tencent.bkrepo.generic.model.TUserShareRecord
import com.tencent.bkrepo.generic.pojo.share.UserShareConfigInfo
import com.tencent.bkrepo.generic.pojo.share.UserShareGenUrlRequest
import com.tencent.bkrepo.generic.pojo.share.UserShareRecordCreateRequest
import com.tencent.bkrepo.generic.pojo.share.UserShareRecordInfo
import com.tencent.bkrepo.generic.pojo.share.UserShareUrls
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class UserShareService(
    private val userShareRecordDao: UserShareRecordDao,
    private val userShareApprovalService: UserShareApprovalService,
    private val nodeService: NodeService,
    private val devXProperties: DevXProperties,
    private val permissionManager: PermissionManager,
) {

    fun create(request: UserShareRecordCreateRequest): UserShareRecordInfo {
        with(request) {
            if (path == StringPool.ROOT) throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "path[$path]")
            val nodes = nodeService.listNode(ArtifactInfo(projectId, repoName, path), NodeListOption())
            if (nodes.isEmpty() && workspaceFiles.isNullOrEmpty()) {
                throw NodeNotFoundException(path)
            }
            workspaceFiles?.forEach { it.name = PathUtils.resolveName(PathUtils.normalizeFullPath(it.fullPath)) }
            val record = TUserShareRecord(
                projectId = projectId,
                repoName = repoName,
                path = path,
                expiredDate = expireSeconds?.let { LocalDateTime.now().plusSeconds(expireSeconds!!) },
                permits = permits,
                workspaceName = workspaceName,
                workspaceFiles = workspaceFiles,
                createBy = SecurityUtils.getUserId(),
                createDate = LocalDateTime.now(),
                lastModifiedDate = LocalDateTime.now()
            )
            val tUserShareRecord = userShareRecordDao.insert(record)
            ActionAuditContext.current().addExtendData(TUserShareRecord::id.name, tUserShareRecord.id)
            ActionAuditContext.current()
                .addExtendData(TUserShareRecord::workspaceFiles.name, tUserShareRecord.workspaceFiles)
            return convert(tUserShareRecord)
        }
    }

    fun info(id: String): UserShareRecordInfo {
        val shareRecord = userShareRecordDao.findById(id)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        checkShareRecord(shareRecord)
        return convert(shareRecord)
    }

    fun findById(id: String): UserShareRecordInfo {
        return userShareRecordDao.findById(id)?.let { convert(it) }
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
    }

    fun generateUrls(request: UserShareGenUrlRequest): UserShareUrls {
        with(request) {
            val record = userShareRecordDao.findById(id)
                ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
            ActionAuditContext.current().scopeId = record.projectId
            ActionAuditContext.current().setInstanceId(record.path)
            ActionAuditContext.current().setInstanceName(record.path)
            ActionAuditContext.current().addAttribute(PROJECT_CODE_TEMPLATE, record.projectId)
            ActionAuditContext.current().addAttribute(REPO_NAME_TEMPLATE, record.repoName)
            ActionAuditContext.current().addExtendData(TUserShareRecord::workspaceName.name, record.workspaceName)
            ActionAuditContext.current().addExtendData(TUserShareRecord::workspaceFiles.name, record.workspaceFiles)
            permissionManager.checkNodePermission(
                PermissionAction.DOWNLOAD, record.projectId, record.repoName, record.path
            )
            checkShareRecord(record)
            val nodes = nodeService.listNode(
                artifact = ArtifactInfo(record.projectId, record.repoName, record.path),
                option = NodeListOption(includeFolder = false, deep = true)
            )
            val needApproval = checkFileSize(nodes)
            if (needApproval) {
                val approval = userShareApprovalService.checkApprovalStatus(record.id!!)
                ActionAuditContext.current().addExtendData(TUserShareApproval::approvalId.name, approval.approvalId)
                ActionAuditContext.current()
                    .addExtendData(TUserShareApproval::approveUserId.name, approval.approveUserId)
                ActionAuditContext.current().addExtendData(TUserShareApproval::approveDate.name, approval.approveDate)
            }
            record.permits?.let {
                userShareRecordDao.decrementPermit(record.id!!)
            }
            val fullPathList = nodes.map { it.fullPath }
            return buildUrls(record.projectId, record.repoName, fullPathList)
        }
    }

    fun checkShareRecord(record: TUserShareRecord) {
        if (record.permits != null && record.permits <= 0) {
            throw UserShareNoPermitsException(record.id!!)
        }
        if (record.expiredDate != null && record.expiredDate.isBefore(LocalDateTime.now())) {
            throw UserShareExpiredException(record.id!!)
        }
    }

    fun checkFileSize(nodes: List<NodeInfo>): Boolean {
        if (!devXProperties.enabled) {
            return false
        }
        nodes.forEach { node ->
            if (node.size > devXProperties.shareFileSizeLimit.toBytes()) {
                return true
            }
        }
        return false
    }

    fun buildUrls(projectId: String, repoName: String, fullPathList: List<String>): UserShareUrls {
        val urls = mutableListOf<String>()
        fullPathList.forEach {
            val url = "/web/generic/$projectId/$repoName$it?download=true"
            urls.add(url)
        }
        return UserShareUrls(urls)
    }

    // TODO 后续配置从蓝盾查询
    fun getConfig(projectId: String, repoName: String): UserShareConfigInfo {
        return UserShareConfigInfo(
            projectId = projectId,
            repoName = repoName,
            sizeLimit = devXProperties.shareFileSizeLimit.toBytes(),
            createdBy = SecurityUtils.getUserId(),
            createdDate = LocalDateTime.now(),
            lastModifiedBy = SecurityUtils.getUserId(),
            lastModifiedDate = LocalDateTime.now(),
        )
    }

    private fun convert(record: TUserShareRecord): UserShareRecordInfo {
        with(record) {
            return UserShareRecordInfo(
                id = id!!,
                url = "/s/$projectId/$id",
                projectId = projectId,
                repoName = repoName,
                path = path,
                expiredDate = expiredDate,
                permits = permits,
                workspaceName = workspaceName,
                workspaceFiles = workspaceFiles,
                createBy = createBy,
                createDate = createDate,
                lastModifiedDate = lastModifiedDate
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserShareService::class.java)
    }
}
