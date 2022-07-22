<template>
    <bk-form style="max-width: 1080px;" :label-width="120" :model="rule" :rules="rules" ref="ruleForm">
        <template v-if="scanType.includes('LICENSE')">
            <bk-form-item label="质量规则">
                <div style="color:var(--fontSubsidiaryColor);">当许可证中出现不符合以下规则的许可证时，则不通过质量规则</div>
                <div class="mt10"><bk-checkbox v-model="rule.recommend">仅有推荐使用的许可证</bk-checkbox></div>
                <div class="mt10"><bk-checkbox v-model="rule.compliance">仅有合规的许可证</bk-checkbox></div>
                <div class="mt10"><bk-checkbox v-model="rule.unknown">无未知许可证</bk-checkbox></div>
            </bk-form-item>
        </template>
        <template v-else>
            <bk-form-item label="质量规则">
                <div style="color:var(--fontSubsidiaryColor);">当扫描的制品漏洞超过下方任意一条规则中设定的数量，则制品未通过质量规则</div>
            </bk-form-item>
            <bk-form-item label="" v-for="[id, name] in Object.entries(leakLevelEnum)" :key="id"
                :property="id.toLowerCase()" error-display-type="normal">
                <div class="flex-align-center">
                    <div :class="`status-sign ${id}`" :data-name="`${name}漏洞≦`"></div>
                    <bk-input class="ml10 mr10" style="width: 80px;" v-model.trim="rule[id.toLowerCase()]"></bk-input>
                    <span>个</span>
                </div>
            </bk-form-item>
        </template>
        <!-- <bk-form-item label="触发事件">
            <div style="color:var(--fontSubsidiaryColor);">可勾选下方按钮，在扫描或扫描结束后触发勾选项</div>
            <div class="mt10"><bk-checkbox v-model="rule.forbidScanUnFinished">自动禁止使用制品：制品扫描未结束的制品</bk-checkbox></div>
            <div class="mt10"><bk-checkbox v-model="rule.forbidQualityUnPass">自动禁止使用制品：质量规则未通过的制品</bk-checkbox></div>
        </bk-form-item> -->
        <bk-form-item>
            <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import { mapActions } from 'vuex'
    import { leakLevelEnum } from '@repository/store/publicEnum'
    export default {
        name: 'scanQualityRule',
        data () {
            const validate = [
                {
                    regex: /^[0-9]*$/,
                    message: '输入格式错误，请输入非负整数',
                    trigger: 'blur'
                }
            ]
            return {
                leakLevelEnum,
                rule: {
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
            projectId () {
                return this.$route.params.projectId
            },
            scanType () {
                return this.$route.query.scanType
            },
            planId () {
                return this.$route.params.planId
            }
        },
        created () {
            if (this.scanType.includes('LICENSE')) {
                this.rule = {
                    recommend: false,
                    compliance: false,
                    unknown: false,
                    forbidQualityUnPass: false
                }
            }
            this.getRules()
        },
        methods: {
            ...mapActions(['getQualityRule', 'saveQualityRule']),
            async save () {
                await this.$refs.ruleForm.validate()
                this.saveQualityRule({
                    type: this.scanType,
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
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                    this.getRules()
                })
            },
            getRules () {
                this.getQualityRule({
                    type: this.scanType,
                    id: this.planId
                }).then(res => {
                    Object.keys(res).forEach(k => {
                        res[k] !== null && (this.rule[k] = res[k])
                    })
                })
            }
        }
    }
</script>
