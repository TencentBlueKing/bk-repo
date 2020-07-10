package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

object XmlConvertUtil {

    fun xml2MethodCall(xmlString: String): MethodCall {
        val xmlMapper = XmlMapper().registerModule(KotlinModule()) as XmlMapper
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT)
        return xmlMapper.readValue(xmlString, MethodCall::class.java)
    }

    fun xml2MethodResponse(xmlString: String): MethodResponse {
        val xmlMapper = XmlMapper().registerModule(KotlinModule()) as XmlMapper
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT)
        return xmlMapper.readValue(xmlString, MethodResponse::class.java)
    }

    fun methodResponse2Xml(methodResponse: MethodResponse): String {
        val xmlMapper = XmlMapper().registerModule(KotlinModule()) as XmlMapper
        xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        xmlMapper.enable(MapperFeature.USE_STD_BEAN_NAMING)
        return xmlMapper.writeValueAsString(methodResponse)
    }
}
