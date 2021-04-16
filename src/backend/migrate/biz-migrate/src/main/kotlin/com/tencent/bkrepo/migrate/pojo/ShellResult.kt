package com.tencent.bkrepo.migrate.pojo

data class ShellResult(
    val exitValue: Int,
    val outputStr: List<String?>
) {
    fun isSuccess(): Boolean {
        return exitValue == 0
    }
}
