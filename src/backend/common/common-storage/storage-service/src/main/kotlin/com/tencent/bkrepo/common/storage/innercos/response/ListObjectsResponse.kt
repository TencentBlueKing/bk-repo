package com.tencent.bkrepo.common.storage.innercos.response

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "ListBucketResult")
data class ListObjectsResponse(
    @JacksonXmlProperty(localName = "Name")
    var name: String = "",
    @JacksonXmlProperty(localName = "Prefix")
    var prefix: String = "",
    @JacksonXmlProperty(localName = "Marker")
    var marker: String = "",
    @JacksonXmlProperty(localName = "IsTruncated")
    var sTruncated: Boolean = false,
    @JacksonXmlProperty(localName = "MaxKeys")
    var maxKeys: Int = 0,
    @JacksonXmlProperty(localName = "NextMarker")
    var nextMarker: String = "",
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Contents")
    var contents: List<Content> = mutableListOf(),
)
