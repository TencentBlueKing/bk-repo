/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.utils

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.router.api.RouterControllerClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime


@DisplayName("Node遍历工具测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeCommonUtilsTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
) : JobBaseTest() {
    @MockBean
    private lateinit var routerControllerClient: RouterControllerClient
    @MockBean
    lateinit var servicePermissionClient: ServicePermissionClient
    @MockBean
    lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient
    @MockBean
    lateinit var messageSupplier: MessageSupplier
    @MockBean
    lateinit var archiveClient: ArchiveClient
    @MockBean
    lateinit var migrateRepoStorageService: MigrateRepoStorageService
    @MockBean
    lateinit var separationTaskService: SeparationTaskService
    @MockBean
    lateinit var operateLogService: OperateLogService
    @BeforeAll
    fun beforeAll() {
        NodeCommonUtils.mongoTemplate = mongoTemplate
        NodeCommonUtils.migrateRepoStorageService = migrateRepoStorageService
        NodeCommonUtils.separationTaskService = separationTaskService
    }

    @Test
    fun `throw IllegalStateException when repo was migrating`() {
        whenever(migrateRepoStorageService.migrating(anyString(), anyString())).thenReturn(true)
        whenever(separationTaskService.findDistinctSeparationDate()).thenReturn(emptySet())
        val node = NodeCommonUtils.Node(
            "",
            UT_PROJECT_ID,
            UT_REPO_NAME,
            "/a/b/c.txt",
            UT_SHA256,
            100L,
            LocalDateTime.now(),
            null
        )
        val collectionName = "node_${HashShardingUtils.shardingSequenceFor(UT_PROJECT_ID, SHARDING_COUNT)}"
        mongoTemplate.insert(node, collectionName)
        assertThrows<IllegalStateException> { NodeCommonUtils.exist(Query(), null) }
    }
}
