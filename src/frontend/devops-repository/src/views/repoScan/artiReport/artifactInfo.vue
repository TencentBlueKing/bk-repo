<template>
    <div class="base-info flex-column">
        <div class="pt20 pb20 flex-align-center">
            <Icon class="mr10" size="40" :name="(subtaskOverview.repoType || '').toLowerCase()" />
            <span class="arti-name text-overflow" :title="subtaskOverview.name">{{ subtaskOverview.name }}</span>
        </div>
        <div class="arti-meta">
            <div v-for="item in artifactInfo" :key="item.label" class="meta-content">
                <span style="color:var(--fontSubsidiaryColor);">{{ item.label }}</span>
                <span class="ml20">{{ item.value }}</span>
            </div>
        </div>
        <div v-if="subtaskOverview.qualityRedLine !== null" class="arti-quality">
            <div class="flex-align-center">
                <span class="mr20" style="color:var(--fontSubsidiaryColor);">{{ $t('qualityRules')}}</span>
                <span v-if="subtaskOverview.qualityRedLine" class="repo-tag SUCCESS">{{$t('pass')}}</span>
                <span v-else class="repo-tag FAILED">{{$t('notPass')}}</span>
            </div>
            <div v-for="item in qualityRules" :key="item">{{ item }}</div>
        </div>
        <div class="arti-leak" v-if="scanTypes.includes(SCAN_TYPE_SECURITY)">
            <div style="color:var(--fontSubsidiaryColor);">{{$t('vulnerabilityStatistics')}}</div><div></div>
            <div class="status-sign"
                :class="id"
                v-for="[id] in Object.entries(leakLevelEnum)"
                :key="id"
                :data-name="$t(`leakLevelEnum.${id}`) + $t('space') + $t('vulnerability') + `:${segmentNumberThree(subtaskOverview[id.toLowerCase()])}`">
            </div>
        </div>
    </div>
</template>

<script>
    import { mapActions } from 'vuex'
    import { leakLevelEnum, SCAN_TYPE_SECURITY } from '../../../store/publicEnum'
    import { segmentNumberThree } from '../../../utils'

    export default {
        name: 'artifactInfo',
        props: {
            scanTypes: Array,
            subtaskOverview: Object
        },
        data () {
            return {
                SCAN_TYPE_SECURITY: SCAN_TYPE_SECURITY,
                leakLevelEnum: leakLevelEnum
            }
        },
        computed: {
            artifactInfo () {
                const info = [
                    { label: this.$t('duration'), value: this.subtaskOverview.duration },
                    { label: this.$t('finishTime'), value: this.subtaskOverview.finishTime },
                    { label: this.$t('repo'), value: this.subtaskOverview.repoName }
                ]
                if (this.subtaskOverview.packageKey) {
                    info.push(
                        { label: this.$t('artifactName'), value: this.subtaskOverview.packageKey },
                        { label: this.$t('artifactVersion'), value: this.subtaskOverview.version }
                    )
                } else {
                    info.push({ label: this.$t('storagePath'), value: this.subtaskOverview.fullPath })
                }
                return info
            },
            qualityRules () {
                const rules = []
                const scanQuality = this.subtaskOverview.scanQuality
                if (scanQuality) {
                    Object.keys(leakLevelEnum).forEach(level => {
                        const count = scanQuality[level.toLowerCase()]
                        // 后端返回的漏洞总数为数字类型，可能包含0，此时需要0也能进入下方判断，但空字符串和null不能进入判断
                        if (!isNaN(parseInt(count))) {
                            rules.push(this.$t(`leakLevelEnum.${level}`) + this.$t('totalNumVulnerability') + ` ≦ ${count}`)
                        }
                    })

                    const licenseRule = {
                        recommend: this.$t('recommendLicenseRule'),
                        compliance: this.$t('compliantLicenseRule'),
                        unknown: this.$t('unknownLicenseRule')
                    }
                    Object.keys(licenseRule).forEach(k => {
                        if (scanQuality[k]) {
                            rules.push(licenseRule[k])
                        }
                    })
                }

                return rules
            }
        },
        methods: {
            segmentNumberThree,
            ...mapActions(['artiReportOverview', 'licenseReportOverview'])
        }
    }
</script>

<style lang="scss" scoped>
.base-info {
    height: 100%;
    overflow-y: auto;
    padding: 0 10px;
    flex-basis: 334px;
    border: 1px solid var(--borderColor);
    background-color: var(--bgColor);
    .arti-name {
        max-width: 250px;
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
        grid-template: auto / repeat(1, 1fr);
    }
}
</style>
