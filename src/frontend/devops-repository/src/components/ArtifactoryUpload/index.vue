<template>
    <div class="artifactory-upload-container flex-center">
        <template v-if="file.blob">
            <div class="flex-column flex-center">
                <icon name="file" size="48"></icon>
                <span>{{ file.size }}</span>
            </div>
            <div class="ml20 mr20 upload-file-info">
                <bk-form :label-width="80" :model="file" :rules="rules" ref="fileName">
                    <bk-form-item :label="$t('fileName')" :required="true" :property="'name'" error-display-type="normal">
                        <bk-input :placeholder="$t('folderNamePlacehodler')" :disabled="Boolean(progress)" v-model="file.name"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('overwrite')" :required="true" :property="'overwrite'">
                        <bk-radio-group v-model="file.overwrite">
                            <bk-radio :disabled="Boolean(progress)" :value="true">{{ $t('allow') }}</bk-radio>
                            <bk-radio :disabled="Boolean(progress)" class="ml20" :value="false">{{ $t('notAllow') }}</bk-radio>
                        </bk-radio-group>
                    </bk-form-item>
                    <bk-form-item :label="$t('expiress')" :required="true" :property="'expires'" error-display-type="normal">
                        <bk-input :disabled="Boolean(progress)" :placeholder="$t('uploadExpiresPlaceholder')" v-model="file.expires"></bk-input>
                    </bk-form-item>
                </bk-form>
                <bk-progress v-if="progress"
                    :show-text="false"
                    :theme="progressTheme" class="mt20"
                    :percent="progress">
                </bk-progress>
            </div>
            <i v-if="!progress" class="devops-icon icon-close hover-btn" @click="reset"></i>
        </template>
        <template v-else>
            <input ref="artifactoryUploadInput" type="file" @change="selectFile" :multiple="multiple">
            <i class="mr10 bk-icon upload-icon icon-upload-cloud"></i>
            <span>{{ $t('uploadPlaceholder') }}</span>
        </template>
    </div>
</template>
<script>
    import { convertFileSize } from '@/utils'
    export default {
        name: 'artifactoryUpload',
        props: {
            multiple: {
                type: Boolean,
                default: false
            }
        },
        data () {
            return {
                file: {
                    name: '',
                    blob: null,
                    size: 0,
                    type: '',
                    overwrite: false,
                    expires: 0
                },
                progress: 0,
                progressTheme: 'primary',
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('fileName'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^(\w|-|\.){1,50}$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('fileName'),
                            trigger: 'blur'
                        }
                    ],
                    expires: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('expiress'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[0-9]+$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('expiress'),
                            trigger: 'blur'
                        }
                    ]
                },
                timeout: null
            }
        },
        methods: {
            async getFiles () {
                await this.$refs.fileName.validate()
                return {
                    file: this.file,
                    progressHandler: this.progressHandler
                }
            },
            reset () {
                this.progress = 0
                this.progressTheme = 'primary'
                this.file = {
                    name: '',
                    blob: null,
                    size: 0,
                    type: '',
                    overwrite: false,
                    expires: 0
                }
                this.$nextTick(() => {
                    this.$refs.fileName && this.$refs.fileName.clearError()
                    this.$refs.artifactoryUploadInput.value = ''
                })
            },
            selectFile ($event) {
                const files = $event.target.files
                if (!files.length) return
                const file = files[0]
                this.file = {
                    ...this.file,
                    name: file.name,
                    blob: file,
                    size: convertFileSize(file.size),
                    type: file.type
                }
            },
            progressHandler ($event) {
                this.progress = $event.loaded / $event.total
                clearTimeout(this.timeout)
                if (this.progress === 1) {
                    this.progressTheme = 'success'
                } else {
                    this.timeout = setTimeout(() => {
                        this.progressTheme = 'danger'
                        this.$emit('upload-failed', this.file)
                    }, 30000)
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.artifactory-upload-container {
    position: relative;
    min-height: 70px;
    padding: 10px 20px;
    border: 1px dashed;
    border-radius: 10px;
    &:hover {
        border-color: $iconPrimaryColor;
    }
    .upload-file-info {
        flex: 1;
    }
    .icon-upload-cloud {
        font-size: 20px;
    }
    .icon-close {
        position: absolute;
        top: 10px;
        right: 10px;
        font-size: 12px;
    }
    input[type=file] {
        position: absolute;
        width: 100%;
        height: 100%;
        z-index: 10;
        opacity: 0;
        cursor: pointer;
    }
}
</style>
