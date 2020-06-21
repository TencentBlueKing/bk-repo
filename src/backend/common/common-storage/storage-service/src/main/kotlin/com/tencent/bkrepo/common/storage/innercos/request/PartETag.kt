package com.tencent.bkrepo.common.storage.innercos.request

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class PartETag(
    @get:JacksonXmlProperty(localName = "PartNumber")
    val partNumber: Int,
    @get:JacksonXmlProperty(localName = "ETag")
    val eTag: String
)
