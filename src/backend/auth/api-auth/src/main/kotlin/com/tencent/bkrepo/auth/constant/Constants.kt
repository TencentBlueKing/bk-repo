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

const val AUTH_FAILED_RESPONSE =
    "{\"code\":401,\"message\":\"Authorization value [%s] is not a valid scheme.\",\"data\":null,\"traceId\":\"\"}"

const val PLATFORM_AUTH_HEADER_PREFIX = "Platform "
