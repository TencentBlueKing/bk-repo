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