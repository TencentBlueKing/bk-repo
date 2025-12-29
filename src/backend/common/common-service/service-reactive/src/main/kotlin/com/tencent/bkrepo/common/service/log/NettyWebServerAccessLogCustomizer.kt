/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.service.log

import com.tencent.bkrepo.common.api.constant.BKREPO_TRACE
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import reactor.netty.http.server.HttpServer
import reactor.netty.http.server.logging.AccessLog
import reactor.util.annotation.Nullable
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NettyWebServerAccessLogCustomizer : NettyServerCustomizer {

    override fun apply(t: HttpServer): HttpServer {
        return t.accessLog(true) { args ->
            // 对URI进行脱敏处理
            val maskedUri = UrlSensitiveDataMasker.maskSensitiveData(args.uri()?.toString())

            AccessLog.create(
                ACCESS_LOG_FORMAT,
                applyHeaderValue(args.responseHeader(BKREPO_TRACE)),
                applyAddress(args.remoteAddress()),
                applyDateTime(args.accessDateTime()),
                args.method(),
                maskedUri,
                args.protocol(),
                args.status(),
                if (args.contentLength() > -1) args.contentLength() else MISSING,
                args.duration()
            )
        }
    }

    private fun applyAddress(@Nullable socketAddress: SocketAddress?): String? {
        return if (socketAddress is InetSocketAddress) socketAddress.hostString else MISSING
    }

    private fun applyHeaderValue(value: CharSequence?): String {
        if (value.isNullOrEmpty()) {
            return MISSING
        }
        return value.toString()
    }

    private fun applyDateTime(datetime: ZonedDateTime?): String {
        return datetime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: MISSING
    }

    companion object {

        private const val ACCESS_LOG_FORMAT = "[{}] {} - [{}] \"{} {} {}\" {} {} {}"
        private const val MISSING = "-"
    }
}
