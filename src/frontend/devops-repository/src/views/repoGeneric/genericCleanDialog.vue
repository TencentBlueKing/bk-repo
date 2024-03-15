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
                <bk-date-picker v-model="date" :clearable="false" :type="'datetime'" @change="resetStatus" :disabled="doing && !isComplete"></bk-date-picker>
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
    import { convertFileSize } from '@repository/utils'
    import moment from 'moment'
    export default {
        name: 'genericCleanDialog',
        data () {
            return {
                show: false,
                loading: false,
                repoName: '',
                projectId: '',
                paths: [],
                displayPaths: [],
                date: new Date(),
                rules: {
                },
                isComplete: false,
                doing: false
            }
        },
        methods: {
            ...mapActions([
                'cleanNode',
                'getFolderSizeBefore'
            ]),
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
                if (this.doing === false && this.isComplete === true) {
                    this.isComplete = false
                    for (let i = 0; i < this.paths.length; i++) {
                        this.paths[i].isComplete = false
                        if (this.displayPaths.length === this.paths.length) {
                            this.displayPaths[i].isComplete = false
                        }
                    }
                }
                if (this.repoName === 'pipeline' && moment().subtract(60, 'days').isBefore(date)) {
                    this.date = moment().subtract(60, 'days').toDate()
                    return
                }
                if (this.repoName === 'custom' && moment().subtract(30, 'days').isBefore(date)) {
                    this.date = moment().subtract(30, 'days').toDate()
                }
            }
        }
    }
</script>

<style scoped>

</style>
