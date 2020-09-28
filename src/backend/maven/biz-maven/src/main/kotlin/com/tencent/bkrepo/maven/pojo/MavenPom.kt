package com.tencent.bkrepo.maven.pojo

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "project")
data class MavenPom(
        @JacksonXmlProperty(isAttribute = true, namespace = "xsi")
        val schemaLocation: String?,
        val modelVersion: String?,
        val groupId: String,
        val artifactId: String,
        val version: String
)