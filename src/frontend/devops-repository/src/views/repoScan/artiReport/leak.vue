<template>
    <div class="arti-report-container">
        <div class="base-info flex-column">
            <div class="pt20 pb20 flex-align-center">
                <Icon class="mr10" size="40" :name="(baseInfo.repoType || '').toLowerCase()" />
                <span class="arti-name text-overflow" :title="baseInfo.name">{{ baseInfo.name }}</span>
            </div>
            <div class="arti-meta">
                <div v-for="item in metaBase.filter(v => baseInfo[v.key])"
                    :key="item.key" class="meta-content">
                    <span style="color:var(--fontSubsidiaryColor);">{{ item.label }}</span>
                    <span class="ml20">{{ baseInfo[item.key] }}</span>
                </div>
            </div>
            <div v-if="baseInfo.qualityRedLine !== null" class="arti-quality">
                <div class="flex-align-center">
                    <span class="mr20" style="color:var(--fontSubsidiaryColor);">质量规则</span>
                    <span v-if="baseInfo.qualityRedLine" class="repo-tag SUCCESS">通过</span>
                    <span v-else class="repo-tag FAILED">不通过</span>
                </div>
                <div class="status-sign"
                    :class="item.id"
                    v-for="item in qualityList"
                    :key="item.id"
                    :data-name="item.label">
                </div>
            </div>
            <div class="arti-leak">
                <div style="color:var(--fontSubsidiaryColor);">漏洞数量统计</div><div></div>
                <div class="status-sign"
                    :class="id"
                    v-for="[id, name] in Object.entries(leakLevelEnum)"
                    :key="id"
                    :data-name="`${name}漏洞：${segmentNumberThree(baseInfo[id.toLowerCase()])}`">
                </div>
            </div>
        </div>
        <div class="leak-list">
            <div class="flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="filter.vulId"
                    clearable
                    placeholder="请输入漏洞ID, 按Enter键搜索"
                    right-icon="bk-icon icon-search"
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="filter.severity"
                    placeholder="漏洞等级"
                    @change="handlerPaginationChange()">
                    <bk-option v-for="[id, name] in Object.entries(leakLevelEnum)" :key="id" :id="id" :name="name"></bk-option>
                </bk-select>
                <div class="flex-1 flex-end-center">
                    <bk-button theme="default" @click="startScanSingleHandler">重新扫描</bk-button>
                </div>
            </div>
            <bk-table
                class="mt10 leak-table"
                height="calc(100% - 80px)"
                :data="leakList"
                :outer-border="false"
                :row-border="false"
                row-key="leakKey"
                size="small">
                <template #empty>
                    <empty-data
                        :is-loading="isLoading"
                        :search="Boolean(filter.vulId || filter.severity)"
                        title="未扫描到漏洞">
                    </empty-data>
                </template>
                <bk-table-column type="expand" width="30">
                    <template #default="{ row }">
                        <template v-if="row.path">
                            <div class="leak-title">存在漏洞的文件路径</div>
                            <div class="leak-tip">{{ row.path }}</div>
                        </template>
                        <div class="leak-title">{{ row.title }}</div>
                        <div class="leak-tip">{{ row.description || '/' }}</div>
                        <div class="leak-title">修复建议</div>
                        <div class="leak-tip">{{ row.officialSolution || '/' }}</div>
                        <template v-if="row.reference && row.reference.length">
                            <div class="leak-title">相关资料</div>
                            <div class="leak-tip" v-for="url in row.reference" :key="url">
                                <a :href="url" target="_blank">{{ url }}</a>
                            </div>
                        </template>
                    </template>
                </bk-table-column>
                <bk-table-column label="漏洞ID" prop="vulId" show-overflow-tooltip></bk-table-column>
                <bk-table-column label="漏洞等级">
                    <template #default="{ row }">
                        <div class="status-sign" :class="row.severity" :data-name="leakLevelEnum[row.severity]"></div>
                    </template>
                </bk-table-column>
                <bk-table-column label="所属依赖" prop="pkgName" show-overflow-tooltip></bk-table-column>
                <bk-table-column label="引入版本" prop="installedVersion" show-overflow-tooltip></bk-table-column>
            </bk-table>
            <bk-pagination
                class="p10"
                size="small"
                align="right"
                show-total-count
                @change="current => handlerPaginationChange({ current })"
                @limit-change="limit => handlerPaginationChange({ limit })"
                :current.sync="pagination.current"
                :limit="pagination.limit"
                :count="pagination.count"
                :limit-list="pagination.limitList">
            </bk-pagination>
        </div>
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    import { formatDate, segmentNumberThree, formatDuration } from '@repository/utils'
    import { leakLevelEnum } from '@repository/store/publicEnum'
    export default {
        name: 'leak',
        data () {
            return {
                leakLevelEnum,
                metaBase: [
                    { key: 'duration', label: '持续时间' },
                    { key: 'finishTime', label: '完成时间' },
                    { key: 'repoName', label: '所属仓库' },
                    { key: 'groupId', label: 'GroupId' },
                    { key: 'version', label: '制品版本' },
                    { key: 'fullPath', label: '存储路径' }
                ],
                baseInfo: {},
                isLoading: false,
                leakList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                filter: {
                    vulId: '',
                    severity: ''
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            planId () {
                return this.$route.params.planId
            },
            recordId () {
                return this.$route.params.recordId
            },
            qualityList () {
                const data = this.baseInfo.scanQuality || {}
                return Object.keys(leakLevelEnum).map(k => {
                    if (k.toLowerCase() in data && data[k.toLowerCase()] !== null) {
                        return {
                            id: k,
                            label: `${leakLevelEnum[k]}漏洞总数 ≦ ${data[k.toLowerCase()]}`
                        }
                    }
                    return undefined
                }).filter(Boolean)
            }
        },
        created () {
            this.artiReportOverview({
                projectId: this.projectId,
                recordId: this.recordId,
                taskId: this.$route.query.taskId,
                viewType: this.$route.query.viewType
            }).then(res => {
                this.baseInfo = {
                    ...res,
                    highestLeakLevel: leakLevelEnum[res.highestLeakLevel],
                    duration: formatDuration(res.duration / 1000),
                    finishTime: formatDate(res.finishTime)
                }
            })
            this.handlerPaginationChange()
        },
        methods: {
            segmentNumberThree,
            ...mapActions([
                'startScanSingle',
                'artiReportOverview',
                'getLeakList'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getLeakListHandler()
            },
            getLeakListHandler () {
                this.isLoading = true
                return this.getLeakList({
                    projectId: this.projectId,
                    recordId: this.recordId,
                    viewType: this.$route.query.viewType,
                    vulId: this.filter.vulId,
                    severity: this.filter.severity,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.leakList = records.map(v => ({
                        ...v,
                        leakKey: `${v.vulId}${v.pkgName}${v.installedVersion}`
                    }))
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            filterHandler () {
                this.filter.show = false
                this.handlerPaginationChange()
            },
            reset () {
                this.filter = {
                    show: true,
                    vulId: '',
                    severity: ''
                }
            },
            startScanSingleHandler () {
                const { repoType, repoName, fullPath, packageKey, version } = this.baseInfo
                this.startScanSingle({
                    projectId: this.projectId,
                    id: this.planId,
                    repoType,
                    repoName,
                    fullPath,
                    packageKey,
                    version
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '已添加到扫描队列'
                    })
                    this.back()
                })
            },
            back () {
                const { repoType, repoName } = this.baseInfo
                const { scanType, scanName, path, packageKey, version } = this.$route.query
                this.$router.push({
                    name: scanName ? 'scanReport' : (this.packageKey ? 'commonPackage' : 'repoGeneric'),
                    params: {
                        projectId: this.projectId,
                        [this.planId ? 'planId' : 'repoType']: this.planId || repoType
                    },
                    query: scanName
                        ? { scanType, scanName }
                        : (this.packageKey
                            ? { repoName, packageKey, version }
                            : { repoName, path })
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.arti-report-container {
    height: 100%;
    padding: 20px;
    display: flex;
    align-items: flex-start;
    overflow: hidden;
    background-color: white;
    .base-info {
        height: 100%;
        overflow-y: auto;
        padding: 0 20px;
        flex-basis: 300px;
        border: 1px solid var(--borderColor);
        background-color: var(--bgColor);
        .arti-name {
            max-width: 200px;
            font-size: 16px;
            font-weight: 600;
        }
        .arti-meta,
        .arti-quality,
        .arti-leak {
            padding: 20px 0;
            display: grid;
            gap: 20px;
            border-top: 1px solid var(--borderColor);
        }
        .arti-meta .meta-content {
            display: flex;
            span:first-child {
                flex-shrink: 0;
            }
            span:last-child {
                word-break: break-word;
                flex: 1
            }
        }
        .arti-leak {
            grid-template: auto / repeat(2, 1fr);
        }
    }
    .leak-list {
        flex: 1;
        height: 100%;
        margin-left: 20px;
        .leak-title {
            padding: 5px 20px 0;
            font-weight: 800;
        }
        .leak-tip {
            padding: 0 20px 5px;
            color: var(--fontDisableColor);
        }
    }
}
</style>
