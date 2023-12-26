/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.TEMP
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.bound
import com.tencent.bkrepo.common.storage.core.crypto.CipherFactoryProducer
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.common.storage.util.createFile
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.nio.file.Paths

/**
 * 支持存储加密
 * */
abstract class AbstractEncryptorFileStorage<Credentials : StorageCredentials, Client> :
    AbstractFileStorage<Credentials, Client>() {
    override fun store(path: String, name: String, file: File, storageCredentials: StorageCredentials) {
        if (!storageCredentials.encrypt.enabled) {
            return super.store(path, name, file, storageCredentials)
        }
        val encryptedFile = createTempFile(storageCredentials)
        val newName = getEncryptName(name, storageCredentials)
        try {
            val throughput = measureThroughput { encryptFile(storageCredentials, file, encryptedFile) }
            logger.info("Success to encrypt artifact file [$name], $throughput.")
            super.store(path, newName, encryptedFile, storageCredentials)
        } finally {
            encryptedFile.delete()
            logger.info("Delete temp encrypt file [${encryptedFile.absolutePath}] success.")
        }
    }

    override fun store(
        path: String,
        name: String,
        inputStream: InputStream,
        size: Long,
        storageCredentials: StorageCredentials,
    ) {
        if (!storageCredentials.encrypt.enabled) {
            return super.store(path, name, inputStream, size, storageCredentials)
        }
        val secretKey = storageCredentials.encrypt.key
        val cipherFactory = CipherFactoryProducer.getFactory(storageCredentials.encrypt.algorithm)
        cipherFactory.getEncryptInputStream(inputStream, secretKey).use {
            val newSize = size + IV_LENGTH
            val newName = getEncryptName(name, storageCredentials)
            return super.store(path, newName, it, newSize, storageCredentials)
        }
    }

    override fun load(path: String, name: String, range: Range, storageCredentials: StorageCredentials): InputStream? {
        if (!storageCredentials.encrypt.enabled) {
            return super.load(path, name, range, storageCredentials)
        }
        // 不支持部分解密，所以需要完整拉取整个加密文件
        val size = range.length + IV_LENGTH
        val newName = getEncryptName(name, storageCredentials)
        val encryptedInput = super.load(path, newName, Range.full(size), storageCredentials) ?: return null
        val key = storageCredentials.encrypt.key
        val cipherFactory = CipherFactoryProducer.getFactory(storageCredentials.encrypt.algorithm)
        val inputStream = cipherFactory.getPlainInputStream(encryptedInput, key)
        if (range.start > 0) {
            inputStream.skip(range.start)
        }
        return inputStream.bound(range)
    }

    override fun delete(path: String, name: String, storageCredentials: StorageCredentials) {
        if (!storageCredentials.encrypt.enabled) {
            return super.delete(path, name, storageCredentials)
        }
        val newName = getEncryptName(name, storageCredentials)
        super.delete(path, newName, storageCredentials)
    }

    override fun exist(path: String, name: String, storageCredentials: StorageCredentials): Boolean {
        if (!storageCredentials.encrypt.enabled) {
            return super.exist(path, name, storageCredentials)
        }
        val newName = getEncryptName(name, storageCredentials)
        return super.exist(path, newName, storageCredentials)
    }

    override fun copy(
        path: String,
        name: String,
        fromCredentials: StorageCredentials,
        toCredentials: StorageCredentials,
    ) {
        if (!fromCredentials.encrypt.enabled) {
            return super.copy(path, name, fromCredentials, toCredentials)
        }
        val newName = getEncryptName(name, fromCredentials)
        super.copy(path, newName, fromCredentials, toCredentials)
    }

    /**
     * 加密文件
     * @param storageCredentials 存储实例
     * @param file 原始文件
     * @param newFile 加密后的新文件
     * */
    private fun encryptFile(
        storageCredentials: StorageCredentials,
        file: File,
        newFile: File,
    ): Long {
        val key = storageCredentials.encrypt.key
        val cipherFactory = CipherFactoryProducer.getFactory(storageCredentials.encrypt.algorithm)
        cipherFactory.getEncryptOutputStream(newFile.outputStream(), key).use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        return newFile.length()
    }

    /**
     * 在指定存储实例中创建一个临时文件
     * */
    private fun createTempFile(storageCredentials: StorageCredentials): File {
        val tempPath = Paths.get(storageCredentials.upload.location, TEMP)
        val filename = StringPool.randomStringByLongValue(ENCRYPT_FILE_PREFIX, ENCRYPT_FILE_SUFFIX)
        val filePath = tempPath.resolve(filename)
        return filePath.createFile()
    }

    /**
     * 获取加密数据的名字
     * */
    private fun getEncryptName(name: String, storageCredentials: StorageCredentials): String {
        val alg = storageCredentials.encrypt.algorithm.toLowerCase()
        return name.plus(".$alg")
    }

    companion object {
        private const val ENCRYPT_FILE_PREFIX = "encrypting_"
        private const val ENCRYPT_FILE_SUFFIX = ".temp"
        private const val IV_LENGTH = 16
        private val logger = LoggerFactory.getLogger(AbstractEncryptorFileStorage::class.java)
    }
}
