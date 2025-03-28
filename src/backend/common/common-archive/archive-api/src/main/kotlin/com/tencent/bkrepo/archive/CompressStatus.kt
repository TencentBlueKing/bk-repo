package com.tencent.bkrepo.archive

enum class CompressStatus {
    /**
     * 已创建
     * */
    CREATED,

    /**
     * 压缩中
     * */
    COMPRESSING,

    /**
     * 已压缩
     * */
    COMPRESSED,

    /**
     * 已完成
     * */
    COMPLETED,

    /**
     * 等待解压
     * */
    WAIT_TO_UNCOMPRESS,

    /**
     * 正在解压
     * */
    UNCOMPRESSING,

    /**
     * 已解压
     * */
    UNCOMPRESSED,

    /**
     * 表示链头
     * */
    NONE,

    /**
     * 压缩失败
     * */
    COMPRESS_FAILED,

    /**
     * 解压失败
     * */
    UNCOMPRESS_FAILED,
}
