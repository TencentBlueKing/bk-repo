package com.tencent.bkrepo.generic.constant

/**
 * 通用文件错误码
 *
 * @author: carrypan
 * @date: 2019-10-11
 */

object GenericMessageCode {
    const val FILE_DATA_NOT_FOUND = 2511001 // 文件数据未找到
    const val DOWNLOAD_FOLDER_FORBIDDEN = 2511002 // 下载出错，不能下载文件夹
    const val DOWNLOAD_BLOCK_FORBIDDEN = 2511003 // 下载出错，分块文件需分块下载
    const val DOWNLOAD_SIMPLE_FORBIDDEN = 2511004 // 下载出错，单文件不能分块下载
}
