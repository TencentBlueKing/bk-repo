<template>
    <bk-form class="clean-config-container" :label-width="120" :model="cleanupStrategy" :rules="rules" ref="cleanForm">
        <bk-form-item :label="$t('autoCleanup')">
            <bk-switcher v-model="cleanupStrategy.enable" size="small" theme="primary" @change="clearError"></bk-switcher>
        </bk-form-item>
        <bk-form-item :label="$t('cleanupStrategy')" required>
            <bk-select
                style="width:150px;"
                v-model="cleanupStrategy.cleanupType"
                @change="changeType"
                :clearable="false"
                :disabled="!cleanupStrategy.enable">
                <bk-option id="retentionDays" :name="$t('retentionDays')"></bk-option>
                <bk-option id="retentionDate" :name="$t('retentionDate')"></bk-option>
                <bk-option v-if="repoType !== 'generic'" id="retentionNums" :name="$t('retentionNums')"></bk-option>
            </bk-select>
        </bk-form-item>
        <bk-form-item :label="$t('retentionDays')" property="cleanupValue" required error-display-type="normal" v-if="cleanupStrategy.cleanupType === 'retentionDays'">
            <bk-input class="w250" v-model="cleanupStrategy.cleanupValue" :disabled="!cleanupStrategy.enable"></bk-input>
        </bk-form-item>
        <bk-form-item :label="$t('retentionDate')" property="cleanupValue" required error-display-type="normal" v-if="cleanupStrategy.cleanupType === 'retentionDate'">
            <bk-date-picker v-model="cleanupStrategy.cleanupValue" :placeholder="$t('chooseDateTip')" :clearable="false" :type="'datetime'" :disabled="!cleanupStrategy.enable"></bk-date-picker>
        </bk-form-item>
        <bk-form-item :label="$t('retentionNums')" property="cleanupValue" required error-display-type="normal" v-if="cleanupStrategy.cleanupType === 'retentionNums'">
            <bk-input class="w250" v-model="cleanupStrategy.cleanupValue" :disabled="!cleanupStrategy.enable"></bk-input>
        </bk-form-item>
        <bk-form-item :label="$t('cleanTarget')">
            <bk-button :disabled="!cleanupStrategy.enable" icon="plus" @click="addRule()">{{$t('addTarget')}}</bk-button>
            <div class="rule-list">
                <component
                    :is="'generic-clean-rule'"
                    class="mt10"
                    v-for="(rule, ind) in cleanupStrategy.cleanTargets"
                    :key="ind"
                    :disabled="!cleanupStrategy.enable"
                    v-bind="rule"
                    :path.sync="rule"
                    :type.sync="repoType"
                    @change="changeTarget($event, ind)"
                    @delete="deleteTarget(ind)">
                </component>
            </div>
        </bk-form-item>
        <bk-form-item>
            <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import genericCleanRule from './genericCleanRule.vue'
    import { mapActions } from 'vuex'
    import moment from 'moment'
    export default {
        name: 'cleanConfig',
        components: {
            genericCleanRule
        },
        props: {
            baseData: Object
        },
        data () {
            return {
                loading: false,
                cleanupStrategy: {
                    enable: false,
                    cleanupValue: '',
                    cleanTargets: [],
                    cleanupType: 'retentionDays'
                },
                rules: {
                    cleanupValue: [
                        {
                            validator: this.asynCheckCleanValue,
                            message: this.$t('cleanConfigTip'),
                            trigger: 'blur'
                        },
                        {
                            required: true,
                            message: this.$t('cleanConfigTip'),
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
                    if (!val.cleanupStrategy) return
                    this.cleanupStrategy = val.cleanupStrategy
                },
                deep: true,
                immediate: true
            }
        },
        methods: {
            ...mapActions(['updateRepoInfo']),
            addRule () {
                if (!this.cleanupStrategy.enable) return
                if (!this.cleanupStrategy.cleanTargets) {
                    const cleanStrategy = {
                        enable: this.cleanupStrategy.enable,
                        cleanupType: this.cleanupStrategy.cleanupType,
                        cleanupValue: this.cleanupStrategy.cleanupValue,
                        cleanTargets: []
                    }
                    this.cleanupStrategy = cleanStrategy
                }
                this.cleanupStrategy.cleanTargets.push('')
            },
            clearError () {
                this.$refs.cleanForm.clearError()
            },
            async save () {
                let cleanStrategy
                if (!this.cleanupStrategy.enable) {
                    cleanStrategy = {
                        enable: this.cleanupStrategy.enable
                    }
                } else {
                    await this.$refs.cleanForm.validate()
                    let cleanValue = ''
                    if (this.cleanupStrategy.cleanupType === 'retentionDate' && this.cleanupStrategy.cleanupValue instanceof Date) {
                        cleanValue = moment(this.cleanupStrategy.cleanupValue).add(8, 'hours').toISOString()
                    } else {
                        cleanValue = this.cleanupStrategy.cleanupValue
                    }
                    if (this.cleanupStrategy.cleanTargets) {
                        const target = this.cleanupStrategy.cleanTargets.filter(function (item, index, array) {
                            if (item.trim().length !== 0 && item !== null) {
                                return array.indexOf(item) === index
                            } else {
                                return false
                            }
                        })
                        cleanStrategy = {
                            enable: this.cleanupStrategy.enable,
                            cleanupType: this.cleanupStrategy.cleanupType,
                            cleanupValue: cleanValue,
                            cleanTargets: target
                        }
                    } else {
                        cleanStrategy = {
                            enable: this.cleanupStrategy.enable,
                            cleanupType: this.cleanupStrategy.cleanupType,
                            cleanupValue: cleanValue
                        }
                    }
                }
                this.baseData.configuration.settings.cleanupStrategy = cleanStrategy
                this.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body: {
                        configuration: {
                            ...this.baseData.configuration
                        }
                    }
                }).then(() => {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('space') + this.$t('success')
                    })
                }).finally(() => {
                    this.loading = false
                })
            },
            asynCheckCleanValue () {
                if (this.cleanupStrategy.cleanupType === 'retentionDate') {
                    return this.cleanupStrategy.cleanupValue instanceof Date || moment(this.cleanupStrategy.cleanupValue).isValid()
                } else {
                    return (/^[0-9]+$/).test(this.cleanupStrategy.cleanupValue)
                }
            },
            changeType () {
                if (this.cleanupStrategy.cleanupType !== 'retentionDate' && this.cleanupStrategy.cleanupValue instanceof Date) {
                    this.cleanupStrategy.cleanupValue = ''
                }
                this.clearError()
            },
            deleteTarget (index) {
                this.cleanupStrategy.cleanTargets.splice(index, 1)
            },
            changeTarget (r, ind) {
                this.cleanupStrategy.cleanTargets.splice(ind, 1, r.path)
            }
        }
    }
</script>
<style lang="scss" scoped>
.clean-config-container {
    max-width: 1080px;
}
</style>
