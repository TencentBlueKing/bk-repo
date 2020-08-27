package com.tencent.bkrepo.common.storage.hdfs

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

class HDFSClient(
    val workingPath: Path,
    val fileSystem: FileSystem
)
