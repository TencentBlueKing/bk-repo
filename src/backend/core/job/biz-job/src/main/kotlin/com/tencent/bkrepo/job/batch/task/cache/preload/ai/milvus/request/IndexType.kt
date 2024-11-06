package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

enum class IndexType(
    val code: Int,
) {
    None(0),

    // Only supported for float vectors
    FLAT(1),
    IVF_FLAT(2),
    IVF_SQ8(3),
    IVF_PQ(4),
    HNSW(5),
    DISKANN(10),
    AUTOINDEX(11),
    SCANN(12),

    // GPU indexes only for float vectors
    GPU_IVF_FLAT(50),
    GPU_IVF_PQ(51),
    GPU_BRUTE_FORCE(52),
    GPU_CAGRA(53),

    // Only supported for binary vectors
    BIN_FLAT(80),
    BIN_IVF_FLAT(81),

    // Only for varchar type field
    Trie(100),

    // Only for scalar type field
    STL_SORT(200),  // only for numeric type field
    INVERTED(201),  // works for all scalar fields except JSON type field
    BITMAP(202),  // works for all scalar fields except JSON, FLOAT and DOUBLE type fields

    // Only for sparse vectors
    SPARSE_INVERTED_INDEX(300),
    SPARSE_WAND(301);
}
