/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.common.metadata.dao.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCompressedRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeUnCompressedRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeCompressOperation
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

class NodeCompressSupport(
    nodeBaseService: NodeBaseService,
) : NodeCompressOperation {
    val nodeDao: NodeDao = nodeBaseService.nodeDao
    override fun compressedNode(nodeCompressedRequest: NodeCompressedRequest) {
        with(nodeCompressedRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().set(TNode::compressed.name, true)
            nodeDao.updateFirst(query, update)
            logger.info("Success to compress node $projectId/$repoName/$fullPath")
        }
    }

    override fun uncompressedNode(nodeUnCompressedRequest: NodeUnCompressedRequest) {
        with(nodeUnCompressedRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().unset(TNode::compressed.name)
                .set(TNode::lastAccessDate.name, LocalDateTime.now())
            nodeDao.updateFirst(query, update)
            logger.info("Success to uncompress node $projectId/$repoName/$fullPath")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeCompressSupport::class.java)
    }
}
