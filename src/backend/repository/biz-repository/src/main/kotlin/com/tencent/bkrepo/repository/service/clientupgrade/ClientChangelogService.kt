package com.tencent.bkrepo.repository.service.clientupgrade

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogEntry
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogListOption
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogUpsertRequest
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogVo

/**
 * 客户端 changelog 服务。
 *
 * 接口分两类：
 * - 客户端只读 [findPublishedEntry] / [pagePublishedHistory]，仅返回 PUBLISHED 状态的记录
 * - 管理端 CRUD [listPage] / [getById] / [upsert] / [remove]，删除采用物理删除
 */
interface ClientChangelogService {

    /**
     * 客户端：查询某产品某版本的已发布 changelog。
     * @return 命中返回 entry，未命中返回 null（客户端按"无 changelog"处理）
     */
    fun findPublishedEntry(
        productId: String,
        version: String,
    ): ClientChangelogEntry?

    /**
     * 客户端：分页查询某产品的已发布 changelog 历史，按 releasedAt 降序。
     */
    fun pagePublishedHistory(
        productId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<ClientChangelogEntry>

    /** 管理端：分页查询 */
    fun listPage(option: ClientChangelogListOption): Page<ClientChangelogVo>

    /** 管理端：详情 */
    fun getById(id: String): ClientChangelogVo

    /** 管理端：按 (productId, version) 查询详情 */
    fun getByKey(productId: String, version: String): ClientChangelogVo

    /** 管理端：新增或更新 */
    fun upsert(userId: String, request: ClientChangelogUpsertRequest): ClientChangelogVo

    /** 管理端：物理删除 */
    fun remove(userId: String, id: String)
}
