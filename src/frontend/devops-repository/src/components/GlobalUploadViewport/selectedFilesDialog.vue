<template>
    <canway-dialog
        v-model="show"
        width="600"
        height-num="450"
        @cancel="cancel">
        <template #header>
            <div class="flex-align-center">
                <span class="mr10 canway-dialog-title">{{$t('uploadFile')}}</span>
                <span class="repo-tag" v-bk-tooltips=" { content: rootData.fullPath || '/', placements: ['bottom'] ,disabled: !rootData.fullPath }">{{ rootData.fullPath || '/' }}</span>
            </div>
        </template>
        <div class="flex-between-center">
            <bk-button @click="selectFiles">{{$t('continueUploading')}}</bk-button>
            <div class="flex-align-center">
                <label style="white-space:nowrap;">{{$t('fileSameNameOverwrites')}}：</label>
                <bk-radio-group v-model="overwrite">
                    <bk-radio class="ml20" :value="true">{{ $t('allow') }}</bk-radio>
                    <bk-radio class="ml20" :value="false">{{ $t('notAllow') }}</bk-radio>
                </bk-radio-group>
            </div>
        </div>
        <bk-table
            v-if="show"
            class="mt10"
            :data="selectedFiles"
            height="240px"
            :outer-border="false"
            :row-border="false"
            :virtual-render="selectedFiles.length > 3000"
            size="small">
            <bk-table-column :label="$t('fileName')" prop="name" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('size')" width="90" show-overflow-tooltip>
                <template #default="{ row }">{{ convertFileSize(row.size) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="90">
                <template #default="{ $index }">
                    <bk-button text theme="primary" @click="selectedFiles.splice($index, 1)">{{$t('remove')}}</bk-button>
                </template>
            </bk-table-column>
        </bk-table>
        <input lang="en-GB" class="upload-input" ref="uploadInput" type="file" :webkitdirectory="rootData.folder" @change="selectedFilesHandler" multiple>
        <template #footer>
            <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import { convertFileSize } from '@repository/utils'
    export default {
        name: 'selectedFilesDialog',
        props: {
            rootData: Object
        },
        data () {
            return {
                show: false,
                overwrite: false,
                selectedFiles: []
            }
        },
        methods: {
            convertFileSize,
            selectFiles () {
                this.$refs.uploadInput.value = ''
                this.$nextTick(() => {
                    this.$refs.uploadInput.click()
                })
            },
            selectedFilesHandler () {
                const files = [...this.$refs.uploadInput.files]
                if (!files.length) return
                files.forEach(file => {
                    !this.selectedFiles.find(f => {
                        return this.rootData.folder
                            ? f.webkitRelativePath === file.webkitRelativePath
                            : f.name === file.name
                    }) && (this.selectedFiles.push(file))
                })
                this.show = true
            },
            confirm () {
                this.$emit('confirm', {
                    overwrite: this.overwrite,
                    selectedFiles: this.selectedFiles
                })
                this.cancel()
            },
            cancel () {
                this.show = false
                this.overwrite = false
                this.selectedFiles = []
            }
        }
    }
</script>
<style lang="scss" scoped>
.upload-input {
    display: none;
}
</style>
