package com.tencent.bkrepo.auth.pojo.permission

data class CheckPermissionContext(
    var userId: String,
    var roles: List<String>,
    var resourceType: String,
    var action: String,
    var projectId: String,
    var repoName: String? = null,
    var path: String? = null,
    /**
     * 请求级缓存：仓库是否为 Generic 类型。
     * - null：尚未查询；
     * - true：Generic；false：非 Generic；
     * - 同一次校验链路中复用，避免重复调用 RepositoryService.getRepoDetail。
     */
    var isGenericRepo: Boolean? = null,
    /**
     * 请求级缓存：仓库是否处于严格模式（accessControlMode == STRICT）。
     * - null：尚未查询；
     * - true/false：避免重复查询 repoAuthConfigDao。
     */
    var strictMode: Boolean? = null,
)