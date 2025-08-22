/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.component

import com.tencent.bkrepo.analyst.NODE_SHA256
import com.tencent.bkrepo.analyst.configuration.ReportExportProperties
import com.tencent.bkrepo.analyst.pojo.report.Report
import com.tencent.bkrepo.analyst.utils.buildScanExecutorResult
import com.tencent.bkrepo.analyst.utils.buildSubScanTask
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@DisplayName("制品报告上报测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportExporterTest {

    private lateinit var properties: ReportExportProperties

    @BeforeAll
    fun beforeAll() {
        properties = ReportExportProperties()
        properties.enabled = true
        properties.topic = "test-topic"
    }

    @Test
    fun testExport() {
        // 准备数据
        val reportSlots = mutableListOf<Report>()
        val messageSupplier = mockk<MessageSupplier>()
        val reportExporter = ReportExporter(properties, messageSupplier)
        every { messageSupplier.delegateToSupplier(capture(reportSlots), any(), any(), any(), any()) }.returns(Unit)
        val result = buildScanExecutorResult()
        val securityResult = result.output!!.result!!.securityResults!![0]
        val newSecurityResults = (0..2000).map { securityResult.copy(pkgName = securityResult.pkgName + "$it") }
        val newResult = result.copy(
            output = result.output!!.copy(
                result = result.output!!.result!!.copy(
                    securityResults = newSecurityResults
                )
            )
        )

        // 执行测试
        reportExporter.export(buildSubScanTask("taskId", NODE_SHA256), newResult)


        // 验证
        verify(exactly = 5) {
            // license与sensitive各上报1次，security上报3次
            messageSupplier.delegateToSupplier<Report>(any(), any(), any(), any(), any())
        }
        assertEquals(5, reportSlots.size)
        // security
        assertEquals(1000, reportSlots[0].components.size)
        assertEquals(1000, reportSlots[1].components.size)
        assertEquals(1, reportSlots[2].components.size)
        assertEquals(0, reportSlots[3].components.size)
        // license
        assertEquals(1, reportSlots[3].componentLicenses.size)
        // sensitive
        assertEquals(2, reportSlots[4].sensitiveContents.size)
    }

    @Test
    fun testExceedMaxReportSize() {
        // 准备数据
        val messageSupplier = mockk<MessageSupplier>()
        every { messageSupplier.delegateToSupplier<Report>(any(), any(), any(), any(), any()) }.returns(Unit)
        val reportExporter = ReportExporter(properties.copy(maxReportSize = 1), messageSupplier)
        reportExporter.export(buildSubScanTask("taskId", NODE_SHA256), buildScanExecutorResult())
        // 报告数量超过限制，不上报
        verify(exactly = 0) {
            messageSupplier.delegateToSupplier<Report>(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun testExportFailed() {
        // 准备数据
        val messageSupplier = mockk<MessageSupplier>()
        val reportExporter = ReportExporter(properties, messageSupplier)
        every {
            messageSupplier.delegateToSupplier<Report>(any(), any(), any(), any(), any())
        }.throws(RuntimeException())
        val result = buildScanExecutorResult()
        reportExporter.export(buildSubScanTask("taskId", NODE_SHA256), result)

        // 验证
        // 应该尝试发送3次消息（1次security结果，1次license结果，1次sensitive结果）
        verify(exactly = 3) {
            messageSupplier.delegateToSupplier<Report>(any(), any(), any(), any(), any())
        }
    }
}
