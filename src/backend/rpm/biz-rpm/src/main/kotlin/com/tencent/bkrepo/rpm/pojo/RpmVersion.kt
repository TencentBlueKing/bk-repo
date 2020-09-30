package com.tencent.bkrepo.rpm.pojo

data class RpmVersion(
    val name: String,
    val arch: String,
    /*
    rpm包中`epoch`不会在文件名上显示，所以在文件系统中也就无法保存只有`epoch`值不同的包。
    对服务器来说： {path}/{name}-{ver}-{rel}.{arch}.rpm 可以确定唯一文件和索引。
     */
    @Deprecated("")
    val epoch: String,
    val ver: String,
    val rel: String
)
