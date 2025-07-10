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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bk.sdk.notice.config.BkNoticeConfig
import com.tencent.bk.sdk.notice.impl.BkNoticeClient
import com.tencent.bk.sdk.notice.model.resp.AnnouncementDTO
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.config.BkNoticeProperties
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.LocaleResolver

@RestController
@RequestMapping("/api/notice")
class NoticeController(
    private val properties: BkNoticeProperties,
    private val localeResolver: LocaleResolver,
) {
    @GetMapping
    fun listAnnouncements(
        @RequestParam(required = false) offset: Int? = null,
        @RequestParam(required = false) limit: Int? = null,
    ): Response<List<AnnouncementDTO>> {
        if (properties.apiBaseUrl.isBlank() || properties.appCode.isBlank() || properties.appSecret.isBlank()) {
            logger.warn("The config of notice has uncorrected empty")
            return ResponseBuilder.success()
        }
        val bkNoticeConfig = BkNoticeConfig(properties.apiBaseUrl, properties.appCode, properties.appSecret)
        val lang = localeResolver.resolveLocale(HttpContextHolder.getRequest()).toLanguageTag().lowercase()
        return ResponseBuilder.success(BkNoticeClient(bkNoticeConfig).getCurrentAnnouncements(lang, offset, limit))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NoticeController::class.java)
    }
}
