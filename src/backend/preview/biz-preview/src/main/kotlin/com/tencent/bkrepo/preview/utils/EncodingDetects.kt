/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.preview.utils

import org.mozilla.universalchardet.UniversalDetector
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files

/**
 * 自动获取文件的编码
 */
object EncodingDetects {
    private const val DEFAULT_LENGTH = 4096
    private const val LIMIT = 50
    private val logger = LoggerFactory.getLogger(EncodingDetects::class.java)
    fun getJavaEncode(filePath: String?): String? {
        return getJavaEncode(File(filePath))
    }

    fun getJavaEncode(file: File): String? {
        val len = Math.min(DEFAULT_LENGTH, file.length().toInt())
        val content = ByteArray(len)
        try {
            Files.newInputStream(file.toPath()).use { fis -> fis.read(content, 0, len) }
        } catch (e: IOException) {
            logger.error("File read failed:{}", file.path)
        }
        return getJavaEncode(content)
    }

    fun getJavaEncode(content: ByteArray?): String? {
        if (content != null && content.size <= LIMIT) {
            return SimpleEncodingDetects.getJavaEncode(content)
        }
        val detector = UniversalDetector(null)
        detector.handleData(content, 0, content!!.size)
        detector.dataEnd()
        var charsetName: String? = detector.getDetectedCharset()
        if (charsetName == null) {
            charsetName = Charset.defaultCharset().name()
        }
        return charsetName
    }
}