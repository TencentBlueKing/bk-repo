/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.listener

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.CLOSED_SOURCE_PREFIX
import com.tencent.bkrepo.common.api.constant.CODE_PROJECT_PREFIX
import com.tencent.bkrepo.common.artifact.event.project.ProjectCreatedEvent
import com.tencent.bkrepo.common.artifact.event.repo.RepoCreatedEvent
import com.tencent.bkrepo.common.artifact.event.repo.RepoDeletedEvent
import com.tencent.bkrepo.common.artifact.event.repo.RepoUpdatedEvent
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

/**
 * 用于创建资源权限的时间监听器
 * 创建项目/仓库时，为当前用户创建对应项目/仓库的管理员权限
 */
@Component
@Conditional(ReactiveCondition::class)
class RResourcePermissionListener(
    private val rAuthClient: RAuthClient
) {

    /**
     * 创建项目时，为当前用户创建对应项目的管理员权限
     */
    suspend fun handle(event: ProjectCreatedEvent) {
        with(event) {
            if (!createPermission) {
                return
            }
            if (isAuthedNormalUser(userId) && isNeedLocalPermission(projectId)) {
                val projectManagerRoleId = rAuthClient.createProjectManage(projectId).awaitSingle().data!!
                rAuthClient.addUserRole(userId, projectManagerRoleId).awaitSingle()
                rAuthClient.createProjectManage(userId, projectId).awaitSingle()
            }
        }
    }

    /**
     * 创建仓库时，为当前用户创建对应仓库的管理员权限
     */
    // TODO 修改为EventListener https://github.com/spring-projects/spring-framework/issues/21025
    suspend fun handle(event: RepoCreatedEvent) {
        with(event) {
            if (isAuthedNormalUser(userId) && isNeedLocalPermission(projectId)) {
                val repoManagerRoleId = rAuthClient.createRepoManage(projectId, repoName).awaitSingle().data!!
                rAuthClient.addUserRole(userId, repoManagerRoleId).awaitSingle()
                rAuthClient.createRepoManage(userId, projectId, repoName).awaitSingle()
            }
        }
    }


    // TODO 修改为EventListener
    suspend fun handle(event: RepoUpdatedEvent) {
        with(event) {
            if (isAuthedNormalUser(userId) && isNeedLocalPermission(projectId)) {
                rAuthClient.createRepoManage(userId, projectId, repoName).awaitSingle()
            }
        }
    }

    /**
     * 删除仓库时，需要删除当前在权限中心创建的分级管理员
     */
    // TODO 修改为EventListener
    suspend fun handle(event: RepoDeletedEvent) {
        with(event) {
            rAuthClient.deleteRepoManageGroup(userId, projectId, repoName).awaitSingle()
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
        return !(projectId.startsWith(CODE_PROJECT_PREFIX) || projectId.startsWith(CLOSED_SOURCE_PREFIX))
    }

}
