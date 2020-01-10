package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlArrayRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlStructRootElement

@JacksonXmlRootElement(localName = "value")
class XmlValueRootElement(
    @JacksonXmlElementWrapper(localName = "struct")
    val struct: XmlStructRootElement?,
    @JacksonXmlElementWrapper(localName = "array")
    val array: XmlArrayRootElement?,
    @JacksonXmlProperty(localName = "string")
    val string: String?,
    @JacksonXmlProperty(localName = "int")
    val int: Int?

)