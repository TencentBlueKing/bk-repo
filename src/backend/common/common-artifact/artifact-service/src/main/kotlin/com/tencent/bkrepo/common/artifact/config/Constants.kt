package com.tencent.bkrepo.common.artifact.config

/**
 * header user id key
 */
const val AUTH_HEADER_USER_ID: String = "X-BKREPO-UID"
/**
 * 查询仓库后将仓库写入request attributes的key
 */
const val REPO_KEY = "repository"
/**
 * 解析构件信息后写入request attributes的key
 */
const val ARTIFACT_INFO_KEY = "artifact"
/**
 * 项目id字段
 */
const val PROJECT_ID = "projectId"
/**
 * 仓库名称字段
 */
const val REPO_NAME = "repoName"
/**
 * 认证相关
 */
const val BASIC_AUTH_HEADER = "Authorization"
const val BASIC_AUTH_HEADER_PREFIX = "Basic "
const val BASIC_AUTH_RESPONSE_HEADER = "WWW-Authenticate"
const val BASIC_AUTH_RESPONSE_VALUE = "Basic realm=\"Login Required\""

/**
 * 构件传输相关
 */
const val OCTET_STREAM = "octet-stream"
const val ATTRIBUTE_SHA256MAP = "artifact.sha256"
const val ATTRIBUTE_OCTET_STREAM_SHA256 = "artifact.sha256.octet-stream"
const val DEFAULT_MIME_TYPE = "application/octet-stream"
const val CONTENT_DISPOSITION_TEMPLATE = "attachment; filename=\"%s\""
const val BYTES = "bytes="

/**
 * 虚拟仓库相关
 */
const val TRAVERSED_LIST = "traversed"
