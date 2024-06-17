/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.job

import com.tencent.bkrepo.auth.pojo.role.RoleSource
import com.tencent.bkrepo.auth.pojo.role.UpdateRoleRequest
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.RoleService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ExternalGroupSyncJob(
    private val roleService: RoleService,
    private val permissionService: PermissionService
) {

    @Scheduled(cron = "0 */10 * * * ? ")
    @SchedulerLock(name = "DevopsUserGroupSync", lockAtMostFor = "PT10M")
    fun runDevopsUserGroupSync() {
        logger.info("start to update external role")
        val roleList = roleService.listRoleBySource(RoleSource.DEVOPS)
        val projectIdSet = mutableSetOf<String>()
        val indexIdMap = mutableMapOf<String, String>()
        val projectIdMap = mutableMapOf<String, String>()
        roleList.forEach {
            projectIdSet.add(it.projectId)
            indexIdMap[it.roleId] = it.id!!
            projectIdMap[it.roleId] = it.projectId
        }

        projectIdSet.forEach { project ->
            val roleUserMap = mutableMapOf<String, List<String>>()
            permissionService.listExternalRoleByProject(project, RoleSource.DEVOPS).forEach { externalRole ->
                roleUserMap[externalRole.roleId] = externalRole.userList
            }
            roleList.forEach { role ->
                if (projectIdMap[role.roleId] == project) {
                    val updateRequest = UpdateRoleRequest(
                        userIds = roleUserMap[role.roleId]!!.toSet(),
                        description = null,
                        name = null
                    )
                    logger.info("to update external role [${role.roleId}] ")
                    roleService.updateRoleInfo(indexIdMap[role.roleId]!!, updateRequest)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExternalGroupSyncJob::class.java)
    }
}
