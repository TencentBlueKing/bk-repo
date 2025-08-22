/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.mockRepositoryCommonUtils
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.router.api.RouterControllerClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime


@DisplayName("Node遍历工具测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeCommonUtilsTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
) : JobBaseTest() {
    @MockitoBean
    private lateinit var routerControllerClient: RouterControllerClient

    @MockitoBean
    lateinit var servicePermissionClient: ServicePermissionClient

    @MockitoBean
    lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient

    @MockitoBean
    lateinit var messageSupplier: MessageSupplier

    @MockitoBean
    lateinit var archiveClient: ArchiveClient

    @MockitoBean
    lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockitoBean
    lateinit var separationTaskService: SeparationTaskService

    @MockitoBean
    lateinit var operateLogService: OperateLogService

    private val nodeCollectionName = "node_${HashShardingUtils.shardingSequenceFor(UT_PROJECT_ID, SHARDING_COUNT)}"

    @BeforeEach
    fun beforeEach() {
        mongoTemplate.remove(Query(), nodeCollectionName)
        whenever(migrateRepoStorageService.migrating(anyString(), anyString())).thenReturn(true)
        NodeCommonUtils.mongoTemplate = mongoTemplate
        NodeCommonUtils.migrateRepoStorageService = migrateRepoStorageService
        NodeCommonUtils.separationTaskService = separationTaskService
        mockRepositoryCommonUtils()
    }

    @Test
    fun `throw IllegalStateException when repo was migrating`() {
        whenever(separationTaskService.findDistinctSeparationDate()).thenReturn(emptySet())
        mockNode()
        assertThrows<IllegalStateException> { NodeCommonUtils.exist(Query(), null) }
    }

    @Test
    fun `findNodes should check migrating status when checkMigrating is true`() {
        mockNode()
        assertEquals(1, NodeCommonUtils.findNodes(Query(), UT_STORAGE_CREDENTIALS_KEY, false).size)
        assertEquals(0, NodeCommonUtils.findNodes(Query(), UT_STORAGE_CREDENTIALS_KEY, true).size)
    }

    private fun mockNode() {
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
        mongoTemplate.insert(node, nodeCollectionName)
    }
}
