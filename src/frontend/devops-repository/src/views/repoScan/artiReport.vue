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
            <div class="arti-leak">
                <div v-for="item in leakBase" :key="item.key">
                    <span style="color:var(--fontSubsidiaryColor);">{{ item.label }}</span>
                    <span class="ml20">{{ segmentNumberThree(baseInfo[item.key]) }}</span>
                </div>
            </div>
        </div>
        <div class="leak-list display-block" data-title="漏洞列表">
            <bk-button class="scan-btn" theme="default" @click="startScanSingleHandler">重新扫描</bk-button>
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
            </div>
            <bk-table
                class="mt10 leak-table"
                height="calc(100% - 82px)"
                :data="leakList"
                :outer-border="false"
                :row-border="false"
                row-key="leakKey"
                size="small">
                <template #empty>
                    <empty-data :is-loading="isLoading"></empty-data>
                </template>
                <bk-table-column type="expand" width="30">
                    <template #default="{ row }">
                        <div class="leak-title">{{ row.title }}</div>
                        <div class="leak-tip">{{ row.description }}</div>
                        <div class="leak-title">修复建议</div>
                        <div class="leak-tip">{{ row.officialSolution }}</div>
                        <div class="leak-title">相关资料</div>
                        <div class="leak-tip" display v-for="url in row.reference" :key="url">
                            <a :href="url" target="_blank">{{ url }}</a>
                        </div>
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
    import { formatDate, segmentNumberThree } from '@repository/utils'
    import { leakLevelEnum } from '@repository/store/publicEnum'
    export default {
        name: 'artiReport',
        data () {
            return {
                leakLevelEnum,
                metaBase: [
                    { key: 'finishTime', label: '完成时间' },
                    { key: 'repoName', label: '所属仓库' },
                    { key: 'groupId', label: 'GroupId' },
                    { key: 'version', label: '制品版本' },
                    { key: 'fullPath', label: '存储路径' }
                ],
                leakBase: [
                    { key: 'highestLeakLevel', label: '风险等级' },
                    { key: 'total', label: '漏洞总数' },
                    { key: 'critical', label: '危急漏洞' },
                    { key: 'high', label: '高风险漏洞' },
                    { key: 'medium', label: '中风险漏洞' },
                    { key: 'low', label: '低风险漏洞' }
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
            }
        },
        created () {
            this.artiReportOverview({
                projectId: this.projectId,
                recordId: this.recordId
            }).then(res => {
                this.baseInfo = {
                    ...res,
                    highestLeakLevel: leakLevelEnum[res.highestLeakLevel],
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
                const { repoType, repoName, name, fullPath, packageKey, version } = this.baseInfo
                this.startScanSingle({
                    projectId: this.projectId,
                    id: this.planId,
                    repoType,
                    repoName,
                    name,
                    fullPath,
                    packageKey,
                    version
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '已添加到扫描队列'
                    })
                    this.$router.back()
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
        .arti-leak {
            padding: 20px 0;
            display: grid;
            gap: 20px;
            border-top: 1px solid var(--borderColor);
            .meta-content {
                display: flex;
                span:first-child {
                    flex-shrink: 0;
                }
                span:last-child {
                    word-break: break-word;
                    flex: 1
                }
            }
        }
    }
    .leak-list {
        flex: 1;
        height: calc(100% - 35px);
        margin-left: 20px;
        margin-top: 35px;
        .scan-btn {
            position: absolute;
            right: 0;
            margin-top: -32px;
        }
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
