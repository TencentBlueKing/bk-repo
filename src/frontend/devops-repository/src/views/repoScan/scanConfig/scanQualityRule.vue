<template>
    <bk-form style="max-width: 1080px;" :label-width="120" :model="rule" :rules="rules" ref="ruleForm">
        <template>
            <bk-form-item v-if="ruleTypes.includes(SCAN_TYPE_LICENSE)" :label="$t('licenseRules')">
                <div style="color:var(--fontSubsidiaryColor);">{{ $t('scanQualityLicenceRule') }}</div>
                <div class="mt10"><bk-checkbox v-model="rule.recommend">{{ $t('recommendLicenseRule') }}</bk-checkbox></div>
                <div class="mt10"><bk-checkbox v-model="rule.compliance">{{ $t('compliantLicenseRule') }}</bk-checkbox></div>
                <div class="mt10"><bk-checkbox v-model="rule.unknown">{{ $t('unknownLicenseRule') }}</bk-checkbox></div>
            </bk-form-item>
            <bk-form-item v-if="ruleTypes.includes(SCAN_TYPE_SECURITY)" :label="$t('safetyRules')">
                <div style="color:var(--fontSubsidiaryColor);">{{ $t('scanQualitySafetyRule') }}</div>
            </bk-form-item>
            <template v-if="ruleTypes.includes(SCAN_TYPE_SECURITY)">
                <bk-form-item label="" v-for="[id] in Object.entries(leakLevelEnum)" :key="id"
                    :property="id.toLowerCase()" error-display-type="normal">
                    <div class="flex-align-center">
                        <div :class="`status-sign ${id}`" :data-name="$t(`leakLevelEnum.${id}`) + $t('space') + $t('vulnerability') + `≦`"></div>
                        <bk-input class="ml10 mr10" style="width: 80px;" v-model.trim="rule[id.toLowerCase()]"></bk-input>
                        <span>{{ $t('per') }}</span>
                    </div>
                </bk-form-item>
            </template>
        </template>
        <bk-form-item :label="$t('triggerEvent')">
            <div style="color:var(--fontSubsidiaryColor);">{{ $t('scanQualityCheckBtnPre') }}</div>
            <!-- <div class="mt10"><bk-checkbox v-model="rule.forbidScanUnFinished">自动禁止使用制品：制品扫描未结束的制品</bk-checkbox></div> -->
            <div class="mt10"><bk-checkbox v-model="rule.forbidQualityUnPass">{{ $t('scanQualityCheckBtn') }}</bk-checkbox></div>
        </bk-form-item>
        <bk-form-item>
            <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import { mapActions } from 'vuex'
    import { leakLevelEnum } from '@repository/store/publicEnum'
    import { SCAN_TYPE_LICENSE, SCAN_TYPE_SECURITY } from '../../../store/publicEnum'
    export default {
        name: 'scanQualityRule',
        props: {
            projectId: String,
            planId: String,
            scanTypes: Array
        },
        data () {
            const validate = [
                {
                    regex: /^[0-9]*$/,
                    message: this.$t('nonNegativeIntegerTip'),
                    trigger: 'blur'
                }
            ]
            return {
                SCAN_TYPE_SECURITY: SCAN_TYPE_SECURITY,
                SCAN_TYPE_LICENSE: SCAN_TYPE_LICENSE,
                leakLevelEnum,
                rule: {
                    recommend: false,
                    compliance: false,
                    unknown: false,
                    critical: '',
                    high: '',
                    medium: '',
                    low: '',
                    forbidScanUnFinished: false,
                    forbidQualityUnPass: false
                },
                rules: {
                    critical: validate,
                    high: validate,
                    medium: validate,
                    low: validate
                }
            }
        },
        computed: {
            ruleTypes () {
                return this.scanTypes.filter(scanType => scanType === SCAN_TYPE_SECURITY || scanType === SCAN_TYPE_LICENSE)
            }
        },
        created () {
            this.getRules()
        },
        methods: {
            ...mapActions(['getQualityRule', 'saveQualityRule']),
            async save () {
                await this.$refs.ruleForm.validate()
                Promise
                    .all(this.ruleTypes.map(type => this.doSave(type)))
                    .then(() => {
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('save') + this.$t('success')
                        })
                        this.getRules()
                    })
            },
            doSave (ruleType) {
                return this.saveQualityRule({
                    type: ruleType,
                    id: this.planId,
                    body: Object.keys(this.rule).reduce((target, key) => {
                        const value = this.rule[key]
                        if (typeof value === 'string' && value.length > 0) {
                            target[key] = Number(value)
                        }
                        if (typeof value === 'boolean' || typeof value === 'number') {
                            target[key] = value
                        }
                        return target
                    }, {})
                })
            },
            getRules () {
                Promise.all(
                    this.ruleTypes.map(type => this.getQualityRule({ type: type, id: this.planId }))
                ).then(qualityRules => {
                    qualityRules.forEach(qualityRule => {
                        Object.keys(qualityRule).forEach(k => {
                            qualityRule[k] !== null && (this.rule[k] = qualityRule[k])
                        })
                    })
                })
            }
        }
    }
</script>
