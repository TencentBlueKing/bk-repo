package com.tencent.bkrepo.scanner.component.manager.scancode

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScanCodeToolkitScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.scanner.component.manager.ResultItem
import com.tencent.bkrepo.scanner.component.manager.ResultItemDao
import com.tencent.bkrepo.scanner.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.scanner.component.manager.scancode.dao.ScancodeItemDao
import com.tencent.bkrepo.scanner.component.manager.scancode.model.TScancodeItem
import com.tencent.bkrepo.scanner.message.ScannerMessageCode
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.SaveResultArguments
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ScancodeToolkitResultArguments
import org.slf4j.LoggerFactory
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
        logger.info("save DependencyCheckResult detail")
        result as ScanCodeToolkitScanExecutorResult
        scanner as ScancodeToolkitScanner
        val scannerName = scanner.name

        result.scancodeItem
            .map { convert<ScancodeItem, TScancodeItem>(credentialsKey, sha256, scannerName, it) }
            .run { replace(credentialsKey, sha256, scannerName, scancodeItemDao, this) }

    }

    override fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        arguments: LoadResultArguments?
    ): Any? {
        scanner as ScancodeToolkitScanner
        arguments as ScancodeToolkitResultArguments
        val pageLimit = arguments.pageLimit
        val type = arguments.reportType

        val page = when (type) {
            ScancodeItem.TYPE -> scancodeItemDao
            else -> {
                throw ErrorCodeException(
                    messageCode = ScannerMessageCode.SCANNER_RESULT_TYPE_INVALID,
                    status = HttpStatus.BAD_REQUEST,
                    params = arrayOf(type)
                )
            }
        }.run { pageOf(credentialsKey, sha256, scanner.name, pageLimit, arguments) }
        logger.info("page:${page.toJsonString()}")
        return Page(page.pageNumber, page.pageSize, page.totalRecords, page.records.map { it.data })
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScancodeResultManager::class.java)
    }

    private inline fun <T, reified R : ResultItem<T>> convert(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        data: T
    ): R {
        return R::class.java.constructors[0].newInstance(null, credentialsKey, sha256, scanner, data) as R
    }

    /**
     * 替换同一文件使用同一扫描器原有的扫描结果
     */
    private fun <T : ResultItem<*>, D : ResultItemDao<T>> replace(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        resultItemDao: D,
        resultItems: List<T>
    ) {
        resultItemDao.deleteBy(credentialsKey, sha256, scanner)
        logger.info("resultItems:${resultItems.toJsonString()}")
        resultItemDao.insert(resultItems)
    }
}
