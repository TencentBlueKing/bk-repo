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

import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_RECEIVE_RATE_RECORD
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_RECEIVE_RATE_RECORD_DESC
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_RESPONSE_RATE_RECORD
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_RESPONSE_RATE_RECORD_DESC
import com.tencent.bkrepo.common.artifact.metrics.ArtifactTransferRecord
import com.tencent.bkrepo.common.artifact.metrics.ArtifactTransferRecord.Companion.RECEIVE
import com.tencent.bkrepo.common.artifact.metrics.prometheus.PrometheusDrive
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Queue


class PrometheusMetricsExporter(
    private val drive: PrometheusDrive = PrometheusDrive()
) {

    fun export(queue: Queue<ArtifactTransferRecord>) {
        if (queue.isEmpty()) {
            return
        }
        val count: Int = queue.size
        for (i in 0 until count) {


            val item = queue.poll()

            val tags = Tags.of(
                Tag.of("storage", item.storage),
                Tag.of("elapsed", item.elapsed.toString()),
                Tag.of("bytes", item.bytes.toString()),
                Tag.of("average", item.average.toString()),
                Tag.of("sha256", item.sha256),
                Tag.of("clientIp", item.clientIp),
                Tag.of("project", item.project),
                Tag.of("repoName", item.repoName),
//                Tag.of("fullPath", item.fullPath),
            )
//            val timer = if (item.type == RECEIVE) {
//                Timer.builder(ARTIFACT_RECEIVE_RATE_RECORD)
//                    .description(ARTIFACT_RECEIVE_RATE_RECORD_DESC)
//                    .tags(tags)
//                    .register()
//            } else {
//                Timer.builder(ARTIFACT_RESPONSE_RATE_RECORD)
//                    .description(ARTIFACT_RESPONSE_RATE_RECORD_DESC)
//                    .tags(tags)
//                    .register()
//            }
//            timer.record(Duration.of(item.elapsed, ChronoUnit.NANOS))
        }
        drive.push()
    }


    companion object {
        private val logger = LoggerFactory.getLogger(PrometheusMetricsExporter::class.java)
    }
}
