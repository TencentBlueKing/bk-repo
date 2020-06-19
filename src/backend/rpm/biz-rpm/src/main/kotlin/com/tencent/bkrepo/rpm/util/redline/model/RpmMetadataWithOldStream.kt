package com.tencent.bkrepo.rpm.util.redline.model

import java.io.InputStream

data class RpmMetadataWithOldStream(
    val newRpmMetadata: RpmMetadata,
    val OldPrimaryStream: InputStream
)
