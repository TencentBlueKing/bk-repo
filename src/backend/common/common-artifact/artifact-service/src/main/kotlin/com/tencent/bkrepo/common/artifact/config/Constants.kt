package com.tencent.bkrepo.common.artifact.config

/**
 * 认证成功后username写入request attributes的key
 */
const val USER_KEY = "artifact.userId"
/**
 * 查询仓库后将仓库写入request attributes的key
 */
const val REPO_KEY = "artifact.repo"
/**
 * 解析构件信息后写入request attributes的key
 */
const val ARTIFACT_INFO_KEY = "artifact.info"
/**
 * 项目id字段
 */
const val PROJECT_ID = "projectId"
/**
 * 仓库名称字段
 */
const val REPO_NAME = "repoName"
/**
 *  匿名用户
 */
const val ANONYMOUS_USER = "anonymous"
/**
 * 认证相关
 */
const val BASIC_AUTH_HEADER = "Authorization"
const val BASIC_AUTH_HEADER_PREFIX = "Basic "
const val BASIC_AUTH_RESPONSE_HEADER = "WWW-Authenticate"
const val BASIC_AUTH_RESPONSE_VALUE = "Basic realm=\"Login Required\""
