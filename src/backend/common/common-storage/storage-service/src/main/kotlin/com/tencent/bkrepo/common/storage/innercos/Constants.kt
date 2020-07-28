package com.tencent.bkrepo.common.storage.innercos

import java.net.URLEncoder

const val DEFAULT_ENCODING = "UTF-8"
const val PATH_DELIMITER = '/'
const val PARAMETER_UPLOAD_ID = "uploadid"
const val PARAMETER_PART_NUMBER = "partnumber"
const val PARAMETER_UPLOADS = "uploads"
const val COS_COPY_SOURCE = "x-cos-copy-source"

const val RESPONSE_UPLOAD_ID = "UploadId"
const val RESPONSE_LAST_MODIFIED = "LastModified"

fun String.encode(): String {
    val encodedString = URLEncoder.encode(this, DEFAULT_ENCODING)
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~")

    val builder = StringBuilder()
    val length = encodedString.length
    var index = 0
    while (index < length) {
        index += if (encodedString[index] == '%' && index + 2 < length) {
            builder.append(encodedString[index])
            builder.append(Character.toLowerCase(encodedString[index + 1]))
            builder.append(Character.toLowerCase(encodedString[index + 2]))
            3
        } else {
            builder.append(encodedString[index])
            1
        }
    }
    return builder.toString()
}

/**
 * 重试函数，times表示重试次数，加上第一次执行，总共会执行times+1次，
 */
inline fun <R> retry(times: Int, delayInSeconds: Long = 10, block: (Int) -> R): R {
    var retries = 0
    while (true) {
        try {
            return block(retries)
        } catch (e: Exception) {
            if (retries < times) {
                Thread.sleep(delayInSeconds * 1000)
                retries += 1
            } else {
                throw e
            }
        }
    }
}
