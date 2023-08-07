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

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.analyst.NODE_FULL_PATH
import com.tencent.bkrepo.analyst.NODE_NAME
import com.tencent.bkrepo.analyst.NODE_SHA256
import com.tencent.bkrepo.analyst.NODE_SIZE
import com.tencent.bkrepo.analyst.PROJECT_ID
import com.tencent.bkrepo.analyst.REPO
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RequestTest {
    @Test
    fun testRequestNodes() {
        val mockNodeClient = mockNodeClient()
        val nodes = Request.requestNodes(mockNodeClient, Rule.NestedRule(mutableListOf()), 0, 1)
        Assertions.assertEquals(nodes.size, 1)
        Assertions.assertEquals(nodes.first().sha256, NODE_SHA256)
    }

    private fun mockNodeClient(): NodeClient {
        val node = mapOf(
            NodeDetail::sha256.name to NODE_SHA256,
            NodeDetail::size.name to NODE_SIZE,
            NodeDetail::fullPath.name to NODE_FULL_PATH,
            NodeDetail::projectId.name to PROJECT_ID,
            NodeDetail::repoName.name to REPO,
            NodeDetail::name.name to NODE_NAME
        )
        val response = Response(CommonMessageCode.SUCCESS.getCode(), null, Pages.buildPage(listOf(node), 0, 1), null)

        return mock {
            on { queryWithoutCount(any()) }.doReturn(response)
        }
    }
}
