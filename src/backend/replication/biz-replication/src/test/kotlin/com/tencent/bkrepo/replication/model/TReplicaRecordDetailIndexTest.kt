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

package com.tencent.bkrepo.replication.model

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes

/**
 * TReplicaRecordDetail 索引声明验证测试。
 *
 * 拓扑流量统计依赖 (localCluster, remoteCluster, startTime) 复合索引来加速聚合查询，
 * 该测试通过反射读取实体类上的 @CompoundIndexes，确保索引声明在后续重构中不被误删。
 */
internal class TReplicaRecordDetailIndexTest {

    @Test
    fun `TReplicaRecordDetail should declare compound index on (localCluster, remoteCluster, startTime)`() {
        val annotation = TReplicaRecordDetail::class.java.getAnnotation(CompoundIndexes::class.java)
        assertNotNull(annotation, "TReplicaRecordDetail must be annotated with @CompoundIndexes")

        val indexes: Array<CompoundIndex> = annotation!!.value
        val matched = indexes.any { idx ->
            val def = idx.def.replace(" ", "").replace("\"", "'")
            def.contains("'localCluster':1") &&
                def.contains("'remoteCluster':1") &&
                def.contains("'startTime':1")
        }
        assertTrue(
            matched,
            "expected a compound index covering (localCluster, remoteCluster, startTime), " +
                "but got: ${indexes.joinToString { it.def }}"
        )
    }
}
