<template>
    <bk-form style="max-width: 1080px;" :label-width="150" :model="config" :rules="rules" ref="scanForm">
        <bk-form-item label="自动扫描">
            <bk-switcher v-model="config.autoScan" size="small" theme="primary"></bk-switcher>
            <div style="color:var(--fontSubsidiaryColor);">符合方案类型要求并且满足以下规则的制品，在新入库时会使用本方案进行扫描</div>
        </bk-form-item>
        <bk-form-item label="仓库范围" :required="true" property="repo" error-display-type="normal">
            <repo-table
                ref="repoConfig"
                :init-data="config.repoNameList"
                :scan-type="config.type"
                :disabled="!config.autoScan"
                @clearError="clearError">
            </repo-table>
        </bk-form-item>
        <bk-form-item label="制品范围" :required="true" property="arti" error-display-type="normal">
            <arti-table
                ref="artiConfig"
                :init-data="config.artifactRules"
                :scan-type="config.type"
                :disabled="!config.autoScan"
                @clearError="clearError">
            </arti-table>
        </bk-form-item>
        <bk-form-item>
            <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import repoTable from './repoTable'
    import artiTable from './artiTable'
    export default {
        name: 'autoScanConfig',
        components: { repoTable, artiTable },
        props: {
            data: {
                type: Object,
                default: () => ({
                    type: '',
                    autoScan: false,
                    repoNameList: [],
                    artifactRules: []
                })
            }
        },
        data () {
            return {
                config: {
                    autoScan: false,
                    repoNameList: [],
                    artifactRules: []
                },
                rules: {
                    repo: [
                        {
                            validator: () => {
                                return this.$refs.repoConfig.getConfig()
                            },
                            message: '请选择指定仓库',
                            trigger: 'blur'
                        }
                    ],
                    arti: [
                        {
                            validator: () => {
                                return this.$refs.artiConfig.getConfig()
                            },
                            message: '请填写制品规则',
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
            planId () {
                return this.$route.params.planId
            }
        },
        watch: {
            data: {
                handler (val) {
                    this.config = JSON.parse(JSON.stringify(val))
                },
                deep: true,
                immediate: true
            }
        },
        methods: {
            clearError () {
                this.$refs.scanForm.clearError()
            },
            async save () {
                if (this.config.autoScan) {
                    await this.$refs.scanForm.validate()
                    const repoNameList = await this.$refs.repoConfig.getConfig()
                    const artifactRules = await this.$refs.artiConfig.getConfig()
                    this.$emit('save', {
                        autoScan: this.config.autoScan,
                        repoNameList,
                        artifactRules
                    })
                } else {
                    this.$emit('save', { autoScan: this.config.autoScan })
                }
            }
        }
    }
</script>
