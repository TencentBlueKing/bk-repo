package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.repository.pojo.node.NodeInfo

object RpmCollectionUtils {

    fun List<Map<String, Any>>.filterRpmCustom(set: MutableSet<String>, enabledFileLists: Boolean): List<Map<String,
            Any>> {
        val resultList = mutableListOf<Map<String, Any>>()

        resultList.add(
            this.first {
                (it["metadata"] as Map<String, String>)["indexType"] == "primary"
            }
        )
        resultList.add(
            this.first {
                (it["metadata"] as Map<String, String>)["indexType"] == "others"
            }
        )
        if (enabledFileLists) {
            resultList.add(
                this.first {
                    (it["metadata"] as Map<String, String>)["indexType"] == "filelists"
                }
            )
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
                        (it["metadata"] as Map<String, String>)["indexName"] == str
                    }
                )
            } catch (noSuchElementException: NoSuchElementException) {
                // 用户未上传对应分组文件
            }
        }
        return resultList
    }
}
