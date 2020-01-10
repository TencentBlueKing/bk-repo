package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "param")
class XmlParamRootElement(
    @JacksonXmlElementWrapper(localName = "value")
    val value: XmlValueRootElement?
)