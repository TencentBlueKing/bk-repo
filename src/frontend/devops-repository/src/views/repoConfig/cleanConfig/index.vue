<template>
    <bk-form class="clean-config-container" :label-width="166" :model="config" :rules="rules" ref="cleanForm">
        <bk-form-item :label="$t('autoCleanup')">
            <bk-switcher v-model="config.autoClean" size="small" theme="primary" @change="clearError"></bk-switcher>
            <span class="auto-clean-info">{{$t('autoCleanInfo')}}</span>
        </bk-form-item>
        <bk-form-item v-if="repoType !== 'generic'" :label="$t('minimumVersion')" required property="reserveVersions" error-display-type="normal">
            <bk-input class="w250" v-model="config.reserveVersions" :disabled="!config.autoClean"></bk-input>
            <div class="form-tip">{{$t('cleanTip')}}</div>
        </bk-form-item>
        <bk-form-item v-if="repoType !== 'generic'" :label="$t('minRetentionTime')" required property="reserveDays" error-display-type="normal">
            <bk-input class="w250" v-model="config.reserveDays" :disabled="!config.autoClean"></bk-input>
        </bk-form-item>
        <bk-form-item :label="repoType === 'generic' ? $t('genericRetention') : $t('retentionRules')">
            <template v-if="repoType !== 'generic'">
                <bk-button :disabled="!config.autoClean" icon="plus" @click="addRule()">{{$t('addRules')}}</bk-button>
                <div class="form-tip">
                    {{$t('notGenericTip')}}
                </div>
                <div class="rule-list">
                    <package-clean-rule
                        class="mt10"
                        v-for="(rule, ind) in config.rules"
                        :key="ind"
                        :disabled="!config.autoClean"
                        v-bind="rule"
                        @change="(r) => onChange(r,ind)"
                        @delete="config.rules.splice(ind, 1)">
                    </package-clean-rule>
                </div>
            </template>
            <template v-else>
                <div class="flex-align-center">
                    <span class="generic-rule-icon"><i class="bk-icon icon-question-circle" v-bk-tooltips="htmlConfig"></i></span>
                    <div id="generic-rule-info">
                        <p>{{$t('genericRuleInfo1')}}</p>
                        <p>{{$t('genericRuleInfo2')}}</p>
                        <p>{{$t('genericRuleInfo3')}}</p>
                        <p>{{$t('genericRuleInfo4')}}</p>
                    </div>
                    <bk-button :disabled="!config.autoClean" icon="plus" @click="addGenericCatalog()">{{$t('addGenericCatalog')}}</bk-button>
                </div>
                <div v-for="(rule, index) in genericConfig.rules"
                    :key="index">
                    <div class="generic-config-container">
                        <bk-form
                            ref="genericFormRefs"
                            label-width="166"
                            form-type="inline"
                            :model="genericConfig"
                            :rules="genericRules">
                            <bk-form-item
                                :label="$t('genericRulesIndexInfo') + (index + 1)"
                                :rules="genericRules.catalogValue"
                                :property="`rules.${index }.catalogValue`"
                                error-display-type="normal">
                                <bk-popover
                                    :disabled="!config.autoClean || index === 0"
                                    placement="top-end"
                                    trigger="click"
                                    theme="light"
                                    ref="genericPopoverRefs"
                                    :max-width="250"
                                    :width="250"
                                    :arrow="false"
                                    flip-on-update
                                    :distance="0">
                                    <bk-input
                                        class="w250"
                                        readonly
                                        :placeholder="$t('genericRulePlaceHolder')"
                                        :disabled="!config.autoClean || index === 0"
                                        right-icon="bk-icon icon-angle-down"
                                        v-model="rule.catalogValue"
                                        v-bk-tooltips="{ content: rule.catalogValue ,disabled: ((rule.catalogValue && rule.catalogValue.length) || 0) < 20 }"
                                        @mouseover.native="rule.clearable = true"
                                        @mouseout.native="rule.clearable = false"
                                        :clearable="rule.clearable ? true : false"
                                        @clear="onClearCatalog(index)"></bk-input>
                                    <div v-if="index !== 0" slot="content" class="generic-rule-tree">
                                        <vue-tree
                                            :key="index"
                                            ref="genericTreeRefs"
                                            :data="rule.treeData"
                                            show-checkbox
                                            node-key="fullPath"
                                            highlight-current
                                            :render-after-expand="false"
                                            :props="defaultProps"
                                            :expand-on-click-node="false"
                                            check-strictly
                                            @node-expand="(data,node) => handleNodeExpand (index,data,node)"
                                            @check="(data,checked) => handleCheckChange(index,data,checked)">
                                            <template slot-scope="{ node }">
                                                <span v-bk-tooltips="node.label" class="text-overflow">
                                                    {{ node.label }}
                                                </span>
                                            </template>
                                        </vue-tree>
                                        <bk-button
                                            class="generic-rule-tree-btn"
                                            :disabled="!config.autoClean"
                                            theme="primary"
                                            :title="$t('confirm')" @click="onConfirmCheck(index)">
                                            {{$t('confirm')}}
                                        </bk-button>
                                    </div>
                                </bk-popover>
                            </bk-form-item>
                            <bk-form-item :label="$t('genericFileTempTime')" :rules="genericRules.reserveDays"
                                :property="`rules.${index}.reserveDays`" error-display-type="normal">
                                <!-- rule.rules.length 表明当前设置的保留规则的数量 -->
                                <!-- 当设置保留规则为全部时，为空对象，经过下方过滤之后会去除空对象，所以如果保留规则设置的存在全部，遍历之后的数量会和之前的数量不一致 -->
                                <!-- rule.rules.filter(v => Object.keys(v).length).length 表明当前设置的保留规则去除全部(保留规则为全部)的数量 -->
                                <bk-input
                                    class="reserve-time-input"
                                    type="number"
                                    :max="maxReserveDay"
                                    :min="1"
                                    :precision="0"
                                    v-model="rule.reserveDays"
                                    :disabled="!config.autoClean || rule.rules.length !== rule.rules.filter(v => Object.keys(v).length).length">
                                </bk-input>
                                <span class="ml10 mr10">{{$t('day')}}</span>
                            </bk-form-item>
                            <bk-form-item>
                                <bk-button :disabled="!config.autoClean" icon="plus" @click="onAddGenericRule(index)">{{$t('addRetentionRules')}}</bk-button>
                            </bk-form-item>
                        </bk-form>
                        <bk-icon class="ml5 hover-btn" v-if="index !== 0" type="close-circle" :disabled="!config.autoClean" @click="onDeleteGenericCatalog(index)" />
                    </div>
                    <genericCleanRule
                        class="generic-config-rules"
                        v-for="(sonRule, ind) in rule.rules"
                        :key="ind"
                        :disabled="!config.autoClean"
                        v-bind="sonRule"
                        @change="(r) => onChangeGeneric(r,ind,index)"
                        @delete="genericConfig.rules[index].rules.splice(ind, 1)">
                    </genericCleanRule>
                </div>
            </template>
        </bk-form-item>
        <bk-form-item>
            <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
        </bk-form-item>
    </bk-form>
