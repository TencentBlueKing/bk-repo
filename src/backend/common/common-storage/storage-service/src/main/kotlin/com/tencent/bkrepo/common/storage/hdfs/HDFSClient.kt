package com.tencent.bkrepo.common.storage.hdfs

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

/**
 *
 * @author: carrypan
 * @date: 2020/1/13
 */
class HDFSClient (
    val workingPath: Path,
    val fileSystem: FileSystem
)