package com.tencent.bkrepo.opdata.handler.impl

import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_NUMBER
import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_STRING
import com.tencent.bkrepo.opdata.constant.OPDATA_NODE_NUM
import com.tencent.bkrepo.opdata.constant.OPDATA_NODE_SIZE
import com.tencent.bkrepo.opdata.constant.OPDATA_REPO_NAME
import com.tencent.bkrepo.opdata.handler.QueryHandler
import com.tencent.bkrepo.opdata.model.ArchiveInfoModel
import com.tencent.bkrepo.opdata.pojo.Columns
import com.tencent.bkrepo.opdata.pojo.QueryResult
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import org.springframework.stereotype.Component

/**
 * 归档信息统计
 * */
@Component
class ArchiveInfoHandler(
    private val archiveInfoModel: ArchiveInfoModel,
) : QueryHandler {
    override val metric: Metrics = Metrics.ARCHIVEDiNFO

    override fun handle(target: Target, result: MutableList<Any>) {
        val rows = mutableListOf<List<Any>>()
        val columns = mutableListOf<Columns>()
        val resultMap = archiveInfoModel.info()

        columns.add(Columns(OPDATA_REPO_NAME, OPDATA_GRAFANA_STRING))
        columns.add(Columns(OPDATA_NODE_NUM, OPDATA_GRAFANA_NUMBER))
        columns.add(Columns(OPDATA_NODE_SIZE, OPDATA_GRAFANA_NUMBER))
        resultMap.toList().forEach { rows.add(listOf(it.first, it.second[0], it.second[1])) }
        val data = QueryResult(columns, rows, target.type)
        result.add(data)
    }
}
