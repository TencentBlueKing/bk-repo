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
                <span class="mr20" style="color:var(--fontSubsidiaryColor);">质量规则</span>
                <span v-if="subtaskOverview.qualityRedLine" class="repo-tag SUCCESS">通过</span>
                <span v-else class="repo-tag FAILED">不通过</span>
            </div>
            <div v-for="item in qualityRules" :key="item">{{ item }}</div>
        </div>
        <div class="arti-leak" v-if="scanTypes.includes(SCAN_TYPE_SECURITY)">
            <div style="color:var(--fontSubsidiaryColor);">漏洞数量统计</div><div></div>
            <div class="status-sign"
                :class="id"
                v-for="[id, name] in Object.entries(leakLevelEnum)"
                :key="id"
                :data-name="`${name}漏洞：${segmentNumberThree(subtaskOverview[id.toLowerCase()])}`">
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
                    { label: '持续时间', value: this.subtaskOverview.duration },
                    { label: '完成时间', value: this.subtaskOverview.finishTime },
                    { label: '所属仓库', value: this.subtaskOverview.repoName }
                ]
                if (this.subtaskOverview.packageKey) {
                    info.push(
                        { label: '制品名称', value: this.subtaskOverview.packageKey },
                        { label: '制品版本', value: this.subtaskOverview.version }
                    )
                } else {
                    info.push({ label: '存储路径', value: this.subtaskOverview.fullPath })
                }
                return info
            },
            qualityRules () {
                const rules = []
                const scanQuality = this.subtaskOverview.scanQuality
                if (scanQuality) {
                    Object.keys(leakLevelEnum).forEach(level => {
                        const count = scanQuality[level.toLowerCase()]
                        if (count) {
                            rules.push(`${leakLevelEnum[level]}漏洞总数 ≦ ${count}`)
                        }
                    })

                    const licenseRule = {
                        recommend: '仅有推荐使用的许可证',
                        compliance: '仅有合规的许可证',
                        unknown: '无未知许可证'
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
    padding: 0 20px;
    flex-basis: 360px;
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
</style>
