package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "data")
class XmlDataRootElement(
    @JacksonXmlElementWrapper(localName = "value", useWrapping = false)
    val value: MutableList<XmlValueRootElement>?
)