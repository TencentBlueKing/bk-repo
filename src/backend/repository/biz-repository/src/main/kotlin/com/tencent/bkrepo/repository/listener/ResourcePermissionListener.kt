/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServiceRoleClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.artifact.event.project.ProjectCreatedEvent
import com.tencent.bkrepo.common.artifact.event.repo.RepoCreatedEvent
import com.tencent.bkrepo.common.artifact.event.repo.RepoDeletedEvent
import com.tencent.bkrepo.common.artifact.event.repo.RepoUpdatedEvent
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 用于创建资源权限的时间监听器
 * 创建项目/仓库时，为当前用户创建对应项目/仓库的管理员权限
 */
@Component
class ResourcePermissionListener(
    private val roleResource: ServiceRoleClient,
    private val userResource: ServiceUserClient,
    private val bkiamV3Resource: ServiceBkiamV3ResourceClient
) {

    /**
     * 创建项目时，为当前用户创建对应项目的管理员权限
     */
    fun handle(event: ProjectCreatedEvent) {
        with(event) {
            if (!createPermission) {
                return
            }
            if (isAuthedNormalUser(userId) && isNeedLocalPermission(projectId)) {
                val projectManagerRoleId = roleResource.createProjectManage(projectId).data!!
                userResource.addUserRole(userId, projectManagerRoleId)
                bkiamV3Resource.createProjectManage(userId, projectId)
            }
        }
    }

    /**
     * 创建仓库时，为当前用户创建对应仓库的管理员权限
     */
    @Async
    @EventListener(RepoCreatedEvent::class)
    fun handle(event: RepoCreatedEvent) {
        with(event) {
            if (isAuthedNormalUser(userId) && isNeedLocalPermission(projectId)) {
                val repoManagerRoleId = roleResource.createRepoManage(projectId, repoName).data!!
                userResource.addUserRole(userId, repoManagerRoleId)
                bkiamV3Resource.createRepoManage(userId, projectId, repoName)
            }
        }
    }

    @Async
    @EventListener(RepoUpdatedEvent::class)
    fun handle(event: RepoUpdatedEvent) {
        with(event) {
            if (isAuthedNormalUser(userId) && isNeedLocalPermission(projectId)) {
                bkiamV3Resource.createRepoManage(userId, projectId, repoName)
            }
        }
    }

    /**
     * 删除仓库时，需要删除当前在权限中心创建的分级管理员
     */
    @Async
    @EventListener(RepoDeletedEvent::class)
    fun handle(event: RepoDeletedEvent) {
        with(event) {
            bkiamV3Resource.deleteRepoManageGroup(userId, projectId, repoName)
        }
    }

    /**
     * 判断是否为经过认证的普通用户(非匿名用户 & 非系统用户)
     *
     */
    private fun isAuthedNormalUser(userId: String): Boolean {
        return userId != SYSTEM_USER && userId != ANONYMOUS_USER
    }

    private fun isNeedLocalPermission(projectId: String): Boolean {
        if (projectId.startsWith(CODE_PROJECT_PREFIX) || projectId.startsWith(CLOSED_SOURCE_PREFIX)) {
            return false
        }
        return true
    }

    companion object {
        private const val CODE_PROJECT_PREFIX = "CODE_"
        private const val CLOSED_SOURCE_PREFIX = "CLOSED_SOURCE_"
    }
}
