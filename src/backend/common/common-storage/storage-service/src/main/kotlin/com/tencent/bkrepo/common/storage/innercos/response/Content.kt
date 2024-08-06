package com.tencent.bkrepo.common.storage.innercos.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Content(
    @JacksonXmlProperty(localName = "Key")
    var key: String = "",
    @JacksonXmlProperty(localName = "LastModified")
    var lastModified: String = "",
    @JacksonXmlProperty(localName = "Created")
    var created: String = "",
    @JacksonXmlProperty(localName = "ETag")
    var etag: String = "",
    @JacksonXmlProperty(localName = "Size")
    var size: Long = 0,
    @JacksonXmlProperty(localName = "Forbid")
    var forbid: Int = 0,
)
