package com.tencent.bkrepo.scanner.component.manager

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult

/**
 * 详细扫描结果管理
 */
interface ScanExecutorResultManager {
    /**
     * 保存扫描结果详情
     *
     * @param credentialsKey 被扫描文件所在存储， 为null时表示在默认存储
     * @param sha256 被扫描文件sha256
     * @param scanner 使用的扫描器名称
     * @param result 扫描结果详情
     */
    fun save(credentialsKey: String?, sha256: String, scanner: String, result: ScanExecutorResult)

    /**
     * 分页获取指定类型的扫描结果详情
     *
     * @param credentialsKey 被扫描文件所在存储， 为null时表示在默认存储
     * @param sha256 被扫描文件sha256
     * @param scanner 使用的扫描器名称
     * @param type 指定类型的扫描结果详情
     * @param pageLimit 分页信息
     *
     * @return 扫描结果详情
     */
    fun load(credentialsKey: String?, sha256: String, scanner: String, type: String, pageLimit: PageLimit): Page<Any>
}
