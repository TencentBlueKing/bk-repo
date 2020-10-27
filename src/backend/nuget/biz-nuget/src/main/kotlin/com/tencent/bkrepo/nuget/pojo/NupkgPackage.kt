package com.tencent.bkrepo.nuget.pojo

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "package")
data class NupkgPackage(
    @JacksonXmlProperty(isAttribute = true)
    val xmlns: String?,
    val metadata: NupkgMetadata
)

data class NupkgMetadata(
    @JacksonXmlProperty(isAttribute = true)
    val minClientVersion: String,
    val id: String,
    val version: String,
    val title: String,
    val authors: String,
    val owners: String,
    val requireLicenseAcceptance: Boolean,
    val licenseUrl: String,
    val projectUrl: String,
    val iconUrl: String,
    val description: String,
    val summary: String,
    val language: String,
    val dependencies: MutableList<Dependency>
)

data class Dependency(
    @JacksonXmlProperty(isAttribute = true)
    val id: String,
    @JacksonXmlProperty(isAttribute = true)
    val version: String
) {
    override fun toString(): String {
        return "id=$id version=$version"
    }
}
