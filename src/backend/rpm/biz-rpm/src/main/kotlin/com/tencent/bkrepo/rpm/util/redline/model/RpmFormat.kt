package com.tencent.bkrepo.rpm.util.redline.model

import org.redline_rpm.header.RpmType

data class RpmFormat(
    val headerStart: Int,
    val headerEnd: Int,
    val format: FormatWithType,
    val type: RpmType
)
