/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.node

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import org.springframework.data.mongodb.core.query.Criteria
import java.time.LocalDateTime

/**
 * 节点重命名接口
 */
interface NodeStatsOperation {

    /**
     * 计算文件或者文件夹大小
     */
    fun computeSize(artifact: ArtifactInfo, estimated: Boolean = false): NodeSizeInfo

    /**
     * 清理前的计算大小
     */
    fun computeSizeBeforeClean(artifact: ArtifactInfo, before: LocalDateTime):NodeSizeInfo

    /**
     * 查询文件节点数量
     */
    fun countFileNode(artifact: ArtifactInfo): Long

    /**
     * 聚合查询节点大小
     */
    fun aggregateComputeSize(criteria: Criteria): Long

}
