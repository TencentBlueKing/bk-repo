import Vue from 'vue'

const prefix = 'scanner/api/scan'

export default {
    // 创建扫描方案
    createScan (_, body) {
        return Vue.prototype.$ajax.post(
            `${prefix}/plan/create`,
            body
        )
    },
    // 扫描方案列表
    getScanList (_, { projectId, type, name, current = 1, limit = 20 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/plan/list/${projectId}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    type: type || undefined,
                    name: name || undefined
                }
            }
        )
    },
    // 所有扫描方案
    getScanAll (_, { projectId, type }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/plan/all/${projectId}`,
            {
                params: {
                    type
                }
            }
        )
    },
    // 获取方案基本设置
    getScanConfig (_, { projectId, id }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/plan/detail/${projectId}/${id}`
        )
    },
    // 保存方案基本设置
    saveScanConfig (_, body) {
        return Vue.prototype.$ajax.post(
            `${prefix}/plan/update`,
            body
        )
    },
    // 删除扫描方案
    deleteScan (_, { projectId, id }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/plan/delete/${projectId}/${id}`
        )
    },
    // 报告基本信息
    scanReportOverview (_, { projectId, id }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/plan/count/${projectId}/${id}`
        )
    },
    // 报告制品列表
    scanReportList (_, {
        projectId, id, name, repoType, repoName,
        highestLeakLevel, status, startTime, endTime,
        current = 1, limit = 20
    }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/plan/artifact`,
            {
                params: {
                    id,
                    name: name || undefined,
                    highestLeakLevel: highestLeakLevel || undefined,
                    projectId,
                    repoType: repoType || undefined,
                    repoName: repoName || undefined,
                    status,
                    startTime,
                    endTime,
                    pageNumber: current,
                    pageSize: limit
                }
            }
        )
    },
    // 中止扫描
    stopScan (_, { projectId, recordId }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/${projectId}/stop`,
            null,
            {
                params: {
                    recordId
                }
            }
        )
    },
    // 制品扫描报告基本信息
    artiReportOverview (_, { projectId, recordId }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/artifact/count/${projectId}/${recordId}`
        )
    },
    // 制品扫描报告漏洞列表
    getLeakList (_, { projectId, recordId, vulId, severity, current = 1, limit = 20 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/artifact/leak/${projectId}/${recordId}`,
            {
                params: {
                    vulId: vulId || undefined,
                    leakType: severity || undefined,
                    pageNumber: current,
                    pageSize: limit
                }
            }
        )
    },
    // 批量扫描
    startScan (_, body) {
        return Vue.prototype.$ajax.post(
            `${prefix}/batch`,
            body
        )
    },
    // 单个扫描
    startScanSingle (_, body) {
        return Vue.prototype.$ajax.post(
            `${prefix}/single`,
            body
        )
    },
    // 制品关联的扫描方案
    getArtiScanList (_, { projectId, repoType, repoName, packageKey, version, fullPath }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/plan/relation/artifact`,
            {
                params: {
                    projectId,
                    repoType: repoType.toUpperCase() || undefined,
                    repoName,
                    packageKey: packageKey || undefined,
                    version: version || undefined,
                    fullPath: fullPath || undefined
                }
            }
        )
    },
    // 获取扫描器列表
    getScannerList () {
        return Vue.prototype.$ajax.get('/scanner/api/scanners/base')
    },
    // 获取质量规则
    getQualityRule (_, { id }) {
        return Vue.prototype.$ajax.get(`/scanner/api/scan/quality/${id}`)
    },
    // 更新质量规则
    saveQualityRule (_, { id, body }) {
        return Vue.prototype.$ajax.post(`/scanner/api/scan/quality/${id}`, body)
    }
}
