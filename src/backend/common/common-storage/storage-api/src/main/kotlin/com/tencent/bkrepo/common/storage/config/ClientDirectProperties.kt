package com.tencent.bkrepo.common.storage.config

/**
 * 制品库客户端 COS 直连下载开关，按 all → 项目 → 仓库 降级。
 */
class ClientDirectProperties {
    /**
     * true：所有制品库客户端下载尝试 COS 回源
     */
    var all: Boolean = false

    /**
     * 项目白名单，命中则该项目下客户端走 COS 回源
     */
    var projects: Set<String> = mutableSetOf()

    /**
     * 仓库白名单，格式 `project/repo`
     */
    var repos: Set<String> = mutableSetOf()

    fun matches(projectId: String, repoName: String): Boolean {
        if (all) {
            return true
        }
        if (projects.contains(projectId)) {
            return true
        }
        return repos.contains("$projectId/$repoName")
    }
}
