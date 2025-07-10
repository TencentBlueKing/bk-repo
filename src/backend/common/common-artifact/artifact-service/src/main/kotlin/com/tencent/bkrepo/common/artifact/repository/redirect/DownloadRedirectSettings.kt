/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.repository.redirect

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration

/**
 * 仓库重定向配置
 */
data class DownloadRedirectSettings(
    /**
     * 下载请求重定向目标
     */
    val redirectTo: String? = null,

    /**
     * 路径正则匹配规则，符合规则的制品才会被重定向
     */
    val fullPathRegex: String? = null,
) {

    companion object {
        /**
         * [RepositoryConfiguration.settings]中的配置键
         */
        private const val SETTINGS_KEY_DOWNLOAD_REDIRECT = "downloadRedirect"
        fun from(configuration: RepositoryConfiguration): DownloadRedirectSettings? {
            val redirectSettings =
                configuration.getSetting<Map<String, Any>>(SETTINGS_KEY_DOWNLOAD_REDIRECT) ?: return null
            val redirectTo = redirectSettings[DownloadRedirectSettings::redirectTo.name] as String?
            val fullPathRegex = redirectSettings[DownloadRedirectSettings::fullPathRegex.name] as String?
            return DownloadRedirectSettings(
                redirectTo = redirectTo,
                fullPathRegex = fullPathRegex
            )
        }
    }
}
