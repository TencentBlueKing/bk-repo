package com.tencent.bkrepo.npm.utils

object MigrationUtils {
    /**
     * 数据分割
     */
    fun <T> split(set: Set<T>, count: Int = 1000): List<List<T>> {
        val list = set.toList()
        if (set.isEmpty()) {
            return emptyList()
        }
        val resultList = mutableListOf<List<T>>()
        var itemList: MutableList<T>?
        val size = set.size

        if (size < count) {
            resultList.add(list)
        } else {
            val pre = size / count
            val last = size % count
            for (i in 0 until pre) {
                itemList = mutableListOf()
                for (j in 0 until count) {
                    itemList.add(list[i * count + j])
                }
                resultList.add(itemList)
            }
            if (last > 0) {
                itemList = mutableListOf()
                for (i in 0 until last) {
                    itemList.add(list[pre * count + i])
                }
                resultList.add(itemList)
            }
        }
        return resultList
    }
}
