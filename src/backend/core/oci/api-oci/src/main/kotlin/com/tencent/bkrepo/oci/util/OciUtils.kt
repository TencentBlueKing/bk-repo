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

package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.StreamUtils.readText
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.oci.constant.DOCKER_IMAGE_MANIFEST_MEDIA_TYPE_V1
import com.tencent.bkrepo.oci.constant.OciMessageCode
import com.tencent.bkrepo.oci.exception.OciBadRequestException
import com.tencent.bkrepo.oci.model.Descriptor
import com.tencent.bkrepo.oci.model.ManifestSchema1
import com.tencent.bkrepo.oci.model.ManifestSchema2
import com.tencent.bkrepo.oci.model.SchemaVersion
import org.apache.logging.log4j.util.Strings
import java.io.InputStream

/**
 * oci blob 工具类
 */
object OciUtils {

    fun checkVersion(inputStream: InputStream): SchemaVersion {
        return schemeVersion(inputStream.readText())
    }

    fun schemeVersion(content: String): SchemaVersion {
        try {
            return content.readJsonString()
        } catch (e: Exception) {
            throw OciBadRequestException(OciMessageCode.OCI_MANIFEST_INVALID, Strings.EMPTY)
        }
    }

    fun checkVersion(mediaType: String): Int {
        if (DOCKER_IMAGE_MANIFEST_MEDIA_TYPE_V1 == mediaType) return 1
        return 2
    }

    fun streamToManifestV1(inputStream: InputStream): ManifestSchema1 {
        return stringToManifestV1(inputStream.readText())
    }

    fun streamToManifestV2(inputStream: InputStream): ManifestSchema2 {
        return stringToManifestV2(inputStream.readText())
    }

    fun stringToManifestV1(content: String): ManifestSchema1 {
        try {
            return content.readJsonString()
        } catch (e: Exception) {
            throw OciBadRequestException(OciMessageCode.OCI_MANIFEST_INVALID, Strings.EMPTY)
        }
    }

    fun stringToManifestV2(content: String): ManifestSchema2 {
        try {
            return content.readJsonString()
        } catch (e: Exception) {
            throw OciBadRequestException(OciMessageCode.OCI_MANIFEST_INVALID, Strings.EMPTY)
        }
    }

    fun manifestIterator(manifest: ManifestSchema2): List<Descriptor> {
        val list = mutableListOf<Descriptor>()
        list.add(manifest.config)
        list.addAll(manifest.layers)
        return list
    }

    fun manifestIteratorDigest(manifest: ManifestSchema2): List<String> {
        val list = mutableListOf<String>()
        list.add(manifest.config.filename)
        manifest.layers.forEach {
            list.add(it.filename)
        }
        return list
    }

    fun manifestIteratorDigest(manifest: ManifestSchema1): List<String> {
        val list = mutableListOf<String>()
        manifest.fsLayers.forEach {
            list.add(it.blobSum)
        }
        return list
    }

    fun manifestIterator(manifest: ManifestSchema2, mediaType: String): Descriptor? {
        val list = mutableListOf<Descriptor>()
        list.add(manifest.config)
        list.addAll(manifest.layers)
        return list.find { it.mediaType == mediaType }
    }

    /**
     * 根据packageKey获取对应的package name
     */
    fun getPackageNameFormPackageKey(
        packageKey: String,
        defaultType: RepositoryType,
        extraTypes: List<RepositoryType>
    ): String {
        var packageName = StringPool.EMPTY
        if (packageKey.startsWith(defaultType.name.toLowerCase())) {
            return PackageKeys.resolveName(defaultType.name.toLowerCase(), packageKey)
        }
        extraTypes.forEach {
            if (packageKey.startsWith(it.name.toLowerCase())) {
                packageName = PackageKeys.resolveName(it.name.toLowerCase(), packageKey)
                return@forEach
            }
        }
        return packageName
    }

    /**
     * 根据n或者last进行过滤（注意n是否会超过tags总长）
     * 1 n和last 都不存在，则返回所有
     * 2 n存在， last不存在，则返回前n个
     * 3 last存在 n不存在， 则返回查到的last，如不存在，则返回空列表
     * 4 last存在，n存在，则返回last之后的n个
     */
    fun filterHandler(tags: MutableList<String>, n: Int?, last: String?): Pair<MutableList<String>, Int> {
        if (n != null) return handleNFilter(tags, n, last)
        if (last.isNullOrEmpty()) return Pair(tags, 0)
        val index = tags.indexOf(last)
        return if (index == -1) {
            Pair(mutableListOf(), 0)
        } else {
            Pair(mutableListOf(last), 0)
        }
    }

    /**
     * 处理n存在时的逻辑
     */
    private fun handleNFilter(tags: MutableList<String>, n: Int, last: String?): Pair<MutableList<String>, Int> {
        var left = 0
        var tagList = tags
        var size = n
        val length = tags.size
        if (last.isNullOrEmpty()) {
            // 需要判断n个是否超过tags总长
            if (size > length) {
                size = length
            }
            tagList = tagList.subList(0, size)
            left = length - size
            return Pair(tagList, left)
        }
        // 当last存在，n也存在 则获取last所在后n个tag
        val index = tagList.indexOf(last)
        return if (index == -1) {
            Pair(mutableListOf(), 0)
        } else {
            // 需要判断last后n个是否超过tags总长
            if (index + size + 1 > length) {
                size = length - 1 - index
            }
            tagList = tagList.subList(index + 1, index + size + 1)
            left = length - index - size - 1
            Pair(tagList, left)
        }
    }
}
