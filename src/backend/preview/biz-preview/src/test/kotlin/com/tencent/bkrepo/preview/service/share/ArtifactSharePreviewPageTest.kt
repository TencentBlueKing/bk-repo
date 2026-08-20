package com.tencent.bkrepo.preview.service.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("作品分享短链内嵌页")
class ArtifactSharePreviewPageTest {

    @Test
    fun `escapes preview url and title`() {
        val html = ArtifactSharePreviewPage.render(
            "https://example.com/ui/p/filePreview?token=ab&x=1",
            "BKCI介绍",
        )
        assertTrue(html.contains("src=\"https://example.com/ui/p/filePreview?token=ab&amp;x=1\""))
        assertTrue(html.contains("<title>BKCI介绍</title>"))
        assertTrue(html.contains("<iframe"))
    }

    @Test
    fun `blank title falls back to default`() {
        val html = ArtifactSharePreviewPage.render("/ui/p/filePreview/local/0/r/a.html", "  ")
        assertTrue(html.contains("<title>预览</title>"))
        assertFalse(html.contains("Location"))
    }
}
