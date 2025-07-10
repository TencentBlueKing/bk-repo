/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.replication.pojo.docker.Manifest
import com.tencent.bkrepo.replication.pojo.remote.ManifestInfo
import java.io.InputStream

/**
 * 流转换为manifest对象，并获取对应属性
 */
object ManifestParser {

    /**
     * 解析manifest文件，获取所有digest列表
     */
    fun parseManifest(inputStream: InputStream?): ManifestInfo? {
        if (inputStream == null) return null
        val list = mutableListOf<String>()
        val manifest = inputStream.use { it.readJsonString<Manifest>() }
        val configFullPath = manifest.config.digest
        val iterator = manifest.layers.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            list.add(next.digest)
        }
        list.add(configFullPath)
        return ManifestInfo(list, manifest.mediaType)
    }
}
