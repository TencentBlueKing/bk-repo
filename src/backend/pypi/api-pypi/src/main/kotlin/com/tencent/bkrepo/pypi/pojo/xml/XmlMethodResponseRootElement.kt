package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "methodResponse")
class XmlMethodResponseRootElement(
    @JacksonXmlElementWrapper(localName = "params")
    val params: XmlParamsRootElement?
)