<template>
    <canway-dialog
        v-model="show"
        :title="$t('cleanNodeTitle')"
        width="450"
        :height-num="311"
        @cancel="cancel">
        <bk-form class="mr10 repo-generic-form" :label-width="90" :rules="rules" ref="genericForm">
            <bk-form-item :label="$t('cleanNode')" :required="true" property="path" error-display-type="normal">
                <div v-if="displayPaths.length">
                    <div v-for="(item,index) in displayPaths " :key="index">
                        <bk-input v-if="item.isComplete" v-model="item.path" style="width: 260px" disabled="true" />
                        <bk-input v-else v-model="item.path" style="margin-bottom: 10px;width: 260px" disabled="true" />
                        <Icon name="right" v-if="item.isComplete" size="14" />
                        <div class="form-tip" v-if="item.isComplete" style="margin-bottom: 5px">{{ item.tip }}</div>
                    </div>
                </div>
                <div v-else>
                    <div v-for="(item,index) in paths " :key="index">
                        <bk-input v-if="item.isComplete" v-model="item.path" style="width: 260px" disabled="true" />
                        <bk-input v-else v-model="item.path" style="margin-bottom: 10px;width: 260px" disabled="true" />
                        <Icon name="right" v-if="item.isComplete" size="14" />
                        <div class="form-tip" v-if="item.isComplete" style="margin-bottom: 5px">{{ item.tip }}</div>
                    </div>
                </div>
            </bk-form-item>
            <bk-form-item :label="$t('deadline')" :required="true" property="date" error-display-type="normal">
                <bk-date-picker
                    v-model="date"
                    :value="value"
                    :clearable="false"
                    :type="'datetime'"
                    @change="resetStatus"
                    :disabled="doing && !isComplete"
                    :open="open"
                    @pick-success="handleOk"
                    style="margin-top: 10px;"
                >
                    <a slot="trigger" href="javascript:void(0)" @click="handleClick">
                        <template>{{value}}</template>
                    </a>
                </bk-date-picker>
                <div class="form-tip">{{$t('cleanFolderTip')}}</div>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click="cancel" v-if="!isComplete">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="loading" theme="primary" @click="calculateNode" v-if="!isComplete" :disabled="doing">{{$t('querySize')}}</bk-button>
            <bk-button class="ml10" :loading="loading" theme="primary" @click="submit" v-if="!isComplete" :disabled="doing">{{$t('cleanRepo')}}</bk-button>
            <bk-button class="ml10" theme="primary" @click="completeClean" v-if="isComplete">{{$t('complete')}}</bk-button>
        </template>
    </canway-dialog>
</template>

<script>
    import { mapActions } from 'vuex'
    import { convertFileSize, formatDate, formatNow } from '@repository/utils'
    export default {
        name: 'GenericCleanDialog',
        data () {
            return {
                show: false,
                loading: false,
                repoName: '',
                projectId: '',
                paths: [],
                displayPaths: [],
                date: new Date(),
                realDate: new Date(),
                rules: {
                },
                isComplete: false,
                doing: false,
                open: false,
                value: ''
            }
        },
        watch: {
            show: {
                handler (val) {
                    if (val) {
                        this.date = formatNow()
                        this.value = this.date
                    }
                },
                immediate: true
            }
        },
        methods: {
            ...mapActions([
                'cleanNode',
                'getFolderSizeBefore'
            ]),
            handleClick () {
                this.open = !this.open
            },
            handleOk () {
                this.open = false
            },
            cancel () {
                this.$emit('refresh')
                this.show = false
                this.doing = false
            },
            async submit () {
                let completeNum = 0
                if (this.doing) {
                    return
                }
                this.doing = true
                for (let i = 0; i < this.paths.length; i++) {
                    const path = this.projectId + '/' + this.repoName + this.paths[i].path
                    await this.cleanNode({
                        path: path,
                        date: this.date instanceof Date ? this.date.toISOString() : undefined
                    }).then((res) => {
                        this.paths[i].isComplete = true
                        this.paths[i].tip = this.$t('cleanDetail', { 0: res.deletedNumber, 1: convertFileSize(res.deletedSize) })
                        if (this.displayPaths.length === this.paths.length) {
                            this.displayPaths[i].isComplete = true
                            this.displayPaths[i].tip = this.$t('cleanDetail', { 0: res.deletedNumber, 1: convertFileSize(res.deletedSize) })
                        }
                        completeNum++
                    })
                }
                if (completeNum === this.paths.length) {
                    this.isComplete = true
                    this.doing = false
                    this.$refs.genericForm.clearError()
                }
            },
            async calculateNode () {
                if (this.doing) {
                    return
                }
                this.doing = true
                let completeNum = 0
                for (let i = 0; i < this.paths.length; i++) {
                    const path = this.projectId + '/' + this.repoName + this.paths[i].path
                    await this.getFolderSizeBefore({
                        path: path,
                        date: this.date instanceof Date ? this.date.toISOString() : undefined
                    }).then((res) => {
                        this.paths[i].isComplete = true
                        this.paths[i].tip = this.$t('preCleanDetail', { 0: res.subNodeWithoutFolderCount, 1: convertFileSize(res.size) })
                        if (this.displayPaths.length === this.paths.length) {
                            this.displayPaths[i].isComplete = true
                            this.displayPaths[i].tip = this.$t('preCleanDetail', { 0: res.subNodeWithoutFolderCount, 1: convertFileSize(res.size) })
                        }
                        completeNum++
                    })
                }
                if (completeNum === this.paths.length) {
                    this.doing = false
                    this.$refs.genericForm.clearError()
                }
            },
            completeClean () {
                this.show = false
                this.$emit('refresh')
                this.doing = false
            },
            resetStatus (date) {
                this.value = formatDate(date)
                if (this.doing === false && this.isComplete === true) {
                    this.isComplete = false
                    for (let i = 0; i < this.paths.length; i++) {
                        this.paths[i].isComplete = false
                        if (this.displayPaths.length === this.paths.length) {
                            this.displayPaths[i].isComplete = false
                        }
                    }
                }
            }
        }
    }
</script>

<style scoped>

</style>
