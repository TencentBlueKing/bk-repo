package com.tencent.bkrepo.maven.pojo

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "metadata")
data class MavenMetadata(
    val groupId: String,
    val artifactId: String,
    var versioning: MavenVersioning

)

class MavenVersioning(
    var release: String,
    var versions: MavenVersions,
    var lastUpdated: String
)

class MavenVersions(
    @JacksonXmlElementWrapper(useWrapping = false)
    var version: MutableList<String>
)
