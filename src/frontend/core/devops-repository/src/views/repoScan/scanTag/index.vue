<template>
    <span class="scan-tag-container"
        :class="{ 'spin-icon': status === 'RUNNING', readonly }"
        v-bk-tooltips="{ content: scanStatusEnum[status] ? $t(`scanStatusEnum.${status}`) : $t('unscanned'), placements: ['right'] }"
        @click.stop="showScanList"
        v-bk-clickoutside="handleClickOutSide">
        <Icon size="16" :name="`scan-${(status || 'INIT').toLowerCase()}`" />
        <bk-dialog
            class="scan-list-dialog"
            v-model="visible"
            :position="position"
            :width="330"
            :show-mask="false"
            :close-icon="false"
            :show-footer="false"
            :draggable="false">
            <div class="">
                <span style="font-size:14px;font-weight:600;">{{ $t('qualityRules') }}</span>
                <span class="ml10 repo-tag"
                    :class="{
                        [status]: true,
                        'SUCCESS': status === 'QUALITY_PASS',
                        'INIT': status === 'UN_QUALITY',
                        'WARNING': status === 'QUALITY_UNPASS'
                    }">
                    {{scanStatusEnum[status] ? $t(`scanStatusEnum.${status}`) : $t('unscanned')}}
                </span>
            </div>
            <div class="scan-item flex-between-center"
                v-for="scan in scanList"
                :key="scan.id">
                <div class="flex-align-center">
                    <Icon v-bk-tooltips="{ content: scanStatusEnum[scan.status] ? $t(`scanStatusEnum.${scan.status}`) : $t('unscanned'), placements: ['bottom-start'] }" size="16" :name="`scan-${(scan.status || 'INIT').toLowerCase()}`" />
                    <span class="ml5 text-overflow" style="max-width:150px;" :title="scan.name">{{ scan.name }}</span>
                </div>
                <bk-button text theme="primary" :disabled="!['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS'].includes(scan.status)" @click="toReport(scan)">{{ $t('viewDetails') }}</bk-button>
            </div>
        </bk-dialog>
    </span>
</template>
<script>
    import { scanStatusEnum } from '@repository/store/publicEnum'
    import { mapActions } from 'vuex'
    export default {
        name: 'scanTag',
        props: {
            status: String,
            repoType: String,
            fullPath: String,
            readonly: Boolean
        },
        data () {
            return {
                visible: false,
                position: {
                    top: 0,
                    left: 0
                },
                isLoading: false,
                scanStatusEnum,
                scanList: []
            }
        },
        methods: {
            ...mapActions(['getArtiScanList']),
            showScanList (e) {
                if (this.readonly || !this.status) return
                this.openScanList(e)
                this.isLoading = true
                const { projectId, repoType = this.repoType } = this.$route.params
                const { repoName, version, packageKey } = this.$route.query
                this.getArtiScanList({
                    projectId,
                    repoType,
                    repoName,
                    packageKey: packageKey || undefined,
                    version: version || undefined,
                    fullPath: this.fullPath || undefined
                }).then(({ scanPlans }) => {
                    this.scanList = scanPlans
                }).finally(() => {
                    this.isLoading = false
                })
            },
            openScanList (e) {
                this.position = {
                    top: e.clientY,
                    left: e.clientX
                }
                this.visible = true
            },
            handleClickOutSide () {
                this.visible = false
            },
            toReport ({ planType, id, recordId, status }) {
                if (['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS'].includes(status)) {
                    this.$router.push({
                        name: 'artiReport',
                        params: {
                            planId: id,
                            recordId
                        },
                        query: {
                            ...this.$route.params,
                            ...this.$route.query,
                            ...(this.repoType ? { repoType: this.repoType } : {}),
                            scanType: planType
                        }
                    })
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.scan-tag-container {
    font-size: 0;
    &:not(.readonly) {
        cursor: pointer;
    }
}
</style>
<style lang="scss">
.scan-list-dialog {
    .bk-dialog-wrapper {
        .bk-dialog {
            margin: initial;
            .bk-dialog-tool {
                display: none;
            }
            .bk-dialog-body {
                padding: 20px;
                max-height: 270px;
                min-height: auto;
                overflow-y: auto;
            }
        }
    }
    .scan-item {
        margin-top: 10px;
        padding: 10px 15px;
        font-size: 12px;
        border: 1px solid var(--borderColor);
        &:first-child {
            margin: 0;
        }
    }
}
</style>
