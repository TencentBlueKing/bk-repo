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
                        <bk-input v-model="item.path" style="margin-bottom: 10px; width: 260px" disabled="true" />
                        <Icon name="right" v-if="item.isComplete" size="14" />
                    </div>
                </div>
                <div v-else>
                    <div v-for="(item,index) in paths " :key="index">
                        <bk-input v-model="item.path" style="margin-bottom: 10px; width: 260px" disabled="true" />
                        <Icon name="right" v-if="item.isComplete" size="14" />
                    </div>
                </div>
            </bk-form-item>
            <bk-form-item :label="$t('deadline')" :required="true" property="date" error-display-type="normal">
                <bk-date-picker v-model="date" :clearable="false" :type="'datetime'"></bk-date-picker>
                <div class="form-tip">{{$t('cleanFolderTip')}}</div>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click="cancel" v-if="!isComplete">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="loading" theme="primary" @click="submit" v-if="!isComplete" :disabled="doing">{{$t('confirm')}}</bk-button>
            <bk-button class="ml10" theme="primary" @click="completeClean" v-if="isComplete">{{$t('complete')}}</bk-button>
        </template>
    </canway-dialog>
</template>

<script>
    import { mapActions } from 'vuex'
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
                'cleanNode'
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
                    }).then(() => {
                        this.paths[i].isComplete = true
                        if (this.displayPaths.length === this.paths.length) {
                            this.displayPaths[i].isComplete = true
                        }
                        completeNum++
                    })
                }
                if (completeNum === this.paths.length) {
                    this.isComplete = true
                    this.$refs.genericForm.clearError()
                }
            },
            completeClean () {
                this.show = false
                this.$emit('refresh')
                this.doing = false
            }
        }
    }
</script>

<style scoped>

</style>
