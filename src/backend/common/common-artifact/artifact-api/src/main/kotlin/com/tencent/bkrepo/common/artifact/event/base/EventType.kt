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
enum class EventType(val msgKey: String) {
    // PROJECT
    PROJECT_CREATED("artifact.event.project-created"),

    // REPOSITORY
    REPO_CREATED("artifact.event.repo-created"),
    REPO_UPDATED("artifact.event.repo-updated"),
    REPO_DELETED("artifact.event.repo-deleted"),
    // 主要针对代理仓库需要定时从远程将相关信息同步到本地
    REPO_REFRESHED("artifact.event.repo-refreshed"),
    REPO_VOLUME_SYNC("artifact.event.repo-volume-sync"),

    // NODE
    NODE_CREATED("artifact.event.node-created"),
    NODE_RENAMED("artifact.event.node-renamed"),
    NODE_MOVED("artifact.event.node-moved"),
    NODE_COPIED("artifact.event.node-copied"),
    NODE_DELETED("artifact.event.node-deleted"),
    NODE_DOWNLOADED("artifact.event.node-downloaded"),
    NODE_CLEAN("artifact.event.node-clean"),

    // METADATA
    METADATA_DELETED("artifact.event.metadata-deleted"),
    METADATA_SAVED("artifact.event.metadata-saved"),

    // VERSION
    VERSION_CREATED("artifact.event.version-created"),
    VERSION_DELETED("artifact.event.version-deleted"),
    VERSION_DOWNLOAD("artifact.event.version-download"),
    VERSION_UPDATED("artifact.event.version-updated"),
    VERSION_STAGED("artifact.event.version-staged"),


    // ADMIN
    ADMIN_ADD("artifact.event.admin-add"),
    ADMIN_DELETE("artifact.event.admin-delete"),

    // WebHook
    WEBHOOK_TEST("artifact.event.webhook-test"),
    WEBHOOK_LIST("artifact.event.webhook-list"),
    WEBHOOK_CREATE("artifact.event.webhook-create"),
    WEBHOOK_UPDATE("artifact.event.webhook-update"),
    WEBHOOK_DELETE("artifact.event.webhook-delete"),
    WEBHOOK_LOG_LIST("artifact.event.webhook-log-list"),

    // ACCOUNT
    ACCOUNT_CREATE("artifact.event.account-create"),
    ACCOUNT_DELETE("artifact.event.account-delete"),
    ACCOUNT_UPDATE("artifact.event.account-update"),
    ACCOUNT_LIST("artifact.event.account-list"),

    // AK/SK
    KEYS_CREATE("artifact.event.keys-create"),
    KEYS_DELETE("artifact.event.keys-delete"),
    KEYS_STATUS_UPDATE("artifact.event.keys-status-update"),

    // SERVICE
    SERVICE_INSTANCE_DOWN("artifact.event.service-instance-down"),
    SERVICE_INSTANCE_UP("artifact.event.service-instance-up"),
    SERVICE_INSTANCE("artifact.event.service-instance"),
    SERVICE_INSTANCE_LIST("artifact.event.service-instance-list"),
    SERVICE_LIST("artifact.event.service-list"),

    // EXT-PERMISSION
    EXT_PERMISSION_LIST("artifact.event.ext-permission-list"),
    EXT_PERMISSION_CREAT("artifact.event.ext-permission-creat"),
    EXT_PERMISSION_UPDATE("artifact.event.ext-permission-update"),
    EXT_PERMISSION_DELETE("artifact.event.ext-permission-delete"),

    // JOB
    JOB_LIST("artifact.event.job-list"),
    JOB_STATUS_UPDATE("artifact.event.job-status-update"),

    // SHED_LOCK
    SHED_LOCK_LIST("artifact.event.shed-lock-list"),

    // PLUGIN
    PLUGIN_LIST("artifact.event.plugin-list"),
    PLUGIN_CREATE("artifact.event.plugin-create"),
    PLUGIN_DELETE("artifact.event.plugin-delete"),
    PLUGIN_UPDATE("artifact.event.plugin-update"),
    PLUGIN_LOAD("artifact.event.plugin-load"),
    PLUGIN_UNLOAD("artifact.event.plugin-unload"),

    // FILESYSTEM
    FILE_SYSTEM_METRICS("artifact.event.file-system-metrics"),
    FILE_SYSTEM_METRICS_DETAIL("artifact.event.file-system-metrics-detail"),
    STORAGE_CREDENTIALS_LIST("artifact.event.storage-credentials-list"),
    STORAGE_CREDENTIALS_CREATE("artifact.event.storage-credentials-create"),
    STORAGE_CREDENTIALS_DELETE("artifact.event.storage-credentials-delete"),
    STORAGE_CREDENTIALS_UPDATE("artifact.event.storage-credentials-update"),

    // EMPLTY_FOLDER
    EMPTY_FOLDER_LIST("artifact.event.empty-folder-list"),
    EMPTY_FOLDER_DELETE("artifact.event.empty-folder-delete"),

    // FIRST_FOLDER
    FIRST_LEVEL_FOLDER_STATISTICS("artifact.event.first-level-folder-statistics"),

    // NOTIFY
    NOTIFY_LIST("artifact.event.notify-list"),
    NOTIFY_CREATE("artifact.event.notify-create"),
    NOTIFY_UPDATE("artifact.event.notify-update"),
    NOTIFY_DELETE("artifact.event.notify-delete"),

    // SCAN
    SCANNER_CREATE("artifact.event.scanner-create"),
    SCANNER_UPDATE("artifact.event.scanner-update"),
    SCANNER_DELETE("artifact.event.scanner-delete"),
    SCANNER_LIST("artifact.event.scanner-list"),

    // SCAN EXECUTION CLUSTER
    EXECUTION_CLUSTER_CREATE("artifact.event.execution-cluster-create"),
    EXECUTION_CLUSTER_UPDATE("artifact.event.execution-cluster-update"),
    EXECUTION_CLUSTER_DELETE("artifact.event.execution-cluster-delete"),
    EXECUTION_CLUSTER_LIST("artifact.event.execution-cluster-list"),

    // SCAN CONFIG
    PROJECT_SCAN_CONFIG_CREATE("artifact.event.project-scan-config-create"),
    PROJECT_SCAN_CONFIG_UPDATE("artifact.event.project-scan-config-update"),
    PROJECT_SCAN_CONFIG_DELETE("artifact.event.project-scan-config-delete"),
    PROJECT_SCAN_CONFIG_LIST("artifact.event.project-scan-config-list"),

    // CONFIG
    CONFIG_UPDATE("artifact.event.config-update"),

    // 第三方同步
    REPLICATION_THIRD_PARTY("artifact.event.replication-third-party");
}
