package com.tencent.bkrepo.scanner.component.manager.scancode

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScanCodeToolkitScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.scanner.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.scanner.component.manager.scancode.dao.ScancodeItemDao
import com.tencent.bkrepo.scanner.component.manager.scancode.model.TScancodeItem
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.SaveResultArguments
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ScancodeToolkitResultArguments
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component(ScancodeToolkitScanner.TYPE)
class ScancodeResultManager @Autowired constructor(
    private val scancodeItemDao: ScancodeItemDao
) : ScanExecutorResultManager {

    @Transactional(rollbackFor = [Throwable::class])
    override fun save(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        result: ScanExecutorResult,
        arguments: SaveResultArguments?
    ) {
        result as ScanCodeToolkitScanExecutorResult
        scanner as ScancodeToolkitScanner
        val items = result.scancodeItem.map { TScancodeItem(null, credentialsKey, sha256, scanner.name, it) }
        scancodeItemDao.deleteBy(credentialsKey, sha256, scanner.name)
        scancodeItemDao.insert(items)
    }

    override fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        arguments: LoadResultArguments?
    ): Any? {
        scanner as ScancodeToolkitScanner
        arguments as ScancodeToolkitResultArguments
        val page = scancodeItemDao.pageBy(credentialsKey, sha256, scanner.name, arguments.pageLimit, arguments)
        return Page(page.pageNumber, page.pageSize, page.totalRecords, page.records.map { it.data })
    }
}
