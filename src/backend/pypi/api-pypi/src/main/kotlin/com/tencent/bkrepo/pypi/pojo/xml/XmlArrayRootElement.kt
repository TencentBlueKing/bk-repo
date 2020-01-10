package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "array")
class XmlArrayRootElement (
    @JacksonXmlElementWrapper(localName = "data")
    val data: XmlDataRootElement?
)