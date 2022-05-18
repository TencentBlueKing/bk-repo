package com.tencent.bkrepo.common.checker.pojo

data class EvidenceCollected(
    val productEvidence: List<ProductEvidence>,
    val vendorEvidence: List<VendorEvidence>,
    val versionEvidence: List<VersionEvidence>
)
