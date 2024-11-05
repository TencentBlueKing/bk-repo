/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.model

import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.DEFAULT_PROJECT_SCAN_PRIORITY
import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.DEFAULT_SCAN_TASK_COUNT_LIMIT
import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.DEFAULT_SUB_SCAN_TASK_COUNT_LIMIT
import com.tencent.bkrepo.analyst.pojo.AutoScanConfiguration
import com.tencent.bkrepo.analyst.pojo.DispatcherConfiguration
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 项目扫描配置
 */
@Document("project_scan_configuration")
@CompoundIndexes(
    CompoundIndex(name = "projectId_idx", def = "{'projectId': 1}", background = true, unique = true)
)
data class TProjectScanConfiguration(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    val projectId: String,
    /**
     * 项目优先级，值越小优先级越低
     */
    val priority: Int = DEFAULT_PROJECT_SCAN_PRIORITY,
    /**
     * 项目限制的扫描任务数量
     */
    val scanTaskCountLimit: Int = DEFAULT_SCAN_TASK_COUNT_LIMIT,
    /**
     * 项目扫描子任务数量限制
     */
    val subScanTaskCountLimit: Int = DEFAULT_SUB_SCAN_TASK_COUNT_LIMIT,
    /**
     * 自动扫描配置,key为scannerName,value为扫描器对应的自动扫描配置
     */
    val autoScanConfiguration: Map<String, AutoScanConfiguration> = emptyMap(),
    /**
     * 用于分发子任务的分发器
     */
    val dispatcherConfiguration: List<DispatcherConfiguration> = emptyList()
) {
    companion object {
        /**
         * projectId为空时表示全局配置
         */
        const val GLOBAL_PROJECT_ID = ""
    }
}
