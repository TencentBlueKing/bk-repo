package com.tencent.bkrepo.common.artifact.metrics

/**
 * 传输速度直方图 SLO 边界（对外暴露单位 B/s，按 MiB/s 视角生成）
 */
object TransferSpeedSlo {
    /** 二进制：1 MiB/s = 1_048_576 B/s */
    const val MIB_PER_SEC_TO_BPS: Double = 1_048_576.0

    /**
     * 速度直方图的固定 SLO 边界（MiB/s）：
     * - 段一：1, 6, 11, ..., 296 MiB/s（每 5 MiB/s，共 60 个边界）
     * - 段二：300, 310, ..., 1200 MiB/s（每 10 MiB/s，共 91 个边界）
     * 合计 151 个边界，加 +Inf 共 152 个 bucket。
     */
    val BOUNDARIES_MIB_PER_SEC: DoubleArray = buildBoundariesMiBps()

    /** 对外暴露给 Micrometer 的 SLO 边界，单位 B/s。 */
    val BOUNDARIES_BPS: DoubleArray =
        BOUNDARIES_MIB_PER_SEC.map { it * MIB_PER_SEC_TO_BPS }.toDoubleArray()

    private fun buildBoundariesMiBps(): DoubleArray {
        val list = mutableListOf<Double>()
        var v = 1.0
        while (v < 300.0) {
            list.add(v)
            v += 5.0
        }
        v = 300.0
        while (v <= 1200.0) {
            list.add(v)
            v += 10.0
        }
        return list.toDoubleArray()
    }
}
