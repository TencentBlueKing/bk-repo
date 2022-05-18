package com.tencent.bkrepo.common.checker.pojo

data class Dependency(
    val description: String?,
    val evidenceCollected: EvidenceCollected,
    val fileName: String,
    val filePath: String,
    val isVirtual: Boolean,
    val license: String?,
    val md5: String,
    val packages: List<Package>?,
    val sha1: String,
    val sha256: String,
    val vulnerabilities: List<Vulnerability>?,
    val vulnerabilityIds: List<VulnerabilityId>?
)
