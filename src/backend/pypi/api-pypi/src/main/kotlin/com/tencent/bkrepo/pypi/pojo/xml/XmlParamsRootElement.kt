package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlParamRootElement

@JacksonXmlRootElement(localName = "params")
class XmlParamsRootElement (
    @JacksonXmlElementWrapper(localName = "param", useWrapping = false)
    val param: List<XmlParamRootElement>?
)