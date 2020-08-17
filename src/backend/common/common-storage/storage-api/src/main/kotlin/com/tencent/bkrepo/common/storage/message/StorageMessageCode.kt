package com.tencent.bkrepo.common.storage.message

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 存储错误码
 */
enum class StorageMessageCode(private val businessCode: Int, private val key: String) : MessageCode {

    STORE_ERROR(1, "storage.store.error"),
    LOAD_ERROR(2, "storage.load.error"),
    DELETE_ERROR(3, "storage.delete.error"),
    QUERY_ERROR(4, "storage.query.error"),
    BLOCK_EMPTY(5, "storage.block.empty"),
    BLOCK_MISSING(6, "storage.block.missing");

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 11
}
