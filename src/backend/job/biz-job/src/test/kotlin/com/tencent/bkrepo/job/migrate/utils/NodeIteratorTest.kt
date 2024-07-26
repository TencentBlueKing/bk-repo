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

package com.tencent.bkrepo.job.migrate.utils

import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.UT_MD5
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.buildTask
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.ensureNodeIndex
import com.tencent.bkrepo.job.model.TNode
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@DisplayName("Node遍历工具测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class NodeIteratorTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
) {

    private val startDate = LocalDateTime.parse("2024-04-15T14:27:08.493", DateTimeFormatter.ISO_DATE_TIME)
    private val totalCount = 101L
    private val beforeStartDateCount = 75L
    private val afterStartDateCount = totalCount - beforeStartDateCount
    private val migratedCount = 31L
    private val collectionName = "node_1"
    private lateinit var nodes: List<TNode>

    @BeforeAll
    fun beforeAll() {
        mongoTemplate.ensureNodeIndex(collectionName)
        nodes = createNodes(startDate)
    }

    @Test
    @DisplayName("测试遍历待迁移制品")
    fun testMigratingIterate() {
        val task = buildTask(startDate)
        testIterate(task, beforeStartDateCount)
        // continue migrate
        val lastMigratedNodeId = nodes[(migratedCount - 1).toInt()].id!!
        val continueTask = task.copy(lastMigratedNodeId = lastMigratedNodeId, migratedCount = migratedCount)
        testIterate(continueTask, beforeStartDateCount, continueTask.migratedCount)
    }

    @Test
    fun testMigrateEmpty() {
        mongoTemplate.remove(Query(), collectionName)
        val task = buildTask(startDate)
        testIterate(task, 0L)
        createNodes(startDate)
    }

    @Test
    @DisplayName("测试遍历迁移过程中新增的制品")
    fun testCorrectingIterate() {
        val task = buildTask(startDate).copy(state = MigrateRepoStorageTaskState.CORRECTING.name)
        testIterate(task, afterStartDateCount)
        // continue correct，correct不支持从上次断点继续，会从头全量遍历
        val continueTask = task.copy(migratedCount = migratedCount)
        testIterate(continueTask, afterStartDateCount)
    }

    private fun testIterate(task: MigrateRepoStorageTask, totalCount: Long, iteratedCount: Long = 0L) {
        val iterator = NodeIterator(task, mongoTemplate, collectionName)
        assertEquals(totalCount, iterator.totalCount)
        var count = iteratedCount
        // 确认按id升序遍历
        var preNodeId = ObjectId(MIN_OBJECT_ID)
        while (iterator.hasNext()) {
            val nodeId = ObjectId(iterator.next().id)
            assertTrue(preNodeId < nodeId)
            preNodeId = nodeId
            count++
        }
        // 确认遍历的总数符合预期
        assertEquals(totalCount, count)
    }

    private fun createNodes(startDate: LocalDateTime): List<TNode> {
        val before = startDate.minus(Duration.ofDays(1L))
        val after = startDate.plus(Duration.ofDays(1L))
        val nodes = ArrayList<TNode>()
        val node = TNode(
            id = null,
            createdDate = before,
            folder = false,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = "/a/b/c.txt",
            size = 100L,
            sha256 = UT_SHA256,
            md5 = UT_MD5,
        )

        for (i in 0 until totalCount) {
            nodes.add(
                node.copy(
                    createdDate = if (i < beforeStartDateCount) before else after,
                    size = i * 10L,
                    fullPath = "/a/b/c$i.txt",
                    deleted = if (i == totalCount - 1) LocalDateTime.now() else null
                ),
            )
        }

        return mongoTemplate.insert(nodes, collectionName).toList()
    }
}
