package com.tencent.bkrepo.analyst.pojo.report

/**
 * 存在漏洞的组件
 */
data class Component(
    /**
     * 组件名
     */
    val name: String,
    /**
     * 存在漏洞的组件版本列表
     */
    val versions: MutableSet<String> = HashSet(),
    /**
     * 漏洞列表
     */
    val vulnerabilities: MutableSet<Vulnerability> = HashSet(),
)
