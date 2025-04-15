<template>
    <div class="report-overview">
        <div class="flex-between-center">
            <span class="report-title flex-align-center">{{ $t('overviewTitle') }}</span>
            <div class="flex-align-center">
                <bk-button class="mr10" theme="default" @click="showExportDialog = true">{{$t('exportReport')}}</bk-button>
                <bk-date-picker
                    class="mr10 w250"
                    v-model="filterTime"
                    :shortcuts="shortcuts"
                    type="daterange"
                    transfer
                    :placeholder="$t('selectDatePlaceHolder')"
                    @change="changeFilterTime">
                </bk-date-picker>
                <bk-button class="mr10" theme="default" @click="stopScanHandler">{{ $t('abortScan') }}</bk-button>
                <bk-button v-if="!scanPlan.readOnly" class="mr10" theme="default" @click="startScanHandler">{{ $t('scanImmediately') }}</bk-button>
                <bk-button theme="default" @click="scanSettingHandler">{{ $t('setting') }}</bk-button>
            </div>
        </div>
        <div class="mt10 flex-align-center">
            <div class="base-info-item flex-column"
                v-for="item in overviewList" :key="item.key">
                <span class="base-info-key">{{ item.label }}</span>
                <span class="base-info-value" :style="{ color: item.color }">{{ segmentNumberThree(overview[item.key]) }}</span>
            </div>
        </div>
        <canway-dialog
            v-model="showExportDialog"
            :title="$t('ExportRecordFilter')"
            :height-num="311"
            @cancel="showExportDialog = false">
            <bk-form :label-width="90">
                <bk-form-item :label="$t('scanTime')">
                    <bk-date-picker
                        v-model="exportTime"
                        :shortcuts="shortcuts"
                        type="daterange"
                        transfer
                        :placeholder="$t('selectDatePlaceHolder')">
                    </bk-date-picker>
                </bk-form-item>
                <bk-form-item :label="$t('scanStatus')">
                    <bk-select
                        v-model="exportStatus"
                        :clearable="false">
                        <bk-option id="ALL" :name="$t('total')"></bk-option>
                        <bk-option id="UN_QUALITY" :name="$t(`scanStatusEnum.UN_QUALITY`)"></bk-option>
                        <bk-option id="QUALITY_PASS" :name="$t(`scanStatusEnum.QUALITY_PASS`)"></bk-option>
                        <bk-option id="QUALITY_UNPASS" :name="$t(`scanStatusEnum.QUALITY_UNPASS`)"></bk-option>
                    </bk-select>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click="showExportDialog = false">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" theme="primary" @click="exportReport">{{$t('confirm')}}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import { segmentNumberThree } from '@repository/utils'
    import { mapActions } from 'vuex'
    import { SCAN_TYPE_LICENSE, SCAN_TYPE_SECURITY } from '../../../store/publicEnum'
    import moment from 'moment'
    import { before, zeroTime } from '@repository/utils/date'
    const nowTime = moment()
    export default {
        props: {
            scanPlan: Object
        },
        data () {
            return {
                filterTime: [],
                showExportDialog: false,
                exportTime: [zeroTime(before(30)), nowTime.toDate()],
                exportStatus: 'ALL',
                overview: {
                    artifactCount: 0,
                    critical: 0,
                    high: 0,
                    medium: 0,
                    low: 0,
                    total: 0,
                    unRecommend: 0,
                    unknown: 0,
                    unCompliance: 0
                },
                shortcuts: [
                    {
                        text: this.$t('lastSevenDays'),
                        value () {
                            return [zeroTime(before(7)), nowTime.toDate()]
                        }
                    },
                    {
                        text: this.$t('lastFifteenDays'),
                        value () {
                            return [zeroTime(before(15)), nowTime.toDate()]
                        }
                    },
                    {
                        text: this.$t('lastThirtyDays'),
                        value () {
                            return [zeroTime(before(30)), nowTime.toDate()]
                        }
                    }
                ]
            }
        },
        computed: {
            overviewList () {
                const info = [{ key: 'artifactCount', label: this.$t('scanArtifactNum') }]
                if (this.scanPlan.scanTypes.includes(SCAN_TYPE_SECURITY)) {
                    info.push(
                        { key: 'critical', label: this.$t(`leakLevelEnum.${'CRITICAL'}`) + this.$t('space') + this.$t('vulnerability'), color: '#EA3736' },
                        { key: 'high', label: this.$t(`leakLevelEnum.${'HIGH'}`) + this.$t('space') + this.$t('vulnerability'), color: '#FFB549' },
                        { key: 'medium', label: this.$t(`leakLevelEnum.${'MEDIUM'}`) + this.$t('space') + this.$t('vulnerability'), color: '#3A84FF' },
                        { key: 'low', label: this.$t(`leakLevelEnum.${'LOW'}`) + this.$t('space') + this.$t('vulnerability'), color: '#979BA5' })
                }
                if (this.scanPlan.scanTypes.includes(SCAN_TYPE_LICENSE)) {
                    info.push(
                        { key: 'total', label: this.$t('totalLicenses') },
                        { key: 'unRecommend', label: this.$t('deprecatedNumber') },
                        { key: 'unknown', label: this.$t('unknownNun') },
                        { key: 'unCompliance', label: this.$t('non-compliance') }
                    )
                }
                return info
            },
            formatISO () {
                const [startTime, endTime] = this.filterTime
                return {
                    ...(startTime instanceof Date ? { startTime: startTime.toISOString() } : {}),
                    ...(endTime instanceof Date ? { endTime: endTime.toISOString() } : {})
                }
            }
        },
        created () {
            // 在点击面包屑回退时需要设置时间选择的默认值
            const startTime = this.$route.query?.startTime || ''
            const endTime = this.$route.query?.endTime || ''
            const backData = [startTime ? new Date(startTime) : '', endTime ? new Date(endTime) : '']?.filter(Boolean) || []
            this.filterTime = backData || []
            this.changeFilterTime('initFlag')
        },
        methods: {
            segmentNumberThree,
            ...mapActions([
                'scanReportOverview',
                'scanLicenseOverview',
                'stopScan'
            ]),
            getScanReportOverview () {
                const reqs = []
                if (this.scanPlan.scanTypes.includes(SCAN_TYPE_SECURITY)) {
                    reqs.push(this.scanReportOverview)
                }
                if (this.scanPlan.scanTypes.includes(SCAN_TYPE_LICENSE)) {
                    reqs.push(this.scanLicenseOverview)
                }
                Promise.all(reqs.map(req =>
                    req({
                        projectId: this.scanPlan.projectId,
                        id: this.scanPlan.id,
                        ...this.formatISO
                    })
                )).then(res => {
                    res.forEach(planCount =>
                        Object.keys(this.overview).forEach(key => {
                            this.overview[key] = planCount[key] ?? this.overview[key]
                        })
                    )
                })
            },
            // 注意，当时间改变时此处的 initFlag 会是上方时间选择器绑定的数组
            changeFilterTime (initFlag) {
                this.getScanReportOverview()
                this.$emit('refreshData', 'formatISO', this.formatISO)
                this.$emit('refresh', { forceFlag: true, initFlag })
            },
            stopScanHandler () {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('stopScanMsg', [this.scanPlan.name]),
                    confirmFn: () => {
                        return this.stopScan({
                            projectId: this.scanPlan.projectId,
                            id: this.scanPlan.id
                        }).then(() => {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('discontinuedProgram') + this.$t('space') + this.$t('success')
                            })
                            this.$emit('refresh', false)
                        })
                    }
                })
            },
            startScanHandler () {
                this.$router.push({
                    name: 'startScan',
                    query: this.$route.query
                })
            },
            scanSettingHandler () {
                this.$router.push({
                    name: 'scanConfig',
                    params: {
                        ...this.$route.params,
                        projectId: this.scanPlan.projectId,
                        planId: this.scanPlan.id
                    },
                    query: {
                        ...this.$route.query,
                        scanName: this.scanPlan.name
                    }
                })
            },
            exportReport () {
                const [startTime, endTime] = this.exportTime.filter(Boolean)
                const params = new URLSearchParams({
                    projectId: this.scanPlan.projectId,
                    id: this.scanPlan.id,
                    ...(this.exportStatus === 'ALL'
                        ? {}
                        : {
                            status: this.exportStatus
                        }),
                    ...(startTime instanceof Date ? { startTime: startTime.toISOString() } : {}),
                    ...(endTime instanceof Date ? { endTime: endTime.toISOString() } : {})
                })
                this.showExportDialog = false
                this.$bkNotify({
                    title: this.$t('exportReportInfo'),
                    position: 'bottom-right',
                    theme: 'success'
                })
                const url = `/web/analyst/api/scan/plan/export?${params.toString()}`
                window.open(url, '_self')
            }
        }
    }
