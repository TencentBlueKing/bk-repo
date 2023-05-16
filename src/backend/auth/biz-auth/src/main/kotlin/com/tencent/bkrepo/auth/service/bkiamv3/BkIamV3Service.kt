/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.bkiamv3

import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest

/**
 * bk iamv3接口调用
 */
interface BkIamV3Service {

    /**
     * 判断权限中心相关配置是否配置
     */
    fun checkIamConfiguration(): Boolean

    /**
     * 判断是否开启了蓝鲸权限配置
     */
    fun checkBkiamv3Config(projectId: String?, repoName: String?): Boolean

    /**
     * 生成无权限跳转url
     */
    fun getPermissionUrl(request: CheckPermissionRequest): String?

    /**
     * 鉴权
     */
    fun validateResourcePermission(
        userId: String,
        projectId: String,
        repoName: String?,
        resourceType: String,
        action: String,
        resourceId: String,
        appId: String?
    ): Boolean

    /**
     * 仓库资源id转换
     */
    fun convertRepoResourceId(projectId: String, repoName: String): String?

    /**
     * 节点资源id转换
     */
    fun convertNodeResourceId(projectId: String, repoName: String, fullPath: String): String?

    /**
     * 获取有权限的资源列表
     */
    fun listPermissionResources(
        userId: String,
        projectId: String? = null,
        resourceType: String,
        action: String,
    ): List<String>

    /**
     * 刷新项目以及旗下仓库对应的权限中心权限
     */
    fun refreshProjectManager(userId: String? = null, projectId: String): Boolean


    /**
     * 创建项目分级管理员
     */
    fun createGradeManager(
        userId: String,
        projectId: String,
        repoName: String? = null
    ): String?

    /**
     * 删除分级管理员
     */
    fun deleteGradeManager(
        projectId: String,
        repoName: String? = null
    ): Boolean

    /**
     * 资源id转换
     */
    fun getResourceId(resourceType: String, projectId: String?, repoName: String?, path: String?): String?


    /**
     * 查询列表中的项目是否已生成rbac默认用户组
     */
    fun getExistRbacDefaultGroupProjectIds(ids: List<String>) : Map<String, Boolean>
}
