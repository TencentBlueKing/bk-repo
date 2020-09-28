package com.tencent.bkrepo.maven.pojo

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "metadata")
data class MavenSnapshot(
    @JacksonXmlProperty
    val modelVersion: String,
    val groupId: String,
    val artifactId: String,
    val versioning: SnapshotVersioning
)

data class SnapshotVersioning(
    val snapshot: Snapshot,
    val lastUpdated: String,
    val snapshotVersions: List<SnapshotVersion>
)

data class Snapshot(
    val timestamp: String,
    val buildNumber: String
)

data class SnapshotVersion(
    val extension: String,
    val value: String,
    val updated: String
)
