<template>
    <div class="cron-container w180">
        <div class="flex-align-center" @click="showMain = !showMain">
            <bk-input class="cron-display" :value="value" readonly="readonly"></bk-input>
            <Icon class="cron-icon" name="cron" size="32" />
        </div>
        <div v-if="showMain" class="cron-main" :style="{ 'width': `${currentLanguage === 'zh-cn' ? 490 : 590}px` }">
            <bk-radio-group style="display:flex;" v-model="mode">
                <bk-radio value="manual">{{ $t('manualInput')}}</bk-radio>
                <bk-radio value="ui">{{ $t('autoEdit') }}</bk-radio>
            </bk-radio-group>
            <div v-if="mode === 'manual'" class="mt10">
                <bk-input v-model="manualVal" @blur="manualChange"></bk-input>
            </div>
            <bk-tab v-else class="cron-tab" type="unborder-card" :active.sync="tabName">
                <bk-tab-panel
                    v-for="tab in ['second', 'minute', 'hour', 'day', 'month', 'week', 'year']"
                    :key="tab"
                    :label="$t(`cron.${tab}`)"
                    :name="tab"
                    :disabled="(tab === 'day' && cron.week.type !== 'every') || (tab === 'week' && cron.day.type !== 'every')">
                    <bk-radio-group v-model="cron[tab].type" @change="uiChange">
                        <bk-radio
                            v-for="type in ['every', 'interval', ...(tab === 'year' ? [] : ['circle', 'enumeration'])]"
                            :key="tab + type"
                            class="cron-radio-item"
                            :value="type">
                            <span class="mr20">{{ $t(`cron.${type}`, [$t(`cron.${tab === 'week' ? 'day' : tab}`)]) }}</span>
                            <template v-if="type === 'interval' && cron[tab].type === 'interval'">
                                <span>{{ $t('cron.from') }}</span>
                                <bk-select style="width: 100px;margin: 0 5px;"
                                    v-model="cron[tab][type][0]"
                                    @change="uiChange"
                                    @click.native.stop.prevent="() => {}">
                                    <bk-option
                                        v-for="option in cron[tab].enumerationList.map(v => ({
                                            id: v,
                                            name: tab === 'week' ? $t(`cron.${v}`) : v + $t('space') + $t(`cron.${tab}`)
                                        }))"
                                        :key="option.id"
                                        :id="option.id"
                                        :name="option.name">
                                    </bk-option>
                                </bk-select>
                                <span>{{ $t('cron.to') }}</span>
                                <bk-select style="width: 100px;margin: 0 5px;"
                                    v-model="cron[tab][type][1]"
                                    @change="uiChange"
                                    @click.native.stop.prevent="() => {}">
                                    <bk-option
                                        v-for="option in cron[tab].enumerationList.map(v => ({
                                            id: v,
                                            name: tab === 'week' ? $t(`cron.${v}`) : v + $t('space') + $t(`cron.${tab}`)
                                        }))"
                                        :key="option.id"
                                        :id="option.id"
                                        :name="option.name">
                                    </bk-option>
                                </bk-select>
                                <span>{{ $t('intervalMsg') }}</span>
                            </template>
                            <template v-else-if="type === 'circle' && cron[tab].type === 'circle' && tab !== 'year'">
                                <bk-select style="width: 108px;margin: 0 5px;"
                                    v-model="cron[tab][type][0]"
                                    @change="uiChange"
                                    @click.native.stop.prevent="() => {}">
                                    <bk-option
                                        v-for="option in cron[tab].enumerationList.map(v => ({
                                            id: v,
                                            name: tab === 'week' ? $t(`cron.${v}`) : v + $t('space') + $t(`cron.${tab}`)
                                        }))"
                                        :key="option.id"
                                        :id="option.id"
                                        :name="option.name">
                                    </bk-option>
                                </bk-select>
                                <span>{{ $t('circleMsg1') }}</span>
                                <bk-select style="width: 100px;margin: 0 5px;"
                                    v-model="cron[tab][type][1]"
                                    @change="uiChange"
                                    @click.native.stop.prevent="() => {}">
                                    <bk-option
                                        v-for="option in cron[tab].enumerationList.map(v => ({
                                            id: v,
                                            name: v + $t('space') + $t(`cron.${tab === 'week' ? 'day' : tab}`)
                                        }))"
                                        :key="option.id"
                                        :id="option.id"
                                        :name="option.name">
                                    </bk-option>
                                </bk-select>
                                <span>{{ $t('circleMsg2') }}</span>
                            </template>
                            <template v-else-if="type === 'enumeration' && cron[tab].type === 'enumeration' && tab !== 'year'">
                                <bk-tag-input
                                    style="width:300px"
                                    v-model="cron[tab][type]"
                                    :allow-create="tab === 'year'"
                                    trigger="focus"
                                    separator=","
                                    :create-tag-validator="tag => {
                                        return Number(tag) && tag >= new Date().getFullYear()
                                    }"
                                    :title="cron[tab][type]"
                                    :list="cron[tab].enumerationList.map(v => ({
                                        id: v,
                                        name: tab === 'week' ? $t(`cron.${tab}`) + $t(`cron.${v}`) : v + $t(`cron.${tab}`)
                                    }))">
                                    @change="uiChange"
                                    @click.native.stop.prevent="() => {}"
                                </bk-tag-input>
                            </template>
                        </bk-radio>
                    </bk-radio-group>
                </bk-tab-panel>
            </bk-tab>
        </div>
    </div>