</script>
<style lang="scss" scoped>
.report-overview {
    padding: 10px 20px;
    background-color: white;
    .report-title {
        font-size: 14px;
        font-weight: 600;
        &:before {
            content: '';
            width: 3px;
            height: 12px;
            margin-right: 7px;
            border-radius: 1px;
            background-color: var(--primaryColor);
        }
    }
    .base-info-item {
        position: relative;
        height: 80px;
        padding: 20px;
        flex: 1;
        justify-content: space-around;
        border: solid var(--borderColor);
        border-width: 1px 0;
        background-color: var(--bgColor);
        .base-info-value {
            font-size: 20px;
            font-weight: 600;
        }
        &:first-child {
            flex: initial;
            width: 240px;
            margin-right: 20px;
            color: white;
            border-width: 0;
            border-radius: 2px;
            background-color: var(--primaryColor);
            background-image: url("data:image/svg+xml,%3Csvg width='80' height='80' viewBox='0 0 80 80' xmlns='http://www.w3.org/2000/svg'%3E%3Cdefs%3E%3ClinearGradient x1='21.842%25' y1='2.983%25' y2='137.901%25' id='a'%3E%3Cstop stop-color='%23FFF' offset='0%25'/%3E%3Cstop stop-color='%23FFF' stop-opacity='0' offset='100%25'/%3E%3C/linearGradient%3E%3C/defs%3E%3Cpath d='M36.805.134l.01.099.447 5.61A33.863 33.863 0 0 0 26.667 8.43a34.256 34.256 0 0 0-10.89 7.346 34.059 34.059 0 0 0-7.347 10.89C6.64 30.882 5.736 35.375 5.736 40c0 4.626.904 9.11 2.694 13.333a34.256 34.256 0 0 0 7.346 10.89c3.141 3.15 6.81 5.62 10.89 7.347 4.215 1.79 8.708 2.694 13.334 2.694 4.626 0 9.11-.904 13.333-2.694a34.256 34.256 0 0 0 10.89-7.346c3.15-3.141 5.62-6.81 7.347-10.89 1.79-4.215 2.694-8.708 2.694-13.334 0-4.626-.904-9.11-2.694-13.333a34.002 34.002 0 0 0-3.266-5.987l4.662-3.203c.01 0 .01-.01.018-.01l.036-.035C77.423 23.857 79.991 31.624 80 40c0 22.094-17.906 40-40 40S0 62.085 0 40C0 18.98 16.206 1.754 36.805.134zm1.352 17.1l.456 5.71a17.063 17.063 0 0 0-10.73 4.975 17.064 17.064 0 0 0-5.028 12.144c0 4.59 1.79 8.903 5.029 12.143a17.064 17.064 0 0 0 12.143 5.029c4.59 0 8.904-1.79 12.143-5.03a17.064 17.064 0 0 0 5.03-12.142c0-3.526-1.057-6.89-3.017-9.727l4.725-3.23a22.795 22.795 0 0 1 4.018 12.957c0 12.644-10.255 22.899-22.9 22.899-12.643 0-22.898-10.255-22.898-22.9 0-12.017 9.252-21.879 21.029-22.827zM40 0c12.564 0 23.767 5.799 31.096 14.864-.009 0-.009.008-.018.008l-.035.036-28.85 23.374a2.86 2.86 0 0 1-2.22 4.671 2.86 2.86 0 0 1-2.863-2.864 2.86 2.86 0 0 1 2.863-2.863V0z' fill='url(%23a)' fill-rule='nonzero' opacity='.2'/%3E%3C/svg%3E");
            background-repeat: no-repeat;
            background-position: calc(100% + 6px) calc(100% + 8px);
        }
        &:not(:first-child):before {
            content: '';
            position: absolute;
            width: 1px;
            height: calc(100% - 30px);
            left: 0;
            top: 15px;
            background-color: var(--borderColor);
        }
        &:nth-child(2):before {
            height: 100%;
            top: 0;
        }
        &:last-child {
            border-right-width: 1px;
        }
        :not(:first-child).base-info-key {
            color: var(--subsidiaryColor)
        }
    }
}
</style>
