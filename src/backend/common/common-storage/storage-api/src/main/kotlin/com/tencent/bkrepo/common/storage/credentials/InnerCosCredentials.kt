/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.credentials

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.annotation.Sensitive
import com.tencent.bkrepo.common.metadata.handler.MaskPartString
import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.CompressProperties
import com.tencent.bkrepo.common.storage.config.EncryptProperties
import com.tencent.bkrepo.common.storage.config.MonitorConfig
import com.tencent.bkrepo.common.storage.config.UploadProperties

/**
 * inner cos 身份认证信息
 */
data class InnerCosCredentials(
    var secretId: String = "",
    @Sensitive(handler = MaskPartString::class)
    var secretKey: String = "",
    var region: String = "",
    var bucket: String = "",
    var modId: Int? = null,
    var cmdId: Int? = null,
    var timeout: Float = 0.5F,
    var public: Boolean = false,
    var inner: Boolean = false,
    var slowLogSpeed: Int = MB,
    var slowLogTimeInMillis: Long = 30_000,
    /**
     * 分片上传/下载阈值，单位 MB
     */
    var multipartThreshold: Long = 10,
    /**
     * COS 直传模式（凭证级）
     * - OFF：关闭直连
     * - ON_CHECK_FAILED：当存储 monitor 出现至少一次检查失败时直连，比 ON_UNHEALTHY 更灵敏
     * - ON_UNHEALTHY：仅当存储 monitor 判定为不健康时直连（需连续失败达到 timesToFallback 阈值）
     * - ALWAYS：全部直连（仍受 ReceiveProperties.enableCosDirectUploadRepos 白名单约束）
     */
    var cosDirectUploadMode: String = COS_DIRECT_UPLOAD_MODE_OFF,
    var download: DownloadProperties = DownloadProperties(),
    override var key: String? = null,
    override var cache: CacheProperties = CacheProperties(),
    override var upload: UploadProperties = UploadProperties(),
    override var encrypt: EncryptProperties = EncryptProperties(),
    override var compress: CompressProperties = CompressProperties(),
    override var monitor: MonitorConfig? = null,
    override var allowRepoTypes: Set<String>? = null,
    override var notAllowRepoTypes: Set<String>? = setOf(RepositoryType.DRIVE.name),
) : StorageCredentials(key, cache, upload, encrypt, compress) {

    companion object {
        const val type = "innercos"
        const val MB = 1024 * 1024

        const val COS_DIRECT_UPLOAD_MODE_OFF = "OFF"
        const val COS_DIRECT_UPLOAD_MODE_ON_CHECK_FAILED = "ON_CHECK_FAILED"
        const val COS_DIRECT_UPLOAD_MODE_ON_UNHEALTHY = "ON_UNHEALTHY"
        const val COS_DIRECT_UPLOAD_MODE_ALWAYS = "ALWAYS"
    }

    data class DownloadProperties(
        var workers: Int = 0,
        var downloadTimeHighWaterMark: Long = 8_000,
        var downloadTimeLowWaterMark: Long = 3_000,
        var taskInterval: Long = 10,
        var timeout: Long = 10_000,
        var minimumPartSize: Long = 10,
        var maxDownloadParts: Int = 10000,
        var qps: Int = 10,
        var lazyLoad: Boolean = false,
    )
}
