<template>
    <div class="create-node-container">
        <header class="create-node-header">
            <span>{{ `分发计划 > ${title}` }}</span>
            <div>
                <bk-button v-show="!disabled" theme="primary" @click="save" :loading="planForm.loading">
                    {{$t('save')}}
                </bk-button>
                <bk-button class="ml20" theme="default" @click="$router.back()">
                    {{$t('returnBack')}}
                </bk-button>
            </div>
        </header>
        <main class="create-node-main" v-bkloading="{ isLoading }">
            <bk-form class="plan-form" :label-width="100" :model="planForm" :rules="rules" ref="planForm">
                <bk-form-item label="计划名称" :required="true" property="name" error-display-type="normal">
                    <bk-input style="max-width:400px" v-model.trim="planForm.name" maxlength="32" :disabled="disabled"></bk-input>
                </bk-form-item>
                <bk-form-item label="同步类型" :required="true" property="replicaObjectType">
                    <bk-radio-group v-model="planForm.replicaObjectType" class="replica-type-radio-group" @change="changeReplicaObjectType">
                        <bk-radio-button
                            class="mr20"
                            v-for="type in replicaObjectTypeList"
                            :key="type.value"
                            :value="type.value"
                            :disabled="disabled">
                            <div class="replica-type-radio">
                                <label class="replica-type-label">{{ type.label }}</label>
                                <div class="mt5 replica-type-tip">{{ type.tip }}</div>
                                <div v-show="type.value === planForm.replicaObjectType" class="top-right-selected">
                                    <i class="devops-icon icon-check-1"></i>
                                </div>
                            </div>
                        </bk-radio-button>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item label="同步策略" :required="true">
                    <bk-radio-group v-model="planForm.executionStrategy">
                        <bk-radio class="mr20" value="IMMEDIATELY" :disabled="disabled">立即执行</bk-radio>
                        <bk-radio class="mr20" value="SPECIFIED_TIME" :disabled="disabled">指定时间</bk-radio>
                        <bk-radio class="mr20" value="CRON_EXPRESSION" :disabled="disabled">定时执行</bk-radio>
                        <bk-radio v-if="planForm.replicaObjectType === 'REPOSITORY'" class="mr20" value="REAL_TIME" :disabled="disabled">实时同步</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item v-if="planForm.executionStrategy === 'SPECIFIED_TIME'" label="时间" :required="true" property="time" error-display-type="normal">
                    <bk-date-picker
                        v-model="planForm.time"
                        type="datetime"
                        :disabled="disabled"
                        :options="{
                            disabledDate: (date) => date < new Date()
                        }">
                    </bk-date-picker>
                </bk-form-item>
                <bk-form-item v-else-if="planForm.executionStrategy === 'CRON_EXPRESSION'" label="cron表达式" :required="true" property="cron" error-display-type="normal">
                    <div v-if="disabled">{{ planForm.cron }}</div>
                    <Cron v-else v-model="planForm.cron" />
                </bk-form-item>
                <bk-form-item label="冲突策略" :required="true" property="conflictStrategy">
                    <bk-radio-group v-model="planForm.conflictStrategy">
                        <bk-radio class="mr20" value="SKIP" :disabled="disabled">跳过冲突</bk-radio>
                        <bk-radio class="mr20" value="OVERWRITE" :disabled="disabled">替换制品</bk-radio>
                        <bk-radio value="FAST_FAIL" :disabled="disabled">终止同步</bk-radio>
                    </bk-radio-group>
                    <div class="conflict-strategy-tips">
                        <span v-if="planForm.conflictStrategy === 'SKIP'">当目标节点存在相同制品时，跳过该制品同步，同步剩余制品</span>
                        <span v-else-if="planForm.conflictStrategy === 'OVERWRITE'">当目标节点存在相同制品时，覆盖原制品并继续执行计划</span>
                        <span v-else-if="planForm.conflictStrategy === 'FAST_FAIL'">当目标节点存在相同制品时，终止执行计划</span>
                    </div>
                </bk-form-item>
                <bk-form-item label="同步对象" :required="true" property="config" error-display-type="normal">
                    <template v-if="planForm.replicaObjectType === 'REPOSITORY'">
                        <repository-table
                            ref="planConfig"
                            :init-data="replicaTaskObjects"
                            :disabled="disabled"
                            @clearError="clearError">
                        </repository-table>
                    </template>
                    <template v-else-if="planForm.replicaObjectType === 'PACKAGE'">
                        <package-table
                            ref="planConfig"
                            :init-data="replicaTaskObjects"
                            :disabled="disabled"
                            @clearError="clearError">
                        </package-table>
                    </template>
                    <template v-else-if="planForm.replicaObjectType === 'PATH'">
                        <path-table
                            ref="planConfig"
                            :init-data="replicaTaskObjects"
                            :disabled="disabled"
                            @clearError="clearError">
                        </path-table>
                    </template>
                </bk-form-item>
                <bk-form-item label="目标节点" :required="true" property="remoteClusterIds" error-display-type="normal">
                    <bk-select
                        style="max-width:600px"
                        v-model="planForm.remoteClusterIds"
                        searchable
                        multiple
                        display-tag
                        :collapse-tag="false"
                        :disabled="disabled">
                        <bk-option v-for="option in clusterList.filter(v => v.type !== 'CENTER')"
                            :key="option.name"
                            :id="option.id"
                            :name="option.name">
                        </bk-option>
                    </bk-select>
                </bk-form-item>
                <bk-form-item :label="$t('description')">
                    <bk-input
                        style="max-width:640px"
                        v-model.trim="planForm.description"
                        type="textarea"
                        maxlength="200"
                        :disabled="disabled">
                    </bk-input>
                </bk-form-item>
            </bk-form>
        </main>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import Cron from '@/components/Cron'
    import repositoryTable from './repositoryTable'
    import packageTable from './packageTable'
    import pathTable from './pathTable'
    export default {
        name: 'createPlan',
        components: { Cron, repositoryTable, packageTable, pathTable },
        data () {
            return {
                isLoading: false,
                planForm: {
                    loading: false,
                    name: '',
                    executionStrategy: 'IMMEDIATELY',
                    replicaObjectType: 'REPOSITORY',
                    time: new Date(new Date().getTime() + 30 * 60 * 1000),
                    cron: '',
                    conflictStrategy: 'SKIP',
                    remoteClusterIds: [],
                    description: ''
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '计划名称',
                            trigger: 'blur'
                        }
                    ],
                    time: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + '时间',
                            trigger: 'blur'
                        },
                        {
                            validator: date => date > new Date(),
                            message: '当前时间已过期',
                            trigger: 'blur'
                        }
                    ],
                    cron: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + 'cron表达式',
                            trigger: 'blur'
                        }
                    ],
                    config: [
                        {
                            validator: () => {
                                return this.$refs.planConfig.getConfig()
                            },
                            message: '请完善同步对象信息',
                            trigger: 'blur'
                        }
                    ],
                    remoteClusterIds: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + '目标节点',
                            trigger: 'blur'
                        }
                    ]
                },
                replicaTaskObjects: []
            }
        },
        computed: {
            ...mapState(['clusterList']),
            projectId () {
                return this.$route.params.projectId
            },
            routeName () {
                return this.$route.name
            },
            title () {
                return this.$route.meta.title
            },
            disabled () {
                return this.routeName === 'planDetail'
            },
            replicaObjectTypeList () {
                return [
                    { label: '仓库', value: 'REPOSITORY', tip: '同步多个仓库' },
                    { label: '制品', value: 'PACKAGE', tip: '同步同一仓库下多个包' },
                    { label: '文件', value: 'PATH', tip: '同步同一仓库下多个文件' }
                ]
            }
        },
        created () {
            this.getRepoListAll({
                projectId: this.projectId
            })
            this.routeName !== 'createPlan' && this.handlePlanDetail()
        },
        methods: {
            ...mapActions([
                'getRepoListAll',
                'createPlan',
                'getPlanDetail',
                'updatePlan'
            ]),
            handlePlanDetail () {
                this.isLoading = true
                this.getPlanDetail({
                    key: this.$route.params.planId
                }).then(({
                    task: {
                        name,
                        replicaObjectType,
                        replicaType,
                        remoteClusters,
                        description,
                        setting: {
                            conflictStrategy,
                            executionStrategy,
                            executionPlan: { executeTime, cronExpression }
                        }
                    },
                    objects
                }) => {
                    this.planForm = {
                        ...this.planForm,
                        name,
                        executionStrategy: replicaType === 'REAL_TIME' ? 'REAL_TIME' : executionStrategy,
                        replicaObjectType,
                        ...(executeTime ? {
                            time: new Date(executeTime)
                        } : {}),
                        ...(cronExpression ? {
                            cron: cronExpression
                        } : {}),
                        conflictStrategy,
                        remoteClusterIds: remoteClusters.map(v => v.id),
                        description
                    }
                    this.replicaTaskObjects = objects
                }).finally(() => {
                    this.isLoading = false
                })
            },
            changeReplicaObjectType () {
                this.replicaTaskObjects = []
                this.planForm.executionStrategy === 'REAL_TIME' && (this.planForm.executionStrategy = 'IMMEDIATELY')
                this.clearError()
            },
            clearError () {
                this.$refs.planForm.clearError()
            },
            async save () {
                await this.$refs.planForm.validate()

                if (this.planForm.loading) return
                this.planForm.loading = true

                const replicaTaskObjects = await this.$refs.planConfig.getConfig()

                const body = {
                    name: this.planForm.name,
                    localProjectId: this.projectId,
                    replicaObjectType: this.planForm.replicaObjectType,
                    replicaTaskObjects,
                    replicaType: this.planForm.executionStrategy === 'REAL_TIME' ? 'REAL_TIME' : 'SCHEDULED',
                    setting: {
                        rateLimit: 0, // <=0不限速
                        includeMetadata: true, // 同步元数据
                        conflictStrategy: this.planForm.conflictStrategy,
                        errorStrategy: 'FAST_FAIL',
                        ...(this.planForm.executionStrategy !== 'REAL_TIME' ? {
                            executionStrategy: this.planForm.executionStrategy,
                            executionPlan: {
                                executeImmediately: this.planForm.executionStrategy === 'IMMEDIATELY',
                                ...(this.planForm.executionStrategy === 'SPECIFIED_TIME' ? {
                                    // executeTime: this.planForm.time.toISOString()
                                    // 后端需要,中国时区
                                    executeTime: new Date(this.planForm.time.getTime() + 8 * 3600 * 1000).toISOString().replace(/Z$/, '')
                                } : {}),
                                ...(this.planForm.executionStrategy === 'CRON_EXPRESSION' ? {
                                    cronExpression: this.planForm.cron
                                } : {})
                            }
                        } : {})
                    },
                    remoteClusterIds: this.planForm.remoteClusterIds,
                    enabled: true,
                    description: this.planForm.description
                }
                const request = this.routeName === 'createPlan'
                    ? this.createPlan({ body })
                    : this.updatePlan({ body: { ...body, key: this.$route.params.planId } })
                request.then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.title + this.$t('success')
                    })
                    this.$router.back()
                }).finally(() => {
                    this.planForm.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.create-node-container {
    height: 100%;
    .create-node-header {
        height: 50px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 14px;
        background-color: white;
    }
    .create-node-main {
        height: calc(100% - 70px);
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        overflow-y: auto;
        .plan-form {
            max-width: 1200px;
            .arrow-right-icon {
                position: relative;
                width: 20px;
                height: 20px;
                &:before {
                    position: absolute;
                    content: '';
                    width: 16px;
                    height: 5px;
                    margin: 8px 0;
                    border-width: 1px 0;
                    border-style: solid;
                }
                &:after {
                    position: absolute;
                    content: '';
                    width: 10px;
                    height: 10px;
                    margin-left: 7px;
                    margin-top: 6px;
                    border-width: 1px 1px 0 0;
                    border-style: solid;
                    transform: rotate(45deg);
                }
            }
            .conflict-strategy-tips {
                width: 400px;
                margin-top: 5px;
                padding-left: 10px;
                color: #979BA5;
                font-size: 12px;
                line-height: 24px;
                background-color: $bgLightColor;
            }
            .replica-type-radio-group {
                ::v-deep .bk-form-radio-button {
                    .bk-radio-button-text {
                        height: auto;
                        line-height: initial;
                        padding: 0;
                    }
                    .bk-radio-button-input:disabled+.bk-radio-button-text {
                        border-left: 1px solid currentColor;
                    }
                }
                .replica-type-radio {
                    position: relative;
                    padding: 10px;
                    width: 155px;
                    height: 60px;
                    text-align: left;
                    .replica-type-label {
                        font-weight: bold;
                        color: $fontWeightColor;
                    }
                    .replica-type-tip {
                        font-size: 12px;
                        color: #979BA5;
                    }
                    .top-right-selected {
                        position: absolute;
                        top: 0;
                        right: 0;
                        border-width: 16px;
                        border-style: solid;
                        border-color: $primaryColor $primaryColor transparent transparent;
                        i {
                            position: absolute;
                            margin-top: -12px;
                            font-size: 12px;
                            color: white;
                        }
                    }
                }
            }
            .plan-object-container {
                display: grid;
                grid-template: auto / 1fr 1fr;
                margin: 5px 0 20px;
            }
        }
    }
}
</style>
