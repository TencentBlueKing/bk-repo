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
enum class EventType(val nick: String) {
    // PROJECT
    PROJECT_CREATED("创建项目"),

    // REPOSITORY
    REPO_CREATED("创建仓库"),
    REPO_UPDATED("更新仓库"),
    REPO_DELETED("删除仓库"),
    // 主要针对代理仓库需要定时从远程将相关信息同步到本地
    REPO_REFRESHED("刷新仓库信息"),

    // NODE
    NODE_CREATED("创建节点"),
    NODE_RENAMED("重命名节点"),
    NODE_MOVED("移动节点"),
    NODE_COPIED("复制节点"),
    NODE_DELETED("删除节点"),
    NODE_DOWNLOADED("下载节点"),

    // METADATA
    METADATA_DELETED("删除元数据"),
    METADATA_SAVED("添加元数据"),

    // VERSION
    VERSION_CREATED("创建制品"),
    VERSION_DELETED("删除制品"),
    VERSION_DOWNLOAD("下载制品"),
    VERSION_UPDATED("更新制品"),
    VERSION_STAGED("晋级制品"),

    // ADMIN
    ADMIN_ADD("添加管理员"),
    ADMIN_DELETE("移除管理员"),

    // WebHook
    WEBHOOK_TEST("webhook测试"),
    WEBHOOK_LIST("webhook查询"),
    WEBHOOK_CREATE("webhook创建"),
    WEBHOOK_UPDATE("webhook更新"),
    WEBHOOK_DELETE("webhook删除"),
    WEBHOOK_LOG_LIST("webhook日志查询"),

    // ACCOUNT
    ACCOUNT_ADD("平台账户添加"),
    ACCOUNT_DELETE("平台账户删除"),
    ACCOUNT_UPDATE("平台账户修改"),
    ACCOUNT_LIST("平台账户查询"),

    // AK/SK
    KEYS_CREATE("账户AK/SK新增"),
    KEYS_DELETE("账户AK/SK删除"),
    KEYS_STATUS_UPDATE("账户AK/SK状态修改"),

    // OPDATA_SERVICE(顺序不能乱)
    OPDATA_SERVICE_DOWN("opdata服务下线"),
    OPDATA_SERVICE_UP("opdata服务上线"),
    OPDATA_SERVICE_DETAIL("opdata服务详情"),
    OPDATA_SERVICE_LIST("opdata服务查询"),

    // EXT-PERMISSION
    EXT_PERMISSION_LIST("外部权限查询"),
    EXT_PERMISSION_CREAT("外部权限创建"),
    EXT_PERMISSION_UPDATE("外部权限修改"),
    EXT_PERMISSION_DELETE("外部权限删除"),

    // JOB
    JOB_LIST("任务管理查询"),
    JOB_STATUS_UPDATE("任务状态更改"),

    // SHED_LOCK
    SHED_LOCK_LIST("数据库锁查询"),

    // OPDATA_PLUGIN
    OPDATA_PLUGIN_LIST("opdata插件查询"),
    OPDATA_PLUGIN_CREATE("opdata插件新建"),
    OPDATA_PLUGIN_DELETE("opdata插件删除"),
    OPDATA_PLUGIN_UPDATE("opdata插件更新"),
    OPDATA_PLUGIN_LOAD("opdata插件加载"),
    OPDATA_PLUGIN_UNLOAD("opdata插件卸载"),

    // OPDATA_FILESYSTEM
    OPDATA_FILE_SYSTEM_METRICS("挂载分布式文件系统节点统计功能"),
    OPDATA_FILE_SYSTEM_METRICS_DETAIL("统计某个挂载路径下子目录文件大小"),

    // OPDATA_EMPLTY_FOLDER
    EMPTY_FOLDER_LIST("空目录查询"),
    EMPTY_FOLDER_DELETE("清空空目录"),

    // FIRST_FOLDER
    FIRST_LEVEL_FOLDER_STATISTICS("一级目录统计"),

    // OPDATA_NOTIFY
    OPDATA_NOTIFY_LIST("op系统通知凭证查询"),
    OPDATA_NOTIFY_CREATE("op系统通知凭证新增"),
    OPDATA_NOTIFY_UPDATE("op系统通知凭证修改"),
    OPDATA_NOTIFY_DELETE("op系统通知凭证删除"),

    // OPDATA_SCAN
    SCANNER_CREATE("制品扫描器新增"),
    SCANNER_UPDATE("制品扫描器修改"),
    SCANNER_DELETE("制品扫描器删除"),
    SCANNER_LIST("制品扫描器查询"),

    // OPDATA_CONFIG
    PROJECT_SCAN_CONFIG_CREATE("制品项目配置新增"),
    PROJECT_SCAN_CONFIG_UPDATE("制品项目配置修改"),
    PROJECT_SCAN_CONFIG_DELETE("制品项目配置删除"),
    PROJECT_SCAN_CONFIG_LIST("制品项目配置查询")
}
