package com.tencent.bkrepo.rpm.util.redline.model

import org.redline_rpm.header.RpmType
import java.io.Serializable

class RpmFormat(
    val headerStart: Int,
    val headerEnd: Int,
    val format: FormatWithType,
    val type: RpmType
) : Serializable
