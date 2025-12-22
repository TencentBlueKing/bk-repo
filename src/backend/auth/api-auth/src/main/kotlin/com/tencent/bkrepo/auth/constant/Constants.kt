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

package com.tencent.bkrepo.auth.constant

/**
 * 认证模块常量定义
 * 
 * 按功能分组：
 * - RoleConstants: 角色相关常量
 * - AuthConstants: 认证相关常量
 * - PathConstants: API 路径前缀常量
 * - UserConstants: 内置用户常量
 */

/**
 * 角色常量
 * 定义系统内置角色的 ID 和名称
 */
object RoleConstants {
    /** 项目管理员角色 ID */
    const val PROJECT_MANAGE_ID = "project_manage"
    /** 项目管理员角色名称 */
    const val PROJECT_MANAGE_NAME = "project manager"
    
    /** 项目查看者角色 ID */
    const val PROJECT_VIEWER_ID = "project_view"
    /** 项目查看者角色名称 */
    const val PROJECT_VIEWER_NAME = "project viewer"
    
    /** 仓库管理员角色 ID */
    const val REPO_MANAGE_ID = "repo_manage"
    /** 仓库管理员角色名称 */
    const val REPO_MANAGE_NAME = "repo  manager"
    
    /** 同步管理员角色 ID */
    const val REPLICATION_MANAGE_ID = "replication_manage"
    /** 同步管理员角色名称 */
    const val REPLICATION_MANAGE_NAME = "replication manager"
}

/**
 * 认证常量
 * 包含认证相关的配置和常量
 */
object AuthConstants {
    /** 默认密码 */
    const val DEFAULT_PASSWORD = "blueking"
    
    /** Authorization 请求头名称 */
    const val AUTHORIZATION = "Authorization"
    
    /** 认证失败响应模板 */
    const val AUTH_FAILED_RESPONSE = "{\"code\":401,\"message\":\"Authorization value [%s] " +
            "is not a valid scheme.\",\"data\":null,\"traceId\":\"\"}"
    
    /** Basic 认证头前缀 */
    const val BASIC_AUTH_HEADER_PREFIX = "Basic "
    
    /** Platform 认证头前缀 */
    const val PLATFORM_AUTH_HEADER_PREFIX = "Platform "
    
    /** 随机密钥长度 */
    const val RANDOM_KEY_LENGTH = 30
    
    /** BK-Repo Ticket Cookie 名称 */
    const val BKREPO_TICKET = "bkrepo_ticket"
}

/**
 * API 路径前缀常量
 * 统一管理所有 API 路径前缀，便于维护和修改
 */
object PathConstants {
    
    // ========== 基础路径 ==========
    private const val API_PREFIX = "/api"
    private const val SERVICE_PREFIX = "/service"
    private const val CLUSTER_PREFIX = "/cluster"
    
    // ========== Permission 权限相关 ==========
    /** API 权限路径前缀 */
    const val AUTH_API_PERMISSION_PREFIX = "$API_PREFIX/permission"
    /** Service 权限路径前缀 */
    const val AUTH_SERVICE_PERMISSION_PREFIX = "$SERVICE_PREFIX/permission"
    /** Cluster 权限路径前缀 */
    const val AUTH_CLUSTER_PERMISSION_PREFIX = "$CLUSTER_PREFIX/permission"
    /** API 扩展权限路径前缀 */
    const val AUTH_API_EXT_PERMISSION_PREFIX = "$API_PREFIX/ext-permission"
    /** Service 扩展权限路径前缀 */
    const val AUTH_SERVICE_EXT_PERMISSION_PREFIX = "$SERVICE_PREFIX/ext-permission"
    
    // ========== Role 角色相关 ==========
    /** API 角色路径前缀 */
    const val AUTH_API_ROLE_PREFIX = "$API_PREFIX/role"
    /** Service 角色路径前缀 */
    const val AUTH_SERVICE_ROLE_PREFIX = "$SERVICE_PREFIX/role"
    
    // ========== User 用户相关 ==========
    /** API 用户路径前缀 */
    const val AUTH_API_USER_PREFIX = "$API_PREFIX/user"
    /** Service 用户路径前缀 */
    const val AUTH_SERVICE_USER_PREFIX = "$SERVICE_PREFIX/user"
    
    // ========== Account 账号相关 ==========
    /** API 账号路径前缀 */
    const val AUTH_API_ACCOUNT_PREFIX = "$API_PREFIX/account"
    /** Service 账号路径前缀 */
    const val AUTH_SERVICE_ACCOUNT_PREFIX = "$SERVICE_PREFIX/account"
    
    // ========== OAuth 相关 ==========
    /** API OAuth 路径前缀 */
    const val AUTH_API_OAUTH_PREFIX = "$API_PREFIX/oauth"
    /** Service OAuth 路径前缀 */
    const val AUTH_SERVICE_OAUTH_PREFIX = "$SERVICE_PREFIX/oauth"
    
    // ========== BK-IAM v3 相关 ==========
    /** Service BK-IAM v3 路径前缀 */
    const val AUTH_SERVICE_BKIAMV3_PREFIX = "$SERVICE_PREFIX/bkiamv3"
    
    // ========== Auth Mode 认证模式相关 ==========
    /** API 认证模式路径前缀 */
    const val AUTH_API_AUTH_MODE_PREFIX = "$API_PREFIX/mode/repo"
    
    // ========== Cluster 集群相关 ==========
    /** Cluster 基础路径前缀 */
    const val AUTH_CLUSTER_PREFIX = CLUSTER_PREFIX
    /** Cluster 临时 Token 信息路径 */
    const val AUTH_CLUSTER_TOKEN_INFO_PREFIX = "$CLUSTER_PREFIX/temporary/token/info"
    /** Cluster 临时 Token 删除路径 */
    const val AUTH_CLUSTER_TOKEN_DELETE_PREFIX = "$CLUSTER_PREFIX/temporary/token/delete"
    /** Cluster 临时 Token 递减路径 */
    const val AUTH_CLUSTER_TOKEN_DECREMENT_PREFIX = "$CLUSTER_PREFIX/temporary/token/decrement"
    /** Cluster 权限检查路径 */
    const val AUTH_CLUSTER_PERMISSION_CHECK_PREFIX = "$CLUSTER_PREFIX/permission/check"
    
    /**
     * 构建 API 路径
     * @param resource 资源名称（如 permission、role、user 等）
     * @return API 路径
     */
    fun buildApiPath(resource: String): String = "$API_PREFIX/$resource"
    
    /**
     * 构建 Service 路径
     * @param resource 资源名称
     * @return Service 路径
     */
    fun buildServicePath(resource: String): String = "$SERVICE_PREFIX/$resource"
    
    /**
     * 构建 Cluster 路径
     * @param resource 资源名称
     * @return Cluster 路径
     */
    fun buildClusterPath(resource: String): String = "$CLUSTER_PREFIX/$resource"
}

/**
 * 内置用户常量
 * 定义系统内置的特殊用户
 */
object UserConstants {
    /** 系统管理员用户 */
    const val AUTH_ADMIN = "admin"
    
    /** 内置仓库管理员用户 */
    const val AUTH_BUILTIN_ADMIN = "repo_admin"
    
    /** 内置仓库普通用户 */
    const val AUTH_BUILTIN_USER = "repo_user"
    
    /** 内置仓库查看者用户 */
    const val AUTH_BUILTIN_VIEWER = "repo_viewer"
}









