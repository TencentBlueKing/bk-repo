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

package com.tencent.bkrepo.common.storage.util

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.nio.file.DirectoryIteratorException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

fun String.toPath(): Path = Paths.get(this)

fun Path.createFile(): File {
    if (!Files.isRegularFile(this)) {
        if (this.parent != null) {
            Files.createDirectories(this.parent)
        }
        try {
            Files.createFile(this)
        } catch (ignored: java.nio.file.FileAlreadyExistsException) {
            // ignore
        }
    }
    return this.toFile()
}

fun Path.createNewOutputStream(): OutputStream {
    if (!Files.isDirectory(this.parent)) {
        Files.createDirectories(this.parent)
    }
    return Files.newOutputStream(
        this,
        StandardOpenOption.CREATE_NEW,
    )
}

/**
 * 删除路径，如果路径为文件或者空目录则删除
 * @return true表示已经执行了删除，false表示未执行删除
 * */
fun Path.delete(): Boolean {
    // 文件
    if (this.toFile().isFile) {
        Files.deleteIfExists(this)
        return true
    }
    // 删除空目录
    try {
        Files.newDirectoryStream(this).use {
            if (!it.iterator().hasNext()) {
                Files.deleteIfExists(this)
                return true
            }
        }
    } catch (e: DirectoryIteratorException) {
        // 子目录已经被其他进程删除时会报该错误
        val cause = e.cause
        if (cause is FileSystemException && cause.message?.contains("Stale file handle") == true) {
            logger.warn("delete dir[$this] failed", e)
        } else {
            throw e
        }
    }
    // 目录还存在内容
    return false
}

/**
 * 判断文件是否存在
 * 传统的Files.exist()底层使用stat来获取文件的属性，通过属性来判断文件是否存在。
 * 但是在nfs中，stat会使用缓存，导致判断不正确，而open方法则请求nfs server，以保持一致性。
 * 在更加需要准确判断文件时，可以使用此方法。
 * @return 文件存在，返回true。如果path是目录或者不存在则返回false
 * */
fun Path.existReal(): Boolean {
    return try {
        this.toFile().inputStream().close()
        true
    } catch (e: FileNotFoundException) {
        false
    }
}

private val logger = LoggerFactory.getLogger("PathExtensions")
