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

package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.pojo.PreviewOptions
import com.tencent.bkrepo.preview.pojo.Watermark
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CommonResourceService(private val config: PreviewConfig) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CommonResourceService::class.java)
    }

    /**
     * 水印
     */
    fun getWatermark(decodedParams: String?): Watermark {
        val watermark = if (!decodedParams.isNullOrEmpty()) {
            decodedParams!!.readJsonString()
        } else {
            Watermark()
        }
        watermark.watermarkTxt = watermark.watermarkTxt ?: config.watermarkTxt
        watermark.watermarkXSpace = watermark.watermarkXSpace ?: config.watermarkXSpace
        watermark.watermarkYSpace = watermark.watermarkYSpace ?: config.watermarkYSpace
        watermark.watermarkFont = watermark.watermarkFont ?: config.watermarkFont
        watermark.watermarkFontsize = watermark.watermarkFontsize ?: config.watermarkFontsize
        watermark.watermarkColor = watermark.watermarkColor ?: config.watermarkColor
        watermark.watermarkAlpha = watermark.watermarkAlpha ?: config.watermarkAlpha
        watermark.watermarkWidth = watermark.watermarkWidth ?: config.watermarkWidth
        watermark.watermarkHeight = watermark.watermarkHeight ?: config.watermarkHeight
        watermark.watermarkAngle = watermark.watermarkAngle ?: config.watermarkAngle
        return watermark
    }

    /**
     * 预览属性
     */
    fun getPreviewOptions(): PreviewOptions {
        return PreviewOptions(
            pdfPresentationModeDisable = config.isPdfPresentationModeDisable,
            pdfOpenFileDisable = config.isPdfOpenFileDisable,
            pdfPrintDisable = config.isPdfPrintDisable,
            pdfDownloadDisable = config.isPdfDownloadDisable,
            pdfBookmarkDisable = config.isPdfBookmarkDisable,
            pdfDisableEditing = config.isPdfDisableEditing,
            switchDisabled = config.isOfficePreviewSwitchDisabled
        )
    }
}