package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlMemberRootElement

@JacksonXmlRootElement(localName = "struct")
class XmlStructRootElement(
    @JacksonXmlElementWrapper(localName = "member", useWrapping = false)
    val member: MutableList<XmlMemberRootElement>?
)
