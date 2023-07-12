<template>
    <div class="container">
        <artifact-info :scan-types="scanTypes" :subtask-overview="subtaskOverview"></artifact-info>
        <bk-tab type="unborder-card" class="arti-tab">
            <bk-tab-panel v-for="(panel, index) in panels" v-bind="panel" :key="index" style="height: 100%">
                <component
                    @rescan="rescan"
                    :subtask-overview="subtaskOverview"
                    :project-id="projectId"
                    :view-type="viewType"
                    :is="panel.component"
                    style="height: 100%">
                </component>
            </bk-tab-panel>
        </bk-tab>
    </div>
</template>
<script>
    import {
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
                    [SCAN_TYPE_SECURITY]: { name: SCAN_TYPE_SECURITY, label: this.$t('securityVulnerability'), count: 10, component: 'leak-component' },
                    [SCAN_TYPE_LICENSE]: { name: SCAN_TYPE_LICENSE, label: this.$t('license'), count: 10, component: 'license-component' },
                    [SCAN_TYPE_SENSITIVE]: { name: SCAN_TYPE_SENSITIVE, label: this.$t('sensitiveInfo'), count: 10, component: 'sensitive-component' }
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
            this.artiReportOverview({
                projectId: this.projectId,
                recordId: this.recordId,
                taskId: this.taskId,
                viewType: this.viewType
            }).then(res => {
                this.scanTypes = res.scanTypes
                this.panels = res.scanTypes.map(scanType => this.allPanels[scanType])
                this.subtaskOverview = {
                    ...res,
                    highestLeakLevel: this.$t(`leakLevelEnum.${res.highestLeakLevel}`),
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
                        message: this.$t('scanArtMsg')
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
                    params: { ...this.$route.params, projectId: this.projectId, planId: this.planId },
                    query: { ...this.$route.query, viewType: this.viewType, scanType: scanType, scanName: scanName }
                })
            },
            backToRepo () {
                const { path, packageKey, version } = this.$route.query
                this.$router.push({
                    name: this.subtaskOverview.packageKey ? 'commonPackage' : 'repoGeneric',
                    params: {
                        ...this.$route.params,
                        projectId: this.projectId,
                        repoType: this.subtaskOverview.repoType?.toLowerCase()
                    },
                    query: (
                        this.subtaskOverview.packageKey
                            ? { ...this.$route.query, repoName: this.subtaskOverview.repoName, packageKey, version }
                            : { ...this.$route.query, repoName: this.subtaskOverview.repoName, path }
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
    .arti-tab{
        width: calc(100% - 334px);
        height: 100%;
    }
}
</style>
