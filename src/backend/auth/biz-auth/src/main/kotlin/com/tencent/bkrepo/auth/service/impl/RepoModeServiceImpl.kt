/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.auth.service.impl

import com.tencent.bkrepo.auth.config.AuthProperties
import com.tencent.bkrepo.auth.context.FederationWriteContext
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.RepoAuthConfigDao
import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.RepoAuthConfigInfo
import com.tencent.bkrepo.auth.pojo.permission.RepoModeStatus
import com.tencent.bkrepo.auth.service.RepoModeService
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import org.springframework.stereotype.Service

@Service
class RepoModeServiceImpl(
    private val repoAuthDao: RepoAuthConfigDao,
    private val permissionDao: PermissionDao,
    private val repositoryService: RepositoryService,
    private val messageSupplier: MessageSupplier,
    private val authProperties: AuthProperties
) : RepoModeService {

    override fun createOrUpdateConfig(
        projectId: String,
        repoName: String,
        accessControlMode: AccessControlMode?,
        officeDenyGroupSet: Set<String>,
        bkiamv3Check: Boolean
    ): RepoModeStatus? {
        val repoDetail = repositoryService.getRepoDetail(projectId, repoName) ?: return null
        if (repoDetail.public) return null
        var controlMode = accessControlMode
        if (accessControlMode == null) {
            controlMode = AccessControlMode.DEFAULT
        }
        val id = repoAuthDao.upsertProjectRepo(projectId, repoName, controlMode!!, officeDenyGroupSet, bkiamv3Check)
        publishEvent(projectId, repoName)
        return RepoModeStatus(id, accessControlMode, officeDenyGroupSet, bkiamv3Check)
    }


    override fun getAccessControlStatus(projectId: String, repoName: String): RepoModeStatus {
        var controlMode = AccessControlMode.DEFAULT
        var officeDenyGroupSet = emptySet<String>()
        var bkiamv3Check = false
        if (permissionDao.listByResourceAndRepo(ResourceType.NODE.name, projectId, repoName).isNotEmpty()) {
            controlMode = AccessControlMode.DIR_CTRL
        }
        val result = repoAuthDao.findOneByProjectRepo(projectId, repoName)
        if (result != null) {
            if (result.officeDenyGroupSet != null) {
                officeDenyGroupSet = result.officeDenyGroupSet!!
            }
            if (result.accessControlMode != null) {
                controlMode = result.accessControlMode!!
            }
            // 老的数据， 严格模式直接切换
            if (result.accessControl != null && result.accessControl!! && result.accessControlMode == null) {
                controlMode = AccessControlMode.STRICT
            }
            bkiamv3Check = result.bkiamv3Check ?: false
        }
        val id = repoAuthDao.upsertProjectRepo(projectId, repoName, controlMode, officeDenyGroupSet, bkiamv3Check)
        return RepoModeStatus(
            id = id,
            accessControlMode = controlMode,
            officeDenyGroupSet = officeDenyGroupSet,
            bkiamv3Check = bkiamv3Check
        )
    }

    private fun publishEvent(projectId: String, repoName: String) {
        if (!authProperties.eventEnabled || FederationWriteContext.isFederationWrite()) return
        val event = ArtifactEvent(
            type = EventType.REPO_AUTH_CONFIG_UPDATED,
            projectId = projectId,
            repoName = repoName,
            resourceKey = "$projectId/$repoName",
            userId = runCatching { SecurityUtils.getUserId() }.getOrDefault(""),
            eventId = ArtifactEvent.generateEventId()
        )
        messageSupplier.delegateToSupplier(event, topic = BINDING_OUT_NAME)
    }

    override fun listByProject(projectId: String): List<RepoAuthConfigInfo> {
        return repoAuthDao.listByProject(projectId).map { config ->
            RepoAuthConfigInfo(
                id = config.id!!,
                projectId = config.projectId,
                repoName = config.repoName,
                accessControlMode = config.accessControlMode,
                officeDenyGroupSet = config.officeDenyGroupSet ?: emptySet(),
                bkiamv3Check = config.bkiamv3Check ?: false
            )
        }
    }

    companion object {
        private const val BINDING_OUT_NAME = "artifactEvent-out-0"
    }
}
