package com.tencent.bkrepo.preview.service.share

/**
 * 未注册任何 [OrgScopeIdMappingService] 实现时的空操作占位：展示 ID 与库内 ID 原样对应。
 *
 * 由 [PreviewShareConfiguration] 在缺少其它实现时注册，不要再加 `@Service`：
 * `@ConditionalOnMissingBean` 写在组件扫描类上会匹配到自身，导致 Bean 注册失败。
 */
class NoopOrgScopeIdMappingService : OrgScopeIdMappingService {

    override fun toStoredIds(displayIds: Collection<String>): Map<String, String> {
        return identity(displayIds)
    }

    override fun toDisplayIds(storedIds: Collection<String>): Map<String, String> {
        return identity(storedIds)
    }

    private fun identity(ids: Collection<String>): Map<String, String> {
        return ids.filter { it.isNotBlank() }.distinct().associateWith { it }
    }
}
