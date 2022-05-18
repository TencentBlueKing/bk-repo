package com.tencent.bkrepo.common.checker.pojo

data class Cvssv3(
    val attackComplexity: String,
    val attackVector: String,
    val availabilityImpact: String,
    val baseScore: Double,
    val baseSeverity: String,
    val confidentialityImpact: String,
    val exploitabilityScore: String?,
    val impactScore: String?,
    val integrityImpact: String,
    val privilegesRequired: String,
    val scope: String,
    val userInteraction: String,
    val version: String?
)
