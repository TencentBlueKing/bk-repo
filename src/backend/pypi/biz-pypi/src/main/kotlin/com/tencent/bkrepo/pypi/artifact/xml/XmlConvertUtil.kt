package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper

object XmlConvertUtil {

    fun xml2Bean(xmlString: String): MethodCall {
        val module = JacksonXmlModule()
        module.setDefaultUseWrapper(false)
        val xmlMapper = XmlMapper(module)
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT)
        return xmlMapper.readValue(xmlString, MethodCall::class.java)
    }

    fun bean2Xml(methodResponse: MethodResponse): String {
        var xmlMapper = XmlMapper()
        xmlMapper.setDefaultUseWrapper(false)
        xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        xmlMapper.enable(MapperFeature.USE_STD_BEAN_NAMING)
        return xmlMapper.writeValueAsString(methodResponse)
    }
}
