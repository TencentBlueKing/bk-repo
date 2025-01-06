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

import org.apache.commons.lang3.StringUtils
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * FTP下载工具
 */
object FtpUtils {
    private val logger = LoggerFactory.getLogger(FtpUtils::class.java)

    @Throws(IOException::class)
    fun connect(host: String?, port: Int, username: String?, password: String?, controlEncoding: String?): FTPClient {
        val ftpClient = FTPClient()
        ftpClient.connect(host, port)
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            ftpClient.login(username, password)
        }
        val reply: Int = ftpClient.getReplyCode()
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftpClient.disconnect()
        }
        ftpClient.setControlEncoding(controlEncoding)
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
        return ftpClient
    }

    @Throws(IOException::class)
    fun download(
        ftpUrl: String?,
        localFilePath: String?,
        ftpUsername: String?,
        ftpPassword: String?,
        ftpControlEncoding: String?
    ) {
        val url = URL(ftpUrl)
        val host = url.host
        val port = if (url.port == -1) url.defaultPort else url.port
        val remoteFilePath = url.path
        logger.debug(
            "FTP connection url:{}, username:{}, password:***, controlEncoding:{}, localFilePath:{}",
            ftpUrl,
            ftpUsername,
            ftpControlEncoding,
            localFilePath
        )
        val ftpClient: FTPClient = connect(host, port, ftpUsername, ftpPassword, ftpControlEncoding)
        val outputStream = Files.newOutputStream(Paths.get(localFilePath))
        ftpClient.enterLocalPassiveMode()
        val downloadResult: Boolean = ftpClient.retrieveFile(
            String(
                remoteFilePath.toByteArray(
                    charset(
                        ftpControlEncoding!!
                    )
                ), StandardCharsets.ISO_8859_1
            ), outputStream
        )
        logger.debug("FTP download result {}", downloadResult)
        outputStream.flush()
        outputStream.close()
        ftpClient.logout()
        ftpClient.disconnect()
    }
}