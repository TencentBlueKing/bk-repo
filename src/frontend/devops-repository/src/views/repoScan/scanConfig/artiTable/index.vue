<template>
    <div class="arti-table-container">
        <bk-radio-group v-model="showAddBtn">
            <bk-radio :disabled="disabled" :value="false">所有制品的最新版本</bk-radio>
            <bk-radio :disabled="disabled" class="mt10" :value="true">
                <span>满足规则的制品</span>
                <bk-button v-show="showAddBtn && !disabled" class="ml10" icon="plus" @click="addRule()">添加规则</bk-button>
            </bk-radio>
        </bk-radio-group>
        <div v-show="showAddBtn" class="rule-list">
            <component :is="scanType.includes('GENERIC') ? `generic-rule` : 'package-rule'"
                class="mt10"
                v-for="(rule, ind) in defaultRules"
                :key="ind"
                :disabled="disabled"
                v-bind="rule"
                @change="r => defaultRules.splice(ind, 1, r)"
                @delete="defaultRules.splice(ind, 1)">
            </component>
        </div>
    </div>
</template>
<script>
    import packageRule from './packageRule'
    import genericRule from './genericRule'
    export default {
        name: 'artiTable',
        components: {
            packageRule,
            genericRule
        },
        props: {
            initData: {
                type: Array,
                default: () => []
            },
            disabled: {
                type: Boolean,
                default: false
            },
            scanType: String
        },
        data () {
            return {
                showAddBtn: false,
                defaultRules: []
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            initData: {
                handler (data) {
                    this.defaultRules = data.map(r => {
                        return r.rules?.reduce((target, item) => {
                            target[item.field] = {
                                ...item,
                                value: item.operation === 'MATCH' ? item.value.replace(/^\*(.*)\*$/, '$1') : item.value
                            }
                            return target
                        }, {})
                    })
                    this.showAddBtn = Boolean(data.length)
                },
                immediate: true,
                deep: true
            }
        },
        methods: {
            getConfig () {
                return new Promise((resolve, reject) => {
                    if (!this.showAddBtn) resolve([])
                    else {
                        const rules = this.defaultRules
                            .map(rs => {
                                return {
                                    relation: 'AND',
                                    rules: Object.values(rs).map(i => {
                                        return i.value && {
                                            ...i,
                                            value: i.operation === 'MATCH' ? `*${i.value}*` : i.value
                                        }
                                    }).filter(Boolean)
                                }
                            })
                            .filter(rule => Boolean(rule.rules.length))
                        rules.length ? resolve(rules) : reject(new Error())
                    }
                })
            },
            addRule () {
                this.defaultRules.push({})
            }
        }
    }
</script>
<style lang="scss" scoped>
.arti-table-container {
    ::v-deep .bk-form-radio {
        height: 32px;
        display: flex;
        align-items: center;
        .bk-radio-text {
            display: flex;
            align-items: center;
        }
    }
    .rule-list {
        &:before {
            content: '不填写则跳过规则';
            display: block;
            margin-bottom: -10px;
            color: var(--fontSubsidiaryColor);
        }
    }
}
</style>
