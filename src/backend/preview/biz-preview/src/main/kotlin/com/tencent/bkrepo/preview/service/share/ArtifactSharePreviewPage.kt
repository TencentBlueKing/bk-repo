package com.tencent.bkrepo.preview.service.share

import org.springframework.web.util.HtmlUtils

/**
 * 短链 `/a/{shareId}` 返回的内嵌预览页，避免 302 把地址栏换成带 token 的预览 URL。
 */
object ArtifactSharePreviewPage {

    fun render(previewUrl: String, title: String): String {
        val src = HtmlUtils.htmlEscape(previewUrl, Charsets.UTF_8.name())
        val safeTitle = HtmlUtils.htmlEscape(title.ifBlank { DEFAULT_TITLE }, Charsets.UTF_8.name())
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$safeTitle</title>
            <style>
            html,body,iframe{margin:0;padding:0;height:100%;width:100%;border:0}
            body{overflow:hidden}
            </style>
            </head>
            <body>
            <iframe src="$src" title="$safeTitle"></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    private const val DEFAULT_TITLE = "预览"
}
