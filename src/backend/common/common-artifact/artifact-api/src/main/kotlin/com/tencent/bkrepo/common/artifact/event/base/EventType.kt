/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.event.base

/**
 * 事件类型
 */
enum class EventType(val nick: String, val requestURI: List<String>, val method: String) {
    // PROJECT
    PROJECT_CREATED("创建项目", listOf(), ""),

    // REPOSITORY
    REPO_CREATED("创建仓库", listOf(), ""),
    REPO_UPDATED("更新仓库", listOf(), ""),
    REPO_DELETED("删除仓库", listOf(), ""),
    // 主要针对代理仓库需要定时从远程将相关信息同步到本地
    REPO_REFRESHED("刷新仓库信息", listOf(), ""),

    // NODE
    NODE_CREATED("创建节点", listOf(), ""),
    NODE_RENAMED("重命名节点", listOf(), ""),
    NODE_MOVED("移动节点", listOf(), ""),
    NODE_COPIED("复制节点", listOf(), ""),
    NODE_DELETED("删除节点", listOf(), ""),
    NODE_DOWNLOADED("下载节点", listOf(), ""),

    // METADATA
    METADATA_DELETED("删除元数据", listOf(), ""),
    METADATA_SAVED("添加元数据", listOf(), ""),

    // VERSION
    VERSION_CREATED("创建制品", listOf(), ""),
    VERSION_DELETED("删除制品", listOf(), ""),
    VERSION_DOWNLOAD("下载制品", listOf(), ""),
    VERSION_UPDATED("更新制品", listOf(), ""),
    VERSION_STAGED("晋级制品", listOf(), ""),

    // ADMIN
    ADMIN_ADD("添加管理员", listOf(), ""),
    ADMIN_DELETE("移除管理员", listOf(), ""),

    // WebHook
    WEBHOOK_TEST("webhook测试", listOf(), ""),
    WEBHOOK_LIST("webhook查询", listOf("/api/webhook/list"), "GET"),
    WEBHOOK_CREATE("webhook创建", listOf("/api/webhook/create"), "POST"),
    WEBHOOK_UPDATE("webhook更新", listOf("/api/webhook/update"), "PUT"),
    WEBHOOK_DETELE("webhook删除", listOf("/api/webhook/detele"), "DELETE"),
    WEBHOOK_LOG_LIST("webhook日志查询", listOf("/api/log/list"), "GET"),

    // ACCOUNT
    ACCOUNT_ADD("平台账户添加", listOf("/api/account/create"), "POST"),
    ACCOUNT_DELETE("平台账户删除", listOf("/api/account/delete"), "DELETE"),
    ACCOUNT_UPDATE("平台账户修改", listOf("/api/account/update"), "PUT"),
    ACCOUNT_LIST("平台账户查询", listOf("/api/account/list"), "GET"),

    // AK/SK
    KEYS_CREATE("账户AK/SK新增", listOf("/api/account/credential"), "POST"),
    KEYS_DETELE("账户AK/SK删除", listOf("/api/account/credential"), "DELETE"),
    KEYS_STATUS_UPDATE("账户AK/SK状态修改", listOf("/api/account/credential"), "PUT"),

    // OPDATA_SERVICE(顺序不能乱)
    OPDATA_SERVICE_DOWN("opdata服务下线", listOf("/api/services", "/instances", "/down"), "POST"),
    OPDATA_SERVICE_UP("opdata服务上线", listOf("/api/services", "/instances", "/up"), "POST"),
    OPDATA_SERVICE_DETAIL("opdata服务详情", listOf("/api/services", "/instances"), "GET"),
    OPDATA_SERVICE_LIST("opdata服务查询", listOf("/api/services"), "GET"),

    // EXT-PERMISSION
    EXT_PERMISSION_LIST("外部权限查询", listOf("/api/ext-permission"), "GET"),
    EXT_PERMISSION_CREAT("外部权限创建", listOf("/api/ext-permission"), "POST"),
    EXT_PERMISSION_UPDATE("外部权限修改", listOf("/api/ext-permission"), "PUT"),
    EXT_PERMISSION_DETELE("外部权限删除", listOf("/api/ext-permission"), "DETELE"),

    // JOB
    JOB_LIST("任务管理查询", listOf("/api/job/detail"), "GET"),
    JOB_STATUS_UPDATE("任务状态更改", listOf("/api/job/update"), "PUT"),

    // SHED_LOCK
    SHED_LOCK_LIST("数据库锁查询", listOf("/api/shedlock/list"), "GET"),

    // OPDATA_PLUGIN
    OPDATE_PLUGIN_LIST("opdata插件查询", listOf("/api/plugin"), "GET"),
    OPDATE_PLUGIN_CREATE("opdata插件新建", listOf("/api/plugin"), "POST"),
    OPDATE_PLUGIN_DETELE("opdata插件删除", listOf("/api/plugin"), "DETELE"),
    OPDATE_PLUGIN_UPDATE("opdata插件更新", listOf("/api/plugin"), "PUT"),
    OPDATE_PLUGIN_LOAD("opdata插件加载", listOf("/api/plugin/load"), "POST"),
    OPDATE_PLUGIN_UNLOAD("opdata插件卸载", listOf("/api/plugin/unload"), "DELETE"),

    // OPDATA_FILESYSTEM
    OPDATE_FILE_SYSTEM_METRICS("挂载分布式文件系统节点统计功能", listOf("/api/fileSystem/storage/metrics"), "GET"),
    OPDATE_FILE_SYSTEM_METRICS_DETAIL("统计某个挂载路径下子目录文件大小", listOf("/api/fileSystem/storage/metricsDetail"), "GET"),

    // OPDATA_EMPLTY_FOLDER
    EMPTY_FOLDER_LIST("空目录查询", listOf("/api/nodeOperation/emptyFolders"), "GET"),
    EMPTY_FOLDER_DELETE("清空空目录", listOf("/api/nodeOperation/emptyFolders"), "DETELE"),

    // FIRST_FOLDER
    FIRST_LEVEL_FOLDER_STATISTICS("一级目录统计", listOf("/api/nodeOperation/firstLevelFolder"), "GET")
}
