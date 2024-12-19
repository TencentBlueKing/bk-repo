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

package com.tencent.bkrepo.common.artifact.metrics.export

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.metrics.ArtifactTransferRecord
import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.enums.TypeOfMetricsItem
import org.slf4j.LoggerFactory
import java.util.Queue

class ArtifactMetricsExporter(
    private val customMetricsExporter: CustomMetricsExporter? = null,
    private val allowUnknownProjectExport: Boolean,
) {

    fun export(queue: Queue<ArtifactTransferRecord>) {
        if (queue.isEmpty()) {
            return
        }
        val count: Int = queue.size
        for (i in 0 until count) {
            val item = queue.poll()
            if ((item.project == StringPool.UNKNOWN || item.fullPath == StringPool.UNKNOWN) &&
                !allowUnknownProjectExport
            ) {
                continue
            }
            val labels = convertRecordToMap(item)
            val metrics = TypeOfMetricsItem.ARTIFACT_TRANSFER_RATE
            val metricItem = MetricsItem(
                metrics.displayName, metrics.help,
                metrics.dataModel, metrics.keepHistory, item.average.toDouble(), labels
            )
            customMetricsExporter?.reportMetrics(metricItem)
        }
    }

    private fun convertRecordToMap(record: ArtifactTransferRecord): MutableMap<String, String> {
        val labels = mutableMapOf<String, String>()
        labels[ArtifactTransferRecord::fullPath.name] = record.fullPath
        labels[ArtifactTransferRecord::storage.name] = record.storage
        labels[ArtifactTransferRecord::bytes.name] = record.bytes.toString()
        labels[ArtifactTransferRecord::sha256.name] = record.sha256
        labels[ArtifactTransferRecord::clientIp.name] = record.clientIp
        labels[ArtifactTransferRecord::repoName.name] = record.repoName
        labels[PROJECT_ID] = record.project
        labels[ArtifactTransferRecord::elapsed.name] = record.elapsed.toString()
        labels[ArtifactTransferRecord::type.name] = record.type
        labels[ArtifactTransferRecord::agent.name] = record.agent
        labels[ArtifactTransferRecord::userId.name] = record.userId
        return labels
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactMetricsExporter::class.java)
    }
}
