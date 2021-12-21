package com.tencent.bkrepo.executor.service

import com.tencent.bkrepo.executor.pojo.context.FileScanContext
import com.tencent.bkrepo.executor.pojo.context.RepoScanContext
import com.tencent.bkrepo.executor.pojo.context.ScanReportContext
import com.tencent.bkrepo.executor.pojo.response.TaskRunResponse

interface Task {

    /**
     * 文件制品扫描任务入口
     * @param context  文件扫描context
     * @return 任务Id
     */
    fun runFile(context: FileScanContext): String

    /**
     * 仓库制品扫描任务入口
     * @param context  仓库文件扫描context
     * @return 任务Id
     */
    fun runRepo(context: RepoScanContext): String

    /**
     * 获取任务执行状态
     * @param taskId 任务ID
     * @param pageNum 数据的页码
     * @param pageSize 每页文件个数
     * @return TaskRunResponse 任务执行状态
     */
    fun getTaskStatus(taskId: String, pageNum: Int?, pageSize: Int?): TaskRunResponse

    /**
     * 获取任务报告
     * @param context 任务报告context
     * @return Map<Any, Any>? 任务执行状态
     */
    fun getTaskReport(context: ScanReportContext): MutableList<*>?
}
