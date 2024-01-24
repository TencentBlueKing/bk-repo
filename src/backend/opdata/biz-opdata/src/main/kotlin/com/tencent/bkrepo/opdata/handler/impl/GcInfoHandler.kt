package com.tencent.bkrepo.opdata.handler.impl

import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_NUMBER
import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_STRING
import com.tencent.bkrepo.opdata.constant.OPDATA_POST_GC
import com.tencent.bkrepo.opdata.constant.OPDATA_PRE_GC
import com.tencent.bkrepo.opdata.constant.OPDATA_RATIO
import com.tencent.bkrepo.opdata.constant.OPDATA_REPO_NAME
import com.tencent.bkrepo.opdata.handler.QueryHandler
import com.tencent.bkrepo.opdata.model.GcInfoModel
import com.tencent.bkrepo.opdata.pojo.Columns
import com.tencent.bkrepo.opdata.pojo.QueryResult
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import org.springframework.stereotype.Component
import java.math.BigDecimal.ROUND_HALF_UP

/**
 * GC信息统计
 * */
@Component
class GcInfoHandler(
    private val gcInfoModel: GcInfoModel,
) : QueryHandler {
    override val metric: Metrics = Metrics.GCINFO

    override fun handle(target: Target, result: MutableList<Any>) {
        val rows = mutableListOf<List<Any>>()
        val columns = mutableListOf<Columns>()
        val resultMap = gcInfoModel.info()

        columns.add(Columns(OPDATA_REPO_NAME, OPDATA_GRAFANA_STRING))
        columns.add(Columns(OPDATA_PRE_GC, OPDATA_GRAFANA_NUMBER)) // GC前
        columns.add(Columns(OPDATA_POST_GC, OPDATA_GRAFANA_NUMBER)) // GC后
        columns.add(Columns(OPDATA_RATIO, OPDATA_GRAFANA_NUMBER)) // 压缩率
        resultMap.toList().forEach {
            rows.add(
                listOf(it.first, it.second[0], it.second[1], calculateRatio(it.second[0], it.second[1])),
            )
        }
        val data = QueryResult(columns, rows, target.type)
        result.add(data)
    }

    private fun calculateRatio(uncompressedSize: Long, compressedSize: Long): Double {
        if (compressedSize == 0L) {
            return 0.0
        }
        val ratio = (uncompressedSize - compressedSize.toDouble()) / uncompressedSize * HUNDRED
        return ratio.toBigDecimal().setScale(1, ROUND_HALF_UP).toDouble()
    }

    companion object {
        private const val HUNDRED = 100
    }
}
