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
 * 查询构件配置写入request attributes的key
 */
const val ARTIFACT_CONFIGURER = "artifact-configurer"

/**
 * 查询构件节点信息后许如request attributes的key
 */
const val NODE_DETAIL_KEY = "node-detail"

/**
 * 查询仓库上传下载限速配置后后将其写入request attributes的key
 */
const val REPO_RATE_LIMIT_KEY = "repo-rate-limit"

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

/**
 * Http Content Disposition模板
 */
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

/**
 * 默认storage key
 */
const val DEFAULT_STORAGE_KEY = "default"

/**
 * 响应header check sum
 */
const val X_CHECKSUM_MD5 = "X-Checksum-Md5"
const val X_CHECKSUM_SHA256 = "X-Checksum-Sha256"

/**
 * 流水线仓库
 */
const val PIPELINE = "pipeline"

/**
 * 自定义仓库
 */
const val CUSTOM = "custom"

/**
 * lsync仓库
 */
const val LSYNC = "lsync"

/**
 * 报告仓库
 */
const val REPORT = "report"


/**
 * 日志仓库
 */
const val LOG = "log"

/**
 * 文件访问请求是否为直接下载
 */
const val PARAM_DOWNLOAD = "download"

/**
 * 文件访问请求是否为展示
 */
const val PARAM_PREVIEW = "preview"

/**
 * 用于非磁盘文件路径标识
 * */
// 内存文件
const val SOURCE_IN_MEMORY = "memory"
const val SOURCE_IN_REMOTE = "remote"

/**
 * 用于标识制品来源
 * ArtifactChannel.LOCAL
 * ArtifactChannel.PROXY
 */
const val SOURCE_TYPE = "sourceType"

// 制品禁用信息
const val FORBID_STATUS = "forbidStatus"
const val FORBID_USER = "forbidUser"
const val FORBID_TYPE = "forbidType"

// 制品扫描状态
const val SCAN_STATUS = "scanStatus"

const val METADATA_KEY_PACKAGE_NAME = "packageName"
const val METADATA_KEY_PACKAGE_VERSION = "packageVersion"

/**
 * 节点链接的目标项目
 */
const val METADATA_KEY_LINK_PROJECT = "targetProjectId"
/**
 * 节点链接的目标仓库
 */
const val METADATA_KEY_LINK_REPO = "targetRepoName"
/**
 * 节点链接的目标路径
 */
const val METADATA_KEY_LINK_FULL_PATH = "targetFullPath"
