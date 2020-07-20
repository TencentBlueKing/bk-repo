package com.tencent.bkrepo.docker.helpers

import org.apache.commons.lang.StringUtils
import java.util.TreeSet

/**
 * catalog sice tags helper
 * @author: owenlxu
 * @date: 2020-01-05
 */
object DockerCatalogTagsSlicer {

    /**
     * slice catalog
     * @param elementsHolder elements holder
     * @param maxEntries max entity
     * @param lastEntry the last entry
     */
    fun sliceCatalog(elementsHolder: DockerPaginationElementsHolder, maxEntries: Int, lastEntry: String) {
        if (elementsHolder.elements.isEmpty()) {
            return
        }
        val fromElement = calcFromElement(elementsHolder, lastEntry)
        if (StringUtils.isBlank(fromElement)) {
            elementsHolder.elements = TreeSet()
            return
        }
        val toElement = calcToElement(elementsHolder, fromElement, maxEntries)
        val elements = elementsHolder.elements
        val lastElement = elementsHolder.elements.last() as String
        val firstElement = elementsHolder.elements.first() as String
        if (StringUtils.equals(fromElement, toElement)) {
            elementsHolder.hasMoreElements = !StringUtils.equals(lastElement, toElement)
            elementsHolder.elements = elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
        }
        if (!StringUtils.equals(toElement, lastElement)) {
            if (StringUtils.isBlank(toElement)) {
                elementsHolder.elements = TreeSet()
            } else {
                elementsHolder.hasMoreElements = !StringUtils.equals(lastElement, toElement)
                elementsHolder.elements = elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
            }
        }
        if (!StringUtils.equals(fromElement, firstElement)) {
            elementsHolder.elements = elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
        }
        return
    }

    private fun calcToElement(holder: DockerPaginationElementsHolder, element: String, maxEntries: Int): String {
        var toElement = holder.elements.last() as String
        if (maxEntries <= 0) {
            return toElement
        }
        val repos = holder.elements.tailSet(element)
        var repoIndex = 1
        val iter = repos.iterator()

        while (iter.hasNext()) {
            val repo = iter.next() as String
            if (repoIndex++ == maxEntries) {
                toElement = repo
                break
            }
        }
        return toElement
    }

    private fun calcFromElement(elementsHolder: DockerPaginationElementsHolder, lastEntry: String): String {
        var fromElement = elementsHolder.elements.first() as String
        if (StringUtils.isNotBlank(lastEntry)) {
            fromElement = elementsHolder.elements.higher(lastEntry) as String
        }
        return fromElement
    }
}
