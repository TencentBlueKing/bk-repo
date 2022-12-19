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
    ACCOUNT_CREATE("平台账户创建"),
    ACCOUNT_DELETE("平台账户删除"),
    ACCOUNT_UPDATE("平台账户修改"),
    ACCOUNT_LIST("平台账户查询"),

    // AK/SK
    KEYS_CREATE("账户AK/SK新增"),
    KEYS_DELETE("账户AK/SK删除"),
    KEYS_STATUS_UPDATE("账户AK/SK状态修改"),

    // SERVICE
    SERVICE_INSTANCE_DOWN("服务实例下线"),
    SERVICE_INSTANCE_UP("服务实例上线"),
    SERVICE_INSTANCE("服务实例详情查询"),
    SERVICE_INSTANCE_LIST("服务实例列表查询"),
    SERVICE_LIST("服务列表查询"),

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

    // PLUGIN
    PLUGIN_LIST("插件查询"),
    PLUGIN_CREATE("插件新建"),
    PLUGIN_DELETE("插件删除"),
    PLUGIN_UPDATE("插件更新"),
    PLUGIN_LOAD("插件加载"),
    PLUGIN_UNLOAD("插件卸载"),

    // FILESYSTEM
    FILE_SYSTEM_METRICS("挂载分布式文件系统节点统计功能"),
    FILE_SYSTEM_METRICS_DETAIL("统计某个挂载路径下子目录文件大小"),
    STORAGE_CREDENTIALS_LIST("存储管理凭证查询"),
    STORAGE_CREDENTIALS_CREATE("存储管理凭证创建"),
    STORAGE_CREDENTIALS_DELETE("存储管理凭证删除"),
    STORAGE_CREDENTIALS_UPDATE("存储管理凭证更新"),

    // EMPLTY_FOLDER
    EMPTY_FOLDER_LIST("空目录查询"),
    EMPTY_FOLDER_DELETE("清空空目录"),

    // FIRST_FOLDER
    FIRST_LEVEL_FOLDER_STATISTICS("一级目录统计"),

    // NOTIFY
    NOTIFY_LIST("通知凭证查询"),
    NOTIFY_CREATE("通知凭证新增"),
    NOTIFY_UPDATE("通知凭证修改"),
    NOTIFY_DELETE("通知凭证删除"),

    // SCAN
    SCANNER_CREATE("制品扫描器新增"),
    SCANNER_UPDATE("制品扫描器修改"),
    SCANNER_DELETE("制品扫描器删除"),
    SCANNER_LIST("制品扫描器查询"),

    // SCAN CONFIG
    PROJECT_SCAN_CONFIG_CREATE("制品项目配置新增"),
    PROJECT_SCAN_CONFIG_UPDATE("制品项目配置修改"),
    PROJECT_SCAN_CONFIG_DELETE("制品项目配置删除"),
    PROJECT_SCAN_CONFIG_LIST("制品项目配置查询"),

    // CONFIG
    CONFIG_UPDATE("配置更新"),

    // 第三方同步
    REPLICATION_THIRD_PARTY("外部制品同步");

    companion object {
        /**
         * 获取事件名称
         *
         * @param type 事件类型
         * @return [type]对应的名称，没有对应名称时返回[type]
         */
        fun nick(type: String): String {
            return try {
                EventType.valueOf(type).nick
            } catch (_: IllegalArgumentException) {
                type
            }
        }
    }
}
