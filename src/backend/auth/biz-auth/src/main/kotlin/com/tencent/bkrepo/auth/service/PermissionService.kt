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

package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionDeployInRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionUserRequest

interface PermissionService {

    /**
     * 校验平台账号权限
     */
    fun checkPlatformPermission(request: CheckPermissionRequest): Boolean

    /**
     * 校验普通账号权限
     */
    fun checkPermission(request: CheckPermissionRequest): Boolean

    /**
     * 获取有权限的仓库列表
     */
    fun listPermissionRepo(projectId: String, userId: String, appId: String?): List<String>

    /**
     * 获取权限详情
     */
    fun getPermission(permissionId: String): Permission?

    /**
     * 获取有权限的项目列表
     */
    fun listPermissionProject(userId: String): List<String>

    /**
     * 获取有权限路径列表
     */
    fun listNoPermissionPath(userId: String, projectId: String, repoName: String): List<String>

    fun createPermission(request: CreatePermissionRequest): Boolean

    fun listPermission(projectId: String, repoName: String?, resourceType: String?): List<Permission>

    fun listBuiltinPermission(projectId: String, repoName: String): List<Permission>

    fun deletePermission(id: String): Boolean

    fun updateRepoPermission(request: UpdatePermissionRepoRequest): Boolean

    fun updatePermissionUser(request: UpdatePermissionUserRequest): Boolean

    fun listProjectBuiltinPermission(projectId: String): List<Permission>

    fun updatePermissionDeployInRepo(request: UpdatePermissionDeployInRepoRequest): Boolean

}
