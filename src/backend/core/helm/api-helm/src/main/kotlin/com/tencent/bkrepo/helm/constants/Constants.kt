/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.helm.constants

const val REPO_TYPE = "HELM"

const val INDEX_YAML = "index.yaml"
const val INDEX_CACHE_YAML = "index-cache.yaml"
const val SIZE = "size"
const val FORCE = "force"
const val OVERWRITE = "isOverwrite"

const val FULL_PATH = "_full_path"
const val META_DETAIL = "meta_detail"
const val FILE_TYPE = "file_type"
const val PROXY_URL = "proxyUrl"

const val V1 = "v1"
const val CHART_YAML = "Chart.yaml"
const val CHART = "chart"
const val PROV = "prov"
const val NAME = "name"
const val PACKAGE_KEY = "packageKey"
const val VERSION = "version"
const val CHART_PACKAGE_FILE_EXTENSION = "tgz"
const val PROVENANCE_FILE_EXTENSION = "tgz.prov"
const val QUERY_INDEX_KEY_PREFIX = "helm:lock:indexFile:"
const val REFRESH_INDEX_KEY_PREFIX = "helm:lock:refreshIndex:"

const val SLEEP_MILLIS = 20L


const val HELM_EVENT_TASK_ACTIVE_COUNT = "helm.event.task.active.count"
const val HELM_EVENT_TASK_ACTIVE_COUNT_DESC = "helm事件处理实时执行数量"

const val HELM_EVENT_TASK_QUEUE_SIZE = "helm.event.task.queue.size"
const val HELM_EVENT_TASK_QUEUE_SIZE_DESC = "helm事件处理线程池等待队列大小"

const val HELM_EVENT_TASK_COMPLETED_COUNT = "helm.event.task.completed.count"
const val HELM_EVENT_TASK_COMPLETED_COUNT_DESC = "helm事件处理已完成的任务数量"

const val HELM_INDEX_REFRESH_TASK_ACTIVE_COUNT = "helm.index.refresh.task.active.count"
const val HELM_INDEX_REFRESH_TASK_ACTIVE_COUNT_DESC = "helm index刷新实时执行数量"

const val HELM_INDEX_REFRESH_TASK_QUEUE_SIZE = "helm.index.refresh.task.queue.size"
const val HELM_INDEX_REFRESH_TASK_QUEUE_SIZE_DESC = "helm index刷新线程池等待队列大小"

const val HELM_INDEX_REFRESH_TASK_COMPLETED_COUNT = "helm.index.refresh.task.completed.count"
const val HELM_INDEX_REFRESH_TASK_COMPLETED_COUNT_DESC = "helm index刷新已完成的任务数量"