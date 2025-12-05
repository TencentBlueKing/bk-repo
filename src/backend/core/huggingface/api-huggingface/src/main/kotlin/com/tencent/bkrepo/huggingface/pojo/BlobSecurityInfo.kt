package com.tencent.bkrepo.huggingface.pojo

data class BlobSecurityInfo(
    val safe: Boolean,
    val status: String,
    val avScan: Map<Any, Any>?,
    val pickleImportScan: Map<Any, Any>?
)