</template>
<script>
    export default {
        name: 'cron',
        model: {
            prop: 'value',
            event: 'change'
        },
        props: {
            value: {
                type: String,
                default: ''
            }
        },
        data () {
            return {
                tabName: 'second',
                showMain: false,
                mode: 'manual', // manual: 手动; ui: 界面
                manualVal: '',
                cron: {
                    second: {
                        type: 'every',
                        every: '*',
                        interval: [0, 1],
                        circle: [0, 1],
                        enumeration: [],
                        enumerationList: new Array(60).fill(1).map((v, index) => index.toString())
                    },
                    minute: {
                        type: 'every',
                        every: '*',
                        interval: [0, 1],
                        circle: [0, 1],
                        enumeration: [],
                        enumerationList: new Array(60).fill(1).map((v, index) => index.toString())
                    },
                    hour: {
                        type: 'every',
                        every: '*',
                        interval: [0, 1],
                        circle: [0, 1],
                        enumeration: [],
                        enumerationList: new Array(24).fill(1).map((v, index) => index.toString())
                    },
                    day: {
                        type: 'every',
                        every: '*',
                        interval: [1, 2],
                        circle: [1, 1],
                        enumeration: [],
                        enumerationList: new Array(31).fill(1).map((v, index) => (index + 1).toString())
                    },
                    month: {
                        type: 'every',
                        every: '*',
                        interval: [1, 2],
                        circle: [1, 1],
                        enumeration: [],
                        enumerationList: new Array(12).fill(1).map((v, index) => (index + 1).toString())
                    },
                    week: {
                        type: 'every',
                        every: '?',
                        interval: [1, 2],
                        circle: [1, 1],
                        enumeration: [],
                        enumerationList: new Array(7).fill(1).map((v, index) => (index + 1).toString())
                    },
                    year: {
                        type: 'every',
                        every: '*',
                        interval: [new Date().getFullYear(), new Date().getFullYear() + 1],
                        circle: [new Date().getFullYear(), 1],
                        enumeration: [],
                        enumerationList: new Array(10).fill(1).map((v, index) => (index + new Date().getFullYear()).toString())
                    }
                }
            }
        },
        computed: {
            uiVal () {
                const joinAt = {
                    every: '',
                    interval: '-',
                    circle: '/',
                    enumeration: ','
                }
                if (this.cron.day.type === 'every' && this.cron.week.type !== 'every') {
                    this.cron.day.every = '?'
                } else if (this.cron.week.type === 'every' && this.cron.day.type !== 'every') {
                    this.cron.week.every = '?'
                } else if (this.cron.week.type === 'every' && this.cron.day.type === 'every') {
                    this.cron.day.every = '*'
                    this.cron.week.every = '?'
                }
                return ['second', 'minute', 'hour', 'day', 'month', 'week', 'year'].map(item => {
                    const obj = this.cron[item]
                    return Array.from(obj[obj.type]).join(joinAt[obj.type])
                }).join(' ')
            }
        },
        watch: {
            showMain (show) {
                if (show) {
                    this.mode = 'manual'
                    this.manualVal = this.value
                }
            },
            mode (mode) {
                if (mode === 'manual') {
                    this.manualChange()
                } else if (mode === 'ui') {
                    this.uiChange()
                }
            }
        },
        methods: {
            manualChange () {
                this.$emit('change', this.manualVal)
            },
            uiChange () {
                this.$emit('change', this.uiVal)
            }
        }
    }
</script>
<style lang="scss" scoped>
.cron-container {
    position: relative;
    .cron-display {
        cursor: pointer;
        ::v-deep .bk-form-input[readonly] {
            cursor: inherit;
        }
    }
    .cron-icon {
        padding: 5px;
        margin-left: -1px;
        border: 1px solid var(--borderWeightColor);
        cursor: pointer;
    }
    .cron-main {
        position: absolute;
        // width: 580px;
        right: 0px;
        top: 35px;
        padding: 20px;
        z-index: 99;
        box-shadow: 0 3px 6px rgba(51, 60, 72, 0.12);
        background-color: white;
        .cron-tab {
            ::v-deep .bk-tab-section {
                padding: 0 20px;
            }
            ::v-deep .bk-tab-label-item {
                min-width: 60px;
            }
            .cron-radio-item {
                display: flex;
                align-items: center;
                margin-top: 20px;
                height: 32px;
                line-height: inherit;
                ::v-deep .bk-radio-text {
                    display: flex;
                    align-items: center;
                    height: 32px;
                    flex-shrink: 0;
                }
            }
        }
    }
    &:hover {
        .cron-icon {
            color: var(--primaryColor);
        }
    }
}
</style>
