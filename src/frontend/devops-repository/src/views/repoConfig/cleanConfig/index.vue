<template>
    <bk-form class="clean-config-container" :label-width="120" :model="config" :rules="rules" ref="cleanForm">
        <bk-form-item label="自动清理">
            <bk-switcher v-model="config.autoClean" size="small" theme="primary" @change="clearError"></bk-switcher>
        </bk-form-item>
        <bk-form-item v-if="repoType !== 'generic'" label="最少保留版本" required property="reserveVersions" error-display-type="normal">
            <bk-input class="w250" v-model="config.reserveVersions" :disabled="!config.autoClean"></bk-input>
            <div class="form-tip">制品版本数量超过保留版本数，且在保留时间内没有使用过的制品版本将会被清理</div>
        </bk-form-item>
        <bk-form-item label="最少保留时间(天)" required property="reserveDays" error-display-type="normal">
            <bk-input class="w250" v-model="config.reserveDays" :disabled="!config.autoClean"></bk-input>
        </bk-form-item>
        <bk-form-item label="保留规则">
            <bk-button :disabled="!config.autoClean" icon="plus" @click="addRule()">添加规则</bk-button>
            <div class="form-tip">
                {{ repoType === 'generic' ? '在目录（包含子目录）下符合规则的文件将不会被自动清理' : '符合规则的制品将不会被自动清理' }}
            </div>
            <div class="rule-list">
                <component
                    :is="repoType === 'generic' ? 'generic-clean-rule' : 'package-clean-rule'"
                    class="mt10"
                    v-for="(rule, ind) in config.rules"
                    :key="ind"
                    :disabled="!config.autoClean"
                    v-bind="rule"
                    @change="r => config.rules.splice(ind, 1, r)"
                    @delete="config.rules.splice(ind, 1)">
                </component>
            </div>
        </bk-form-item>
        <bk-form-item>
            <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import packageCleanRule from './packageCleanRule'
    import genericCleanRule from './genericCleanRule.vue'
    import { mapActions } from 'vuex'
    export default {
        name: 'cleanConfig',
        components: {
            packageCleanRule,
            genericCleanRule
        },
        props: {
            baseData: Object
        },
        data () {
            return {
                loading: false,
                config: {
                    autoClean: false,
                    reserveVersions: 20,
                    reserveDays: 30,
                    rules: []
                },
                rules: {
                    reserveVersions: [
                        {
                            regex: /^[0-9]+$/,
                            message: '请填写非负整数',
                            trigger: 'blur'
                        }
                    ],
                    reserveDays: [
                        {
                            regex: /^[0-9]+$/,
                            message: '请填写非负整数',
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoType () {
                return this.$route.params.repoType
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        watch: {
            baseData: {
                handler (val) {
                    if (!val.configuration.cleanStrategy) return
                    const {
                        autoClean = false,
                        reserveVersions = 20,
                        reserveDays = 30,
                        rule = { rules: [] }
                    } = val.configuration.cleanStrategy
                    this.config = { ...this.config, autoClean, reserveVersions, reserveDays }
                    const rules = rule.rules.find(r => r.rules)?.rules || []
                    this.config.rules = rules.map(r => {
                        return r.rules?.reduce((target, item) => {
                            target[item.field] = {
                                ...item,
                                value: item.operation === 'MATCH' ? item.value.replace(/^\*(.*)\*$/, '$1') : item.value
                            }
                            return target
                        }, {})
                    })
                },
                deep: true,
                immediate: true
            }
        },
        methods: {
            ...mapActions(['updateRepoInfo']),
            addRule () {
                if (!this.config.autoClean) return
                this.config.rules.push({})
            },
            clearError () {
                this.$refs.cleanForm.clearError()
            },
            async save () {
                await this.$refs.cleanForm.validate()
                const { autoClean, reserveVersions, reserveDays } = this.config
                let rules = this.config.rules
                rules = rules.map(rs => {
                    return {
                        relation: 'AND',
                        rules: Object.values(rs).map(i => {
                            return i.field.replace(/^metadata\./, '') && i.value && {
                                ...i,
                                value: i.operation === 'MATCH' ? `*${i.value}*` : i.value
                            }
                        }).filter(Boolean)
                    }
                }).filter(rs => rs.rules.length)
                this.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body: {
                        configuration: {
                            ...this.baseData.configuration,
                            cleanStrategy: {
                                autoClean,
                                ...(this.repoType === 'generic' ? {} : { reserveVersions }),
                                reserveDays,
                                rule: {
                                    relation: 'AND',
                                    rules: [
                                        this.repoType === 'generic'
                                            ? {
                                                field: 'projectId',
                                                value: this.projectId,
                                                operation: 'EQ'
                                            }
                                            : undefined,
                                        this.repoType === 'generic'
                                            ? {
                                                field: 'repoName',
                                                value: this.repoName,
                                                operation: 'EQ'
                                            }
                                            : undefined,
                                        {
                                            relation: 'OR',
                                            rules
                                        }
                                    ].filter(Boolean)
                                }
                            }
                        }
                    }
                }).then(() => {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                }).finally(() => {
                    this.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.clean-config-container {
    max-width: 1080px;
}
</style>
