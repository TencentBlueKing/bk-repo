package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

enum class DataType(private val code: Int) {
    None(0),
    Bool(1),
    Int8(2),
    Int16(3),
    Int32(4),
    Int64(5),

    Float(10),
    Double(11),

    String(20),
    VarChar(21),  // variable-length strings with a specified maximum length
    Array(22),
    JSON(23),

    BinaryVector(100),
    FloatVector(101),
    Float16Vector(102),
    BFloat16Vector(103),
    SparseFloatVector(104)
}