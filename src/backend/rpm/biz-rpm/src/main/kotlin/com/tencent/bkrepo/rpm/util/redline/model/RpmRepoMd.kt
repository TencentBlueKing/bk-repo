package com.tencent.bkrepo.rpm.util.redline.model

/**
 * 'repomd.xml'中需要索引文件'xml.gz'的sha1值
 * @property xmlFileSha1 对应'repomd.xml'中'open-checksum',代表'xml.gz'解压之后'xml'文件的sha1值
 * @property xmlGZFileSha1 对应'repomd.xml'中'checksum',代表'xml.gz'的sha1值，同时也作为'xml.gz'的文件名
 */
class RpmRepoMd(
    val type: String,
    val location: String,
    val size: Long,
    val lastModified: String,
    val xmlFileSha1: String,
    val xmlGZFileSha1: String
)
