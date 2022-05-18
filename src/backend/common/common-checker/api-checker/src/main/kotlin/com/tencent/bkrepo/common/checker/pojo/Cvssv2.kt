package com.tencent.bkrepo.common.checker.pojo

data class Cvssv2(
    val accessComplexity: String,
    val accessVector: String,
    val authenticationr: String,
    val availabilityImpact: String,
    val confidentialImpact: String,
    val exploitabilityScore: String,
    val impactScore: String,
    val integrityImpact: String,
    val score: Double,
    val severity: String,
    val version: String
)
