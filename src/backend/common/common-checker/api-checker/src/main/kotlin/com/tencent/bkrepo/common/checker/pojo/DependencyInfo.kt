package com.tencent.bkrepo.common.checker.pojo

data class DependencyInfo(
    val dependencies: List<Dependency>,
    val projectInfo: ProjectInfo,
    val reportSchema: String,
    val scanInfo: ScanInfo
)
