<template>
    <div class="scan-config-container" v-bkloading="{ isLoading }">
        <bk-tab class="scan-config-tab page-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="baseInfo" :label="$t('baseSetting')" v-if="!scanBaseInfo.readOnly">
                <bk-form :label-width="120">
                    <bk-form-item :label="$t('schemeName')">
                        <bk-input class="w250" v-model.trim="scanBaseInfo.name" maxlength="32" show-word-limit></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('schemeType')">{{ $t(`scanTypeEnum.${scanBaseInfo.type}`) }}</bk-form-item>
                    <bk-form-item :label="$t('scanner')">{{ scanBaseInfo.scanner }}</bk-form-item>
                    <bk-form-item :label="$t('description')">
                        <bk-input type="textarea"
                            class="w480"
                            maxlength="200"
                            :rows="6"
                            v-model.trim="scanBaseInfo.description">
                        </bk-input>
                    </bk-form-item>
                    <bk-form-item>
                        <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
                    </bk-form-item>
                </bk-form>
            </bk-tab-panel>
            <bk-tab-panel render-directive="if" name="autoConfig" :label="$t('monitorSettings')" v-if="!scanBaseInfo.readOnly">
                <auto-scan-config :data="scanBaseInfo" @save="ajaxSaveConfig"></auto-scan-config>
            </bk-tab-panel>
            <bk-tab-panel render-directive="if" name="qualityRule" :label="$t('qualityRules')">
                <scan-quality-rule :project-id="projectId" :plan-id="planId" :scan-types="scanBaseInfo.scanTypes">
                </scan-quality-rule>
            </bk-tab-panel>
            <bk-tab-panel render-directive="if" name="ignoreRules" :label="$t('ignoreRules')">
                <ignore-rule :project-id="projectId" :plan-id="planId">
                </ignore-rule>
            </bk-tab-panel>
        </bk-tab>
    </div>
</template>
<script>
    import autoScanConfig from './autoScanConfig'
    import scanQualityRule from './scanQualityRule'
    import { mapActions } from 'vuex'
    import { scanTypeEnum } from '@repository/store/publicEnum'
    import IgnoreRule from './ignoreRule'
    export default {
        name: 'scanConfig',
        components: {
            IgnoreRule,
            autoScanConfig,
            scanQualityRule
        },
        data () {
            return {
                scanTypeEnum,
                tabName: 'baseInfo',
                scanBaseInfo: {
                    name: '',
                    type: '',
                    scanTypes: [],
                    scanner: '',
                    description: '',
                    scanOnNewArtifact: false,
                    rule: {
                        rules: []
                    }
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            planId () {
                return this.$route.params.planId
            }
        },
        created () {
            this.ajaxScanConfig()
        },
        methods: {
            ...mapActions([
                'getScanConfig',
                'saveScanConfig'
            ]),
            save () {
                this.ajaxSaveConfig({
                    name: this.scanBaseInfo.name,
                    description: this.scanBaseInfo.description
                })
            },
            ajaxScanConfig () {
                this.getScanConfig({
                    projectId: this.projectId,
                    id: this.planId
                }).then(res => {
                    this.scanBaseInfo = res
                })
            },
            ajaxSaveConfig (body) {
                this.saveScanConfig({
                    id: this.planId,
                    projectId: this.projectId,
                    ...body
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                    this.ajaxScanConfig()
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.scan-config-container {
    height: 100%;
    background-color: white;
    .scan-config-tab {
        height: 100%;
        ::v-deep .bk-tab-section {
            height: calc(100% - 60px);
            overflow-y: auto;
        }
    }
}
</style>
