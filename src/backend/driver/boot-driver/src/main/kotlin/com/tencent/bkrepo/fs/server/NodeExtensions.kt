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

package com.tencent.bkrepo.fs.server

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.fs.server.model.Node
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

fun NodeInfo.toNode(): Node {
    return Node(
        createdBy = this.createdBy,
        createdDate = this.createdDate,
        lastModifiedBy = this.lastModifiedBy,
        lastModifiedDate = this.lastModifiedDate,
        projectId = this.projectId,
        repoName = this.repoName,
        folder = this.folder,
        path = this.path,
        name = this.name,
        fullPath = this.fullPath,
        size = this.size,
        sha256 = this.sha256,
        md5 = this.md5,
        metadata = this.metadata,
        lastAccessDate = this.lastAccessDate
    )
}

fun Map<String, Any?>.toNode(): Node? {
    return try {
        Node(
            createdBy = this[Node::createdBy.name] as String,
            createdDate = this[Node::createdDate.name] as String,
            lastModifiedBy = this[Node::lastModifiedBy.name] as String,
            lastModifiedDate = this[Node::lastModifiedDate.name] as String,
            projectId = this[Node::projectId.name] as String,
            repoName = this[Node::repoName.name] as String,
            folder = this[Node::folder.name] as Boolean,
            path = this[Node::path.name] as String,
            name = this[Node::name.name] as String,
            fullPath = this[Node::fullPath.name] as String,
            size = this[Node::size.name].toString().toLong(),
            sha256 = this[Node::sha256.name] as String?,
            md5 = this[Node::md5.name] as String?,
            metadata = this[Node::metadata.name] as Map<String, Any>?,
            lastAccessDate = this[Node::lastAccessDate.name]?.toString()?.let { convertDateTime(it) },
            category = (this[Node::category.name] as String?) ?: RepositoryCategory.LOCAL.name
        )
    } catch (e: Exception) {
        logger.error("convert to node failed", e)
        null
    }
}

fun convertDateTime(value: String): String {
    return value.toLongOrNull()?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).format(ISO_DATE_TIME)
    } ?: value
}

private val logger = LoggerFactory.getLogger("NodeExtensions")
