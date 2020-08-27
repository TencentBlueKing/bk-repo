package com.tencent.bkrepo.rpm.util

object RpmCollectionUtils {

    fun List<Map<String, Any>>.filterRpmCustom(set: MutableSet<String>, enabledFileLists: Boolean): List<Map<String,
            Any>> {
        val resultList = mutableListOf<Map<String, Any>>()
        try {
            resultList.add(
                this.first {
                    (it["metadata"] as Map<*, *>)["indexType"] == "primary"
                }
            )
            resultList.add(
                this.first {
                    (it["metadata"] as Map<*, *>)["indexType"] == "others"
                }
            )
            if (enabledFileLists) {
                resultList.add(
                    this.first {
                        (it["metadata"] as Map<*, *>)["indexType"] == "filelists"
                    }
                )
            }
        } catch (noSuchElementException: NoSuchElementException) {
            //todo
            // 仓库中还没有生成索引
        }

        val doubleSet = mutableSetOf<String>()
        for (str in set) {
            doubleSet.add(str)
            doubleSet.add("${str}_gz")
        }

        for (str in doubleSet) {
            try {
                resultList.add(
                    this.first {
                        (it["metadata"] as Map<*, *>)["indexName"] == str
                    }
                )
            } catch (noSuchElementException: NoSuchElementException) {
                //todo
                // 用户未上传对应分组文件
            }
        }
        return resultList
    }
}
