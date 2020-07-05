package com.tencent.bkrepo.docker.helpers

import org.apache.commons.lang.StringUtils
import java.util.TreeSet

/**
 * catalog sice tags helper
 * @author: owenlxu
 * @date: 2020-01-05
 */
class DockerCatalogTagsSlicer {

    companion object {

        fun sliceCatalog(elementsHolder: DockerPaginationElementsHolder, maxEntries: Int, lastEntry: String) {
            if (!elementsHolder.elements.isEmpty()) {
                val fromElement = calcFromElement(elementsHolder, lastEntry)
                if (StringUtils.isBlank(fromElement)) {
                    elementsHolder.elements = TreeSet()
                    return
                }
                val toElement = calcToElement(elementsHolder, fromElement, maxEntries)
                if (!StringUtils.equals(fromElement, toElement)) {
                    if (!StringUtils.equals(toElement, elementsHolder.elements.last() as String)) {
                        if (StringUtils.isBlank(toElement)) {
                            elementsHolder.elements = TreeSet()
                        } else {
                            elementsHolder.hasMoreElements = !StringUtils.equals(elementsHolder.elements.last() as String, toElement)
                            elementsHolder.elements = elementsHolder.elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
                        }
                    } else if (!StringUtils.equals(fromElement, elementsHolder.elements.first() as String)) {
                        elementsHolder.elements = elementsHolder.elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
                    }
                    return
                }
                elementsHolder.hasMoreElements = !StringUtils.equals(elementsHolder.elements.last() as String, toElement)
                elementsHolder.elements = elementsHolder.elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
            }
        }

        private fun calcFromElement(elementsHolder: DockerPaginationElementsHolder, lastEntry: String): String {
            var fromElement = elementsHolder.elements.first() as String
            if (StringUtils.isNotBlank(lastEntry)) {
                fromElement = elementsHolder.elements.higher(lastEntry) as String
            }
            return fromElement
        }

        private fun calcToElement(holder: DockerPaginationElementsHolder, element: String, maxEntries: Int): String {
            var toElement = holder.elements.last() as String
            if (maxEntries > 0) {
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
            }
            return toElement
        }
    }
}
