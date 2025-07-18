/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.s3.pojo

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@JacksonXmlRootElement(localName = "ListBucketResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
data class ListBucketResult(
    @JacksonXmlProperty(localName = "IsTruncated")
    val isTruncated: Boolean,
    @JacksonXmlProperty(localName = "Marker")
    val marker: String? = null,
    @JacksonXmlProperty(localName = "NextMarker")
    val nextMarker: String? = null,
    @JacksonXmlProperty(localName = "Contents")
    @JacksonXmlElementWrapper(useWrapping = false)
    val contents: List<Content>,
    @JacksonXmlProperty(localName = "Name")
    val name: String,
    @JacksonXmlProperty(localName = "Prefix")
    val prefix: String,
    @JacksonXmlProperty(localName = "Delimiter")
    val delimiter: String? = null,
    @JacksonXmlProperty(localName = "CommonPrefixes")
    @JacksonXmlElementWrapper(useWrapping = false)
    val commonPrefixes: List<CommonPrefix>?,
    @JacksonXmlProperty(localName = "MaxKeys")
    val maxKeys: Int,
) {
    constructor(
        repoName: String,
        data: Page<Map<String, Any?>>,
        maxKeys: Int,
        prefix: String,
        folders: List<String>?,
        delimiter: String,
        currentNode: Map<String, Any?>? = null
    ) : this(
        name = repoName,
        prefix = prefix,
        delimiter = delimiter.ifEmpty { null },
        commonPrefixes = folders?.map { CommonPrefix(it) }.orEmpty(),
        marker = data.pageNumber.toString(),
        nextMarker = if (data.pageNumber.toLong() < data.totalPages) (data.pageNumber + 1).toString() else null,
        maxKeys = maxKeys,
        isTruncated = data.pageNumber.toLong() < data.totalPages,
        contents = convertToContents(data.records, currentNode)
    )

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        private val emptyMD5 = StringPool.EMPTY.md5()
        private fun convertToContents(
            nodeDetailList: List<Map<String, Any?>>,
            currentNode: Map<String, Any?>?
        ): List<Content> {
            val list = if (currentNode == null) {
                nodeDetailList
            } else {
                nodeDetailList.plus(currentNode)
            }
            return list.map {
                val owner = it[NodeDetail::createdBy.name].toString()
                val folder = it[NodeDetail::folder.name].toString().toBoolean()
                val key = if (folder) {
                    it[NodeDetail::fullPath.name].toString()
                        .removePrefix(StringPool.SLASH).ensureSuffix(StringPool.SLASH)
                } else {
                    it[NodeDetail::fullPath.name].toString().removePrefix(StringPool.SLASH)
                }
                Content(
                    key = key,
                    lastModified = LocalDateTime.parse(
                        it[NodeDetail::lastModifiedDate.name].toString()
                    ).format(formatter),
                    eTag = if (folder) {
                        "\"$emptyMD5\""
                    } else {
                        "\"${it[NodeDetail::md5.name].toString()}\""
                    },
                    size = if (folder) 0 else it[NodeDetail::size.name].toString().toLong(),
                    storageClass = "STANDARD",
                    owner = Owner(owner, owner)
                )
            }
        }
    }
}

data class Content(
    @JacksonXmlProperty(localName = "ETag")
    val eTag: String,
    @JacksonXmlProperty(localName = "Key")
    val key: String,
    @JacksonXmlProperty(localName = "LastModified")
    val lastModified: String,
    @JacksonXmlProperty(localName = "Owner")
    val owner: Owner,
    @JacksonXmlProperty(localName = "Size")
    val size: Long,
    @JacksonXmlProperty(localName = "StorageClass")
    val storageClass: String
)

data class Owner(
    @JacksonXmlProperty(localName = "DisplayName")
    val displayName: String,
    @JacksonXmlProperty(localName = "ID")
    val id: String
)

data class CommonPrefix(
    @JacksonXmlProperty(localName = "Prefix")
    val prefix: String
)
