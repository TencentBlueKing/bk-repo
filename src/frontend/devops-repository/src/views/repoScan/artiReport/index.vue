<template>
    <div class="container">
        <artifact-info :scan-types="scanTypes" :subtask-overview="subtaskOverview"></artifact-info>
        <bk-tab type="unborder-card" style="width: 100%;height: 100%">
            <bk-tab-panel v-for="(panel, index) in panels" v-bind="panel" :key="index" style="height: 100%">
                <component
                    @rescan="rescan"
                    :subtask-overview="subtaskOverview"
                    :project-id="projectId"
                    :view-type="viewType"
                    :is="panel.component" style="height: 100%"></component>
            </bk-tab-panel>
        </bk-tab>
    </div>
</template>
<script>
    import {
        leakLevelEnum,
        SCAN_TYPE_LICENSE,
        SCAN_TYPE_SECURITY,
        SCAN_TYPE_SENSITIVE
    } from '../../../store/publicEnum'
    import artifactInfo from './artifactInfo'
    import leakComponent from './leak'
    import licenseComponent from './license'
    import sensitiveComponent from './sensitive'
    import { mapActions } from 'vuex'
    import { formatDate, formatDuration } from '../../../utils'

    export default {
        components: { artifactInfo, leakComponent, licenseComponent, sensitiveComponent },
        data () {
            return {
                allPanels: {
                    [SCAN_TYPE_SECURITY]: { name: SCAN_TYPE_SECURITY, label: '安全漏洞', count: 10, component: 'leak-component' },
                    [SCAN_TYPE_LICENSE]: { name: SCAN_TYPE_LICENSE, label: '许可证', count: 10, component: 'license-component' },
                    [SCAN_TYPE_SENSITIVE]: { name: SCAN_TYPE_SENSITIVE, label: '敏感信息', count: 10, component: 'sensitive-component' }
                },
                scanTypes: [],
                panels: [],
                subtaskOverview: {}
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
            taskId () {
                return this.$route.query.taskId
            },
            viewType () {
                return this.$route.query.viewType
            }
        },
        created () {
            this.getScanConfig({ projectId: this.projectId, id: this.planId })
                .then(res => {
                    this.scanTypes = res.scanTypes
                    this.panels = res.scanTypes.map(scanType => this.allPanels[scanType])
                })

            this.artiReportOverview({
                projectId: this.projectId,
                recordId: this.recordId,
                taskId: this.taskId,
                viewType: this.viewType
            }).then(res => {
                this.subtaskOverview = {
                    ...res,
                    highestLeakLevel: leakLevelEnum[res.highestLeakLevel],
                    duration: formatDuration(res.duration / 1000),
                    finishTime: formatDate(res.finishTime)
                }
            })
        },
        methods: {
            ...mapActions(['getScanConfig', 'artiReportOverview', 'startScanSingle']),
            rescan () {
                this.startScanSingle({
                    projectId: this.projectId,
                    id: this.planId,
                    repoType: this.subtaskOverview.repoType,
                    repoName: this.subtaskOverview.repoName,
                    fullPath: this.subtaskOverview.fullPath,
                    packageKey: this.subtaskOverview.packageKey,
                    version: this.subtaskOverview.version
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '已添加到扫描队列'
                    })
                    this.back()
                })
            },
            back () {
                this.$route.query.scanName ? this.backToScanPlan() : this.backToRepo()
            },
            backToScanPlan () {
                const { scanType, scanName } = this.$route.query
                this.$router.push({
                    name: 'scanReport',
                    params: { projectId: this.projectId, planId: this.planId },
                    query: { viewType: this.viewType, scanType: scanType, scanName: scanName }
                })
            },
            backToRepo () {
                const { path, packageKey, version } = this.$route.query
                this.$router.push({
                    name: this.subtaskOverview.packageKey ? 'commonPackage' : 'repoGeneric',
                    params: {
                        projectId: this.projectId,
                        repoType: this.subtaskOverview.repoType
                    },
                    query: (
                        this.subtaskOverview.packageKey
                            ? { repoName: this.subtaskOverview.repoName, packageKey, version }
                            : { repoName: this.subtaskOverview.repoName, path }
                    )
                })
            }
        }
    }
</script>

<style lang="scss" scoped>
.container {
    height: 100%;
    padding: 20px;
    display: flex;
    align-items: flex-start;
    overflow: hidden;
    background-color: white;
    ::v-deep .bk-tab-section {
        height: 100%;
        padding-right: 0;
    }
}
</style>
