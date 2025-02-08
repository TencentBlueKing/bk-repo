package com.tencent.bkrepo.repository.config

data class ProjectProxyUrlMapping(
    /**
     * 项目和代理地址的映射
     */
    var urls: MutableMap<String, String> = mutableMapOf(),
)
