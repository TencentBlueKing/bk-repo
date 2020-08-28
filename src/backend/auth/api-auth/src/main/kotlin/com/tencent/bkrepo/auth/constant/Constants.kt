package com.tencent.bkrepo.auth.constant

/**
 * 认证相关
 */
const val SERVICE_NAME = "auth"

const val PROJECT_MANAGE_ID = "project_manage"

const val PROJECT_MANAGE_NAME = "项目管理员"

const val REPO_MANAGE_ID = "repo_manage"

const val REPO_MANAGE_NAME = "仓库管理员"

const val DEFAULT_PASSWORD = "blueking"

const val AUTHORIZATION = "Authorization"

const val AUTH_FAILED_RESPONSE = "{\"code\":401,\"message\":\"Authorization value [%s] is not a valid scheme.\",\"data\":null,\"traceId\":\"\"}"

const val PLATFORM_AUTH_HEADER_PREFIX = "Platform "

const val RANDOM_KEY_LENGTH = 30

const val AUTH_CLUSTER_PREFIX = "/api/cluster"

const val AUTH_PERMISSION_PREFIX = "/permission"
const val AUTH_API_PERMISSION_PREFIX = "/api/permission"
const val AUTH_SERVICE_PERMISSION_PREFIX = "/service/permission"

const val AUTH_ROLE_PREFIX = "/role"
const val AUTH_API_ROLE_PREFIX = "/api/role"
const val AUTH_SERVICE_ROLE_PREFIX = "/service/role"

const val AUTH_USER_PREFIX = "/user"
const val AUTH_API_USER_PREFIX = "/api/user"
const val AUTH_SERVICE_USER_PREFIX = "/service/user"

const val AUTH_ACCOUNT_PREFIX = "/account"
const val AUTH_SERVICE_ACCOUNT_PREFIX = "/service/account"
const val AUTH_API_ACCOUNT_PREFIX = "/api/account"
