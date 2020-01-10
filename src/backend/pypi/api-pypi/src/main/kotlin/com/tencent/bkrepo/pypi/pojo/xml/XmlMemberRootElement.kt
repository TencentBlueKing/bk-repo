package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class XmlMemberRootElement (
    @JacksonXmlProperty(localName = "name")
    val name: String?,
    @JacksonXmlElementWrapper(localName = "value")
    val value: XmlValueRootElement?
)