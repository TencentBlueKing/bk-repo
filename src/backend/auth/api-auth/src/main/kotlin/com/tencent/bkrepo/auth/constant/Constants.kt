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

package com.tencent.bkrepo.auth.constant

/**
 * 认证相关
 */

const val PROJECT_MANAGE_ID = "project_manage"

const val PROJECT_MANAGE_NAME = "项目管理员"

const val PROJECT_VIEWER_ID = "project_view"

const val PROJECT_VIEWER_NAME = "项目用户"

const val REPO_MANAGE_ID = "repo_manage"

const val REPO_MANAGE_NAME = "仓库管理员"

const val DEFAULT_PASSWORD = "blueking"

const val AUTHORIZATION = "Authorization"

const val AUTH_FAILED_RESPONSE = "{\"code\":401,\"message\":\"Authorization value [%s] " +
    "is not a valid scheme.\",\"data\":null,\"traceId\":\"\"}"

const val BASIC_AUTH_HEADER_PREFIX = "Basic "

const val PLATFORM_AUTH_HEADER_PREFIX = "Platform "

const val RANDOM_KEY_LENGTH = 30

const val BKREPO_TICKET = "bkrepo_ticket"

const val AUTH_REPO_SUFFIX = "/create/repo"

const val AUTH_PROJECT_SUFFIX = "/create/project"

const val AUTH_API_PERMISSION_PREFIX = "/api/permission"
const val AUTH_SERVICE_PERMISSION_PREFIX = "/service/permission"
const val AUTH_CLUSTER_PERMISSION_PREFIX = "/cluster/permission"

const val AUTH_API_ROLE_PREFIX = "/api/role"
const val AUTH_SERVICE_ROLE_PREFIX = "/service/role"


const val AUTH_SERVICE_BKIAMV3_PREFIX = "/service/bkiamv3"


const val AUTH_API_USER_PREFIX = "/api/user"
const val AUTH_SERVICE_USER_PREFIX = "/service/user"
const val AUTH_CLUSTER_USER_PREFIX = "/cluster/permission"

const val AUTH_API_DEPARTMENT_PREFIX = "/api/department"

const val AUTH_SERVICE_ACCOUNT_PREFIX = "/service/account"
const val AUTH_API_ACCOUNT_PREFIX = "/api/account"

const val AUTH_API_KEY_PREFIX = "/api/key"

const val AUTH_API_OAUTH_PREFIX = "/api/oauth"
const val AUTH_SERVICE_OAUTH_PREFIX = "/service/oauth"

const val AUTH_API_PROJECT_ADMIN_PREFIX = "/api/user/admin"
const val AUTH_API_USER_INFO_PREFIX = "/api/user/userinfo"
const val AUTH_API_TOKEN_LIST_PREFIX = "api/user/list/token"
const val AUTH_API_TOKEN_PREFIX = "api/user/token"
const val AUTH_API_USER_LIST_PREFIX = "api/user/list"
const val AUTH_API_INFO_PREFIX = "api/user/info"
const val AUTH_API_ROLE_SYS_LIST_PREFIX = "api/role/sys/list"

const val AUTH_API_USER_UPDATE_PREFIX = "api/user/update/info"
const val AUTH_API_USER_DELETE_PREFIX = "api/user/delete"
const val AUTH_API_USER_ASSET_USER_GROUP_PREFIX = "api/user/group"
const val AUTH_API_USER_BKIAMV3_PREFIX = "api/user/auth"

const val AUTH_CLUSTER_TOKEN_INFO_PREFIX = "/cluster/temporary/token/info"
const val AUTH_CLUSTER_TOKEN_DELETE_PREFIX = "/cluster/temporary/token/delete"
const val AUTH_CLUSTER_TOKEN_DECREMENT_PREFIX = "/cluster/temporary/token/decrement"
const val AUTH_CLUSTER_PERMISSION_CHECK_PREFIX = "/cluster/permission/check"

const val AUTH_API_EXT_PERMISSION_PREFIX = "/api/ext-permission"
const val AUTH_SERVICE_EXT_PERMISSION_PREFIX = "/service/ext-permission"

const val AUTH_API_PERMISSION_LIST_PREFIX = "/api/permission/list"
const val AUTH_API_PERMISSION_CREATE_PREFIX = "/api/permission/create"
const val AUTH_API_PERMISSION_DELETE_PREFIX = "/api/permission/delete"
const val AUTH_API_PERMISSION_UPDATE_PREFIX = "/api/permission/update/config"

const val AUTH_API_PERMISSION_LIST_IN_PROJECT_PREFIX = "api/permission/list/inproject"
const val AUTH_API_PERMISSION_USER_PREFIX = "api/permission/user"

const val AUTH_ADMIN = "admin"
const val AUTH_BUILTIN_ADMIN = "repo_admin"
const val AUTH_BUILTIN_USER = "repo_user"
const val AUTH_BUILTIN_VIEWER = "repo_viewer"
