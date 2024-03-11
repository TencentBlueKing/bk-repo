/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.metadata.constant.SCAN_STATUS
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.dao.repository.MetadataLabelRepository
import com.tencent.bkrepo.repository.pojo.metadata.label.MetadataLabelRequest
import com.tencent.bkrepo.repository.service.metadata.MetadataLabelService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import

@DisplayName("元数据标签服务测试")
@DataMongoTest
@Import(
    MetadataLabelRepository::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataLabelServiceTest @Autowired constructor(
    private val metadataLabelService: MetadataLabelService
) : ServiceBaseTest() {

    @BeforeAll
    fun init() {
        initMock()
    }

    @AfterEach
    fun clear() {
        metadataLabelService.listAll(UT_PROJECT_ID).forEach {
            metadataLabelService.delete(UT_PROJECT_ID, it.labelKey)
        }
    }

    @Test
    fun createTest() {
        val request = buildCreateRequest()
        metadataLabelService.create(request)
        assertThrows<ErrorCodeException> { metadataLabelService.create(request) }
        val invalidColorRequest = MetadataLabelRequest(
            projectId = UT_PROJECT_ID,
            labelKey = SCAN_STATUS,
            labelColorMap = mapOf(
                "FAILED" to "#FF000",
                "SUCCESS" to "#00EE00"
            ),
            display = true
        )
        assertThrows<ErrorCodeException> { metadataLabelService.create(invalidColorRequest) }
    }

    @Test
    fun updateTest() {
        val request = buildCreateRequest()
        metadataLabelService.create(request)
        val updateRequest = MetadataLabelRequest(
            projectId = UT_PROJECT_ID,
            labelKey = SCAN_STATUS,
            labelColorMap = mapOf(
                "FAILED" to "#000000",
                "SUCCESS" to "#000000"
            ),
            display = true
        )
        metadataLabelService.update(updateRequest)
        val metadataLabel = metadataLabelService.detail(updateRequest.projectId, updateRequest.labelKey)
        assert(metadataLabel.labelColorMap["FAILED"] == "#000000")
        assert(metadataLabel.labelColorMap["SUCCESS"] == "#000000")
    }

    @Test
    fun deleteTest() {
        val request = buildCreateRequest()
        metadataLabelService.create(request)
        metadataLabelService.delete(request.projectId, request.labelKey)
        assertThrows<NotFoundException> { metadataLabelService.delete(request.projectId, request.labelKey) }
    }

    private fun buildCreateRequest() = MetadataLabelRequest(
        projectId = UT_PROJECT_ID,
        labelKey = SCAN_STATUS,
        labelColorMap = mapOf(
            "FAILED" to "#FF0000",
            "SUCCESS" to "#00EE00"
        ),
        display = true
    )
}
