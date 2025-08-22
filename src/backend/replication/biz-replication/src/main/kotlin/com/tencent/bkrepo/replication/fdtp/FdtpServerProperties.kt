/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.fdtp

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * fdtp服务端配置
 * */
@ConfigurationProperties("fdtp.server")
data class FdtpServerProperties(
    /**
     * fdtp server port
     * */
    var port: Int = 9000,
    /**
     * server accept thread num
     * */
    var accepts: Int = 1,
    /**
     * server worker thread num
     * */
    var workers: Int = Runtime.getRuntime().availableProcessors() * 2,
    /**
     * server接受队列大小
     * */
    var backLog: Int = 128,
    /**
     * ssl 私钥
     * */
    var privateKey: String? = null,
    /**
     * ssl 证书
     * */
    var certificates: String? = null,
    /**
     * ssl 私钥的密钥
     * */
    var privateKeyPassword: String? = null,
    /**
     * 连接认证密钥
     * */
    var secretKey: String = "secret",
)
