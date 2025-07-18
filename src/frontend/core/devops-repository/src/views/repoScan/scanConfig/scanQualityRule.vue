<template>
    <bk-form style="max-width: 1080px;" :label-width="120" :model="rule" :rules="rules" ref="ruleForm" v-bkloading="{ isLoading }">
        <bk-form-item :label="$t('qualityRules')">
            <bk-switcher v-model="editable" size="small" theme="primary" @change="$refs.ruleForm.clearError()"></bk-switcher>
        </bk-form-item>
        <template>
            <bk-form-item v-if="ruleTypes.includes(SCAN_TYPE_LICENSE)" :label="$t('licenseRules')" property="recommend" error-display-type="normal">
                <div style="color:var(--fontSubsidiaryColor);">{{ $t('scanQualityLicenceRule') }}</div>
                <div class="mt10"><bk-checkbox :disabled="!editable" v-model="rule.recommend">{{ $t('recommendLicenseRule') }}</bk-checkbox></div>
                <div class="mt10"><bk-checkbox :disabled="!editable" v-model="rule.compliance">{{ $t('compliantLicenseRule') }}</bk-checkbox></div>
                <div class="mt10"><bk-checkbox :disabled="!editable" v-model="rule.unknown">{{ $t('unknownLicenseRule') }}</bk-checkbox></div>
            </bk-form-item>
            <bk-form-item v-if="ruleTypes.includes(SCAN_TYPE_SECURITY)" :label="$t('safetyRules')">
                <div style="color:var(--fontSubsidiaryColor);">{{ $t('scanQualitySafetyRule') }}</div>
            </bk-form-item>
            <template v-if="ruleTypes.includes(SCAN_TYPE_SECURITY)">
                <bk-form-item label="" v-for="[id] in Object.entries(leakLevelEnum)" :key="id"
                    :property="id.toLowerCase()" error-display-type="normal">
                    <div class="flex-align-center">
                        <div :class="`status-sign ${id}`" :data-name="$t(`leakLevelEnum.${id}`) + $t('space') + $t('vulnerability') + `≦`"></div>
                        <bk-input class="ml10 mr10" style="width: 80px;" :disabled="!editable" v-model.trim="rule[id.toLowerCase()]"
                                  :placeholder="$t('pleaseInput')"
                                  @focus="$refs.ruleForm.clearError()"
                            @blur="$refs.ruleForm.validate()"></bk-input>
                        <span>{{ $t('per') }}</span>
                    </div>
                </bk-form-item>
            </template>
        </template>
        <bk-form-item :label="$t('triggerEvent')">
            <div style="color:var(--fontSubsidiaryColor);">{{ $t('scanQualityCheckBtnPre') }}</div>
            <!-- <div class="mt10"><bk-checkbox v-model="rule.forbidScanUnFinished">自动禁止使用制品：制品扫描未结束的制品</bk-checkbox></div> -->
            <div class="mt10"><bk-checkbox :disabled="!editable" v-model="rule.forbidQualityUnPass">{{ $t('scanQualityCheckBtn') }}</bk-checkbox></div>
            <div class="mt10"><bk-checkbox :disabled="!(data.scanOnNewArtifact && editable)" v-model="rule.forbidNotScanned">{{ $t('scanQualityScanBtn') }}</bk-checkbox>
                <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('scanQualityScanTip')"></i>
            </div>
        </bk-form-item>
        <bk-form-item>
            <bk-button :loading="isLoading" theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import { mapActions } from 'vuex'
    import { leakLevelEnum, SCAN_TYPE_LICENSE, SCAN_TYPE_SECURITY } from '@repository/store/publicEnum'
    export default {
        name: 'scanQualityRule',
        props: {
            projectId: String,
            planId: String,
            scanTypes: Array,
            data: {
                type: Object,
                default: () => ({
                    type: '',
                    scanOnNewArtifact: false,
                    rule: {}
                })
            }
        },
        data () {
            const validate = {
                validator: this.securityNumberValidate,
                message: this.$t('scanQualityNonNegativeIntegerTip'),
                trigger: 'blur'
            }
            return {
                SCAN_TYPE_SECURITY: SCAN_TYPE_SECURITY,
                SCAN_TYPE_LICENSE: SCAN_TYPE_LICENSE,
                leakLevelEnum,
                editable: false,
                isLoading: false,
                rule: {
                    recommend: false,
                    compliance: false,
                    unknown: false,
                    critical: '',
                    high: '',
                    medium: '',
                    low: '',
                    forbidScanUnFinished: false,
                    forbidQualityUnPass: false,
                    forbidNotScanned: false
                },
                rules: {
                    critical: [validate],
                    high: [validate],
                    medium: [validate],
                    low: [
                        validate,
                        {
                            validator: () => this.editable ? this.computedEditable() : true,
                            message: this.$t('scanQualityErrorTips'),
                            trigger: 'blur'
                        }
                    ],
                    recommend: [
                        {
                            validator: () => this.editable ? this.computedEditable() : true,
                            message: this.$t('scanQualityErrorTips'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ruleTypes () {
                return this.scanTypes.filter(scanType => scanType === SCAN_TYPE_SECURITY || scanType === SCAN_TYPE_LICENSE)
            }
        },
        created () {
            this.initData()
            this.getRules()
        },
        methods: {
            ...mapActions(['getQualityRule', 'saveQualityRule']),
            securityNumberValidate (value) {
                return (/^[0-9]*$/).test(value) && value <= 10000
            },
            initData () {
                this.rule = this.scanTypes.includes('LICENSE')
                    && {
                        ...this.rule,
                        recommend: false,
                        compliance: false,
                        unknown: false,
                        forbidScanUnFinished: false,
                        forbidQualityUnPass: false
                    }
                this.rule = this.scanTypes.includes('SECURITY')
                    && {
                        ...this.rule,
                        critical: '',
                        high: '',
                        medium: '',
                        low: '',
                        forbidScanUnFinished: false,
                        forbidQualityUnPass: false
                    }
            },
            async save () {
                await this.$refs.ruleForm.validate()
                this.isLoading = true
                //  当质量规则关闭时，调用后台接口传参为空对象，不然会导致质量规则一直无法关闭(开关是否开启由下方方法计算得到)
                this.saveQualityRule({
                    id: this.planId,
                    body: !this.editable
                        ? {}
                        : Object.keys(this.rule).reduce((target, key) => {
                            const value = this.rule[key]
                            if (typeof value === 'string' && value.length > 0) {
                                target[key] = Number(value)
                            }
                            if (typeof value === 'boolean' || typeof value === 'number') {
                                target[key] = value
                            }
                            return target
                        }, {})
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                    this.initData()
                    this.getRules()
                }).finally(() => {
                    this.isLoading = false
                })
            },
            getRules () {
                this.getQualityRule({ id: this.planId }).then((res) => {
                    Object.keys(res).forEach(k => {
                        res[k] !== null && (this.rule[k] = res[k])
                    })
                    this.editable = this.computedEditable()
                })
            },
            // 计算质量规则开关是否开启，当下方任何一个值存在时(数值不为空或其他值不为false)开关都需要设置为开启状态
            computedEditable () {
                const { critical, high, medium, low, recommend, compliance, unknown } = this.rule
                let licenseFlag = false
                let securityFlag = false
                licenseFlag = Boolean(
                    recommend
                        || compliance
                        || unknown
                )
                securityFlag = Boolean(
                    critical !== ''
                        || high !== ''
                        || medium !== ''
                        || low !== ''
                )
                // docker仓库现在同时支持扫描许可和漏洞，两个规则只要其中任何一个有值就可以保存
                if (this.scanTypes.includes('LICENSE') && this.scanTypes.includes('SECURITY')) {
                    return licenseFlag || securityFlag
                } else {
                    return this.scanTypes.includes('LICENSE') ? licenseFlag : securityFlag
                }
            }
        }
    }
</script>
