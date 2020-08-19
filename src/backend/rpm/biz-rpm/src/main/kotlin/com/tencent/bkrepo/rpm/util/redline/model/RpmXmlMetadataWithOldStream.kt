package com.tencent.bkrepo.rpm.util.redline.model

import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import java.io.InputStream

data class RpmXmlMetadataWithOldStream(
    val newRpmXmlMetadata: RpmXmlMetadata,
    val OldPrimaryStream: InputStream
)