</template>
<script>
    import packageCleanRule from './packageCleanRule'
    import genericCleanRule from './genericCleanRule.vue'
    import VueTree from '@cw-devops/vue-tree'
    import '@cw-devops/vue-tree/dist/vue-tree.css'
    import customSubHeader from '@repository/utils/infoConfirm'
    import { mapActions } from 'vuex'
    export default {
        name: 'cleanConfig',
        components: {
            packageCleanRule,
            VueTree,
            genericCleanRule
        },
        props: {
            baseData: Object
        },
        data () {
            return {
                loading: false,
                // 非二进制仓库设置(autoClean通用，可以对下面的二进制仓库是否禁用限制)
                config: {
                    autoClean: false,
                    reserveVersions: 20,
                    reserveDays: 30,
                    rules: []
                },
                //  二进制仓库下制品配置单个目录下树结构数据(所有目录通用)
                defaultProps: {
                    children: 'children',
                    label: 'name',
                    disabled: 'disabled'
                },
                maxReserveDay: 365, // 文件最大保留天数
                // 二进制仓库制品规则配置
                genericConfig: {
                    rules: []
                },
                // 非二进制仓库的表单验证规则
                rules: {
                    reserveVersions: [
                        {
                            regex: /^[0-9]+$/,
                            message: this.$t('nonNegativeIntegerTip'),
                            trigger: 'blur'
                        }
                    ],
                    reserveDays: [
                        {
                            regex: /^[0-9]+$/,
                            message: this.$t('nonNegativeIntegerTip'),
                            trigger: 'blur'
                        }
                    ]
                },
                genericRules: {
                    catalogValue: [
                        {
                            required: true,
                            message: this.$t('genericRulePlaceHolder'),
                            trigger: 'blur'
                        }

                    ],
                    reserveDays: [
                        {
                            required: true,
                            message: this.$t('genericFileRetentionTimeTips'),
                            trigger: 'blur'
                        }
                    ]
                },
                htmlConfig: {
                    allowHtml: true,
                    theme: 'light',
                    width: 800,
                    content: '#generic-rule-info',
                    placement: 'left'
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
                    if (val.repoType === 'generic') {
                        const {
                            autoClean = false,
                            rule = { rules: [] }
                        } = val.configuration.cleanStrategy
                        this.config.autoClean = autoClean
                        const rules = rule.rules.find(r => r.rules)?.rules || []
                        this.genericConfig.rules = rules.map(r => {
                            const pathValue = r.rules.find(item => item.field === 'path')?.value || ''
                            const ruleArr = r.rules.find(item => item.rules)?.rules || []
                            return {
                                catalogValue: pathValue === '/' ? this.$t('genericRetentionRuleAllInfo') : pathValue,
                                reserveDays: r.rules.find(item => item.field === 'reserveDays')?.value || 30,
                                clearable: false,
                                treeData: [{
                                    disabled: true,
                                    name: this.repoName,
                                    folder: true,
                                    fullPath: '/',
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    children: [{ name: '', fullPath: '/000000' }]
                                }],
                                rules: ruleArr.map(r => {
                                    const target = {}
                                    target[r.field] = {
                                        ...r,
                                        value: r.operation === 'MATCH' ? r.value.replace(/^\*(.*)\*$/, '$1') : r.value
                                    }
                                    return target
                                })
                            }
                        })
                    } else {
                        const {
                            autoClean = false,
                            reserveVersions = 20,
                            reserveDays = 30,
                            rule = { rules: [] }
                        } = val.configuration.cleanStrategy
                        this.config = { ...this.config, autoClean, reserveVersions, reserveDays }
                        const rules = rule?.rules?.find(r => r.rules)?.rules || []
                        this.config.rules = rules.map(r => {
                            return r.rules?.reduce((target, item) => {
                                target[item.field] = {
                                    ...item,
                                    value: item.operation === 'MATCH' ? item.value.replace(/^\*(.*)\*$/, '$1') : item.value
                                }
                                return target
                            }, {})
                        })
                    }
                },
                deep: true,
                immediate: true
            }
        },
        methods: {
            ...mapActions(['updateRepoInfo', 'getFolderList']),
            addRule () {
                if (this.config.autoClean) {
                    this.config.rules.push({})
                }
            },
            clearError () {
                this.$refs.cleanForm.clearError()
            },
            // 非二进制仓库，某个规则被改变事件
            onChange (r, ind) {
                for (const key in r) {
                    key && this.config.rules.splice(ind, 1, r)
                }
            },
            handleNodeExpand (index, data) {
                // 向当前选择节点添加子节点之前先将之前默认添加的数据清除，否则会出现数据重复或无用数据
                data && (data.children = [])
                // 根据产品要求，generic仓库的清理设置目录限制层级为10级，10级之后不再支持展开和选择
                if (data && data.fullPath && (this.$refs.genericTreeRefs[index - 1].getNode(data)?.level || 1) < 10 && data.fullPath.split('/').length < 10) {
                    this.getFolderList({
                        projectId: data?.projectId || this.projectId,
                        repoName: data?.repoName || this.repoName,
                        fullPath: data?.fullPath || '/'
                    }).then(res => {
                        if (res.records.length === 0) {
                            this.$refs.genericTreeRefs[index - 1].updateKeyChildren(data.fullPath, [])
                        } else {
                            const secondChildren = res.records.map((item) => {
                                const node = {
                                    name: item.name,
                                    folder: item.folder,
                                    fullPath: item.fullPath,
                                    projectId: item.projectId,
                                    repoName: item.repoName,
                                    children: []
                                }
                                if (node.folder) {
                                    // 如果当前节点是文件夹，则默认为其添加一个子节点，让其显示左边的展开图标按钮
                                    node.children = [{ name: '', fullPath: '/000000' }]
                                }
                                return node
                            })
                            if (index !== 0) {
                                this.$refs.genericTreeRefs[index - 1].updateKeyChildren(data.fullPath, secondChildren)
                            }
                        }
                        if (this.genericConfig.rules[index].catalogValue) {
                            this.$refs.genericTreeRefs[index - 1].setChecked(this.genericConfig.rules[index].catalogValue, true)
                        }
                    })
                }
            },

            handleCheckChange (index, data) {
                const checkArr = this.$refs.genericTreeRefs[index - 1].getCheckedKeys() || []
                const currentCheck = checkArr.find((item) => item === data.fullPath)
                if (checkArr.length > 1) {
                    // 此时证明是勾选了新的节点
                    this.$refs.genericTreeRefs[index - 1].setCheckedKeys([currentCheck], true)
                }
            },
            // 点击了树目录的确定按钮
            onConfirmCheck (index) {
                const allCheck = this.genericConfig.rules.map(item => item.catalogValue)
                const checkArr = this.$refs.genericTreeRefs[index - 1].getCheckedKeys() || []
                if (allCheck.includes(checkArr?.[0])) {
                    this.$bkMessage({
                        message: this.$t('checkDirectoryRepeatTip'),
                        theme: 'error'
                    })
                } else {
                    this.$set(this.genericConfig.rules[index], 'catalogValue', checkArr?.[0] || '')
                    // 将当前popover隐藏
                    this.$refs.genericPopoverRefs[index].hideHandler()
                    // 重新校验当前表单，解决因为校验规则是blur 导致的改变输入框的值后校验错误提示依旧存在的问题
                    this.$refs.genericFormRefs[index].validate()
                }
            },
            // 清除当前文件目录的值，因为产品要求的第一个目录是当前仓库，所以此时的树是比文件目录总数少了一个的
            onClearCatalog (index) {
                this.$refs.genericTreeRefs[index - 1].setCheckedKeys([], false)
            },
            // 二进制仓库下添加新的文件目录
            addGenericCatalog () {
                if (this.config.autoClean) {
                    const ruleObj = {
                        catalogValue: '',
                        clearable: false,
                        reserveDays: 30,
                        treeData: [{
                            disabled: true,
                            name: this.repoName,
                            folder: true,
                            fullPath: '/',
                            projectId: this.projectId,
                            repoName: this.repoName,
                            children: [{ name: '', fullPath: '/000000' }]
                        }],
                        rules: []
                    }
                    if (this.genericConfig.rules.length === 0) {
                        ruleObj.catalogValue = this.$t('genericRetentionRuleAllInfo')
                    }
                    this.genericConfig.rules.push(ruleObj)
                }
            },
            // 二进制仓库下删除配置的某个文件目录
            onDeleteGenericCatalog (index) {
                if (this.config.autoClean) {
                    const header = customSubHeader({
                        theme: 'warning',
                        subTitle: this.$t('deleteDirectoryConfirmTip')
                    })
                    this.$bkInfo({
                        subHeader: header,
                        theme: 'warning',
                        title: this.$t('deleteDirectory'),
                        confirmFn: () => {
                            this.genericConfig.rules.splice(index, 1)
                        }
                    })
                }
            },
            // 二进制仓库某个目录下某个规则被改变事件
            onChangeGeneric (r, ind, index) {
                for (const key in r) {
                    key && this.genericConfig.rules[index].rules.splice(ind, 1, r)
                }
                // 此时选择的是空对象，即全部,此时也需要重新改变当前规则，从而触发子组件内部的watch去改变页面
                if (Object.keys(r).length === 0) {
                    this.genericConfig.rules[index].rules.splice(ind, 1, r)
                }
            },
            // 二进制仓库添加规则按钮
            onAddGenericRule (index) {
                this.genericConfig.rules[index].rules.push({})
            },
            async save () {
                // 自动清理规则开启的时候才去校验制品目录的规则是否正确
                const { autoClean } = this.config
                let rules
                let configurationObj = {}
                if (this.repoType === 'generic') {
                    if (this.$refs.genericFormRefs && this.$refs.genericFormRefs.length) {
                        await Promise.all(this.$refs.genericFormRefs.map((item) => {
                            return item.validate()
                        }))
                    }
                    // 组装值
                    rules = this.genericConfig.rules
                    rules = rules.map(rs => {
                        delete rs.treeData
                        return {
                            relation: 'AND',
                            rules: [
                                {
                                    field: 'path',
                                    value: rs.catalogValue === this.$t('genericRetentionRuleAllInfo') ? '/' : rs.catalogValue,
                                    operation: 'REGEX'
                                },
                                {
                                    field: 'reserveDays',
                                    value: isNaN(Number(rs.reserveDays)) ? 30 : Number(rs.reserveDays),
                                    operation: 'LTE'
                                },
                                {
                                    rules: Object.values(rs.rules).map(i => {
                                        if (Object.values(i).length === 0) {
                                            // 此时表明传值为空对象，即此时选择了全部
                                            return {
                                                field: 'id',
                                                value: 'null',
                                                operation: 'NE'
                                            }
                                        } else {
                                            const item = Object.values(i)?.[0]
                                            return item?.field.replace(/^metadata\./, '') && item?.value && {
                                                ...item,
                                                value: item?.operation === 'MATCH' ? `*${item?.value}*` : item?.value
                                            }
                                        }
                                    }).filter(Boolean),
                                    relation: 'OR'
                                }
                            ]

                        }
                    }).filter(rs => rs.rules.length)
                    configurationObj = {
                        ...this.baseData.configuration,
                        cleanStrategy: {
                            autoClean,
                            rule: {
                                relation: 'AND',
                                rules: rules.length
                                    ? [{
                                           field: 'projectId',
                                           value: this.projectId,
                                           operation: 'EQ'
                                       },
                                       {
                                           field: 'repoName',
                                           value: this.repoName,
                                           operation: 'EQ'
                                       },
                                       {
                                           relation: 'OR',
                                           rules
                                       }].filter(Boolean)
                                    : []
                            }
                        }
                    }
                } else {
                    // 非二进制仓库
                    await this.$refs.cleanForm && this.$refs.cleanForm.validate()
                    const { reserveVersions, reserveDays } = this.config
                    rules = this.config.rules
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
                    configurationObj = {
                        ...this.baseData.configuration,
                        cleanStrategy: {
                            autoClean,
                            reserveVersions,
                            reserveDays,
                            rule: {
                                relation: 'AND',
                                rules: rules.length
                                    ? [{
                                        relation: 'OR',
                                        rules
                                    }].filter(Boolean)
                                    : []
                            }
                        }
                    }
                }

                this.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body: {
                        configuration: configurationObj
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
    max-width: 1124px;
}
.generic-tip{
    font-size: 12px;
    line-height: initial;
    color: var(--fontSubsidiaryColor);
    margin: 0 0 0 8px;
}
.generic-config-container{
    display: flex;
    margin: 10px 0;
}
.generic-config-rules{
    margin: 0 0 0 166px;
}
.generic-rule-icon{
    margin: 0 5px 0 -15px;
}
.generic-rule-tree{
    max-height: 200px;
    overflow-y: auto;
}
.generic-rule-tree-btn{
    float: right;
    margin:0 10px 0 0;
}
.auto-clean-info{
    vertical-align: middle;
    margin-left: 40px;
}
.reserve-time-input{
    width: 80px;
}
::v-deep .bk-form-input{
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
}
::v-deep .bk-form.bk-inline-form .bk-form-item .bk-label{
    // 因为bk-form的行内表单会导致width变为auto，且是 !import的,此时的!import不可删除
    width: 166px !important;
}
// 产品要求：此处的选择目录虽然是只读的，但样式要和普通输入框一致
::v-deep .bk-form-input[readonly]{
    background-color: inherit !important;
    color: inherit !important;
}
// 此时需要保证全仓库这个目录的禁用样式，不设置的话会被上面改变的只读样式修改，不符合产品要求
::v-deep .bk-form-input[disabled]{
    background-color: #fafbfd!important;
    color: #8797aa!important;
}
</style>
