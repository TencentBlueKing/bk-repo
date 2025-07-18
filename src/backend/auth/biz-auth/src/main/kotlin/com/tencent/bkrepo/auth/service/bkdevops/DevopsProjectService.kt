/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.bkdevops

import com.tencent.bkrepo.auth.condition.DevopsAuthCondition
import com.tencent.bkrepo.auth.pojo.enums.BkAuthResourceType
import com.tencent.bkrepo.auth.pojo.role.ExternalRoleResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(DevopsAuthCondition::class)
class DevopsProjectService @Autowired constructor(private val ciAuthService: CIAuthService) {
    fun isProjectMember(user: String, projectCode: String, permissionAction: String): Boolean {
        return ciAuthService.isProjectSuperAdmin(
            user = user,
            projectCode = projectCode,
            resourceType = BkAuthResourceType.PIPELINE_DEFAULT,
            action = permissionAction
        ) || ciAuthService.isProjectMember(user, projectCode)
    }

    fun isProjectManager(user: String, projectCode: String): Boolean {
        return ciAuthService.isProjectManager(user, projectCode)
    }

    fun listProjectByUser(user: String): List<String> {
        return ciAuthService.getProjectListByUser(user)
    }

    fun listRoleAndUserByProject(projectCode: String): List<ExternalRoleResult> {
        val externalRoleList = mutableListOf<ExternalRoleResult>()
        ciAuthService.getRoleAndUserByProject(projectCode).forEach {
            externalRoleList.add(ExternalRoleResult(it.roleName, it.roleId.toString(), it.userIdList))
        }
        return externalRoleList
    }
}
