/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@DisplayName("制品报告上报测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportExporterTest {

    private lateinit var reportExporter: ReportExporter

    private lateinit var messageSupplier: MessageSupplier

    @BeforeAll
    fun beforeAll() {
        val properties = ReportExportProperties()
        properties.enabled = true
        properties.topic = "test-topic"
        messageSupplier = mockk()
        reportExporter = ReportExporter(properties, messageSupplier)
    }

    @Test
    fun testExport() {
        // 准备数据
        val reportSlot = slot<Report>()
        every { messageSupplier.delegateToSupplier(capture(reportSlot), any(), any(), any(), any()) }.returns(Unit)
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
        with(reportSlot.captured) {
            // 验证基本信息
            assertEquals("subTaskId", taskId)
            assertEquals(NODE_SHA256, sha256)

            // 验证组件数量不超过分块大小
            assertTrue(components.size <= 1000)

            // 验证组件内容
            components.forEachIndexed { index, component ->
                assertEquals(securityResult.pkgName + index, component.name)
                assertEquals(1, component.vulnerabilities.size)
            }
        }

    }
}