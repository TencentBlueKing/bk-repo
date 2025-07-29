package com.tencent.bkrepo.repository.service.schedule

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadRule
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleUpdateRequest

interface ScheduledDownloadRuleService {
    /**
     * 创建预约下载规则
     *
     * @param request 创建请求
     *
     * @return 完成创建的规则
     */
    fun create(request: UserScheduledDownloadRuleCreateRequest): ScheduledDownloadRule

    /**
     * 删除预约下载规则
     *
     * @param id 预约下载规则id
     */
    fun remove(id: String)

    /**
     * 更新预约下载规则
     *
     * @param request 更新请求
     *
     * @return 更新后的规则
     */
    fun update(request: UserScheduledDownloadRuleUpdateRequest): ScheduledDownloadRule

    /**
     * 分页获取预约下载规则
     *
     * @param request 查询请求
     *
     * @return 预约下载规则分页结果
     */
    fun page(request: UserScheduledDownloadRuleQueryRequest): Page<ScheduledDownloadRule>

    /**
     * 获取预约下载规则
     *
     * @param id 预约下载规则id
     *
     * @return [id]对应的预约下载规则
     */
    fun get(id: String): ScheduledDownloadRule
}
