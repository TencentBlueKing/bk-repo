package com.tencent.bkrepo.rpm.util.redline.model

import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata
import java.io.InputStream

data class RpmMetadataWithOldStream(
    val newRpmMetadata: RpmMetadata,
    val OldPrimaryStream: InputStream
)
