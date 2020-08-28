package com.tencent.bkrepo.common.artifact.constant

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
 * 构件传输相关
 */
const val OCTET_STREAM = "octet-stream"
const val ATTRIBUTE_SHA256MAP = "artifact.sha256"
const val ATTRIBUTE_OCTET_STREAM_SHA256 = "artifact.sha256.octet-stream"
const val ATTRIBUTE_MD5MAP = "artifact.md5"
const val ATTRIBUTE_OCTET_STREAM_MD5 = "artifact.md5.octet-stream"
const val CONTENT_DISPOSITION_TEMPLATE = "attachment;filename=\"%s\";filename*=UTF-8''%s"

/**
 * 虚拟仓库相关
 */
const val TRAVERSED_LIST = "traversed"

/**
 * 公共源代理项目名称
 */
const val PUBLIC_PROXY_PROJECT = "public-proxy"
/**
 * 公共源代理仓库名称, <RepoType>-<ChannelName>
 */
const val PUBLIC_PROXY_REPO_NAME = "%s-%s"
/**
 * 私有源代理仓库名称, <RepoName>-<ChannelName>
 */
const val PRIVATE_PROXY_REPO_NAME = "%s-%s"
