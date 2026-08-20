package com.tencent.bkrepo.preview.service.share

/**
 * 组织范围 ID 映射：对外展示 ID ↔ 库内存储（及访问校验）ID。
 */
interface OrgScopeIdMappingService {

    /**
     * @return `displayId -> storedId`；未命中的 ID 不会出现在结果中
     */
    fun toStoredIds(displayIds: Collection<String>): Map<String, String>

    /**
     * @return `storedId -> displayId`；未命中的 ID 不会出现在结果中
     */
    fun toDisplayIds(storedIds: Collection<String>): Map<String, String>
}
