<template>
    <div class="artifactory-upload-container flex-center">
        <template v-if="file.blob">
            <div class="flex-column flex-center">
                <Icon size="48" :name="getIconName(file.name) || 'file'" />
                <span>{{ file.size }}</span>
            </div>
            <div class="ml20 mr20 upload-file-info">
                <bk-form :label-width="80" :model="file" :rules="rules" ref="fileName">
                    <bk-form-item :label="$t('fileName')" :required="true" property="name" error-display-type="normal">
                        <bk-input :disabled="Boolean(uploadProgress)" v-model.trim="file.name" maxlength="255" show-word-limit :placeholder="$t('pleaseInput')"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('overwrite')" property="overwrite">
                        <bk-radio-group v-model="file.overwrite">
                            <bk-radio :disabled="Boolean(uploadProgress)" :value="true">{{ $t('allow') }}</bk-radio>
                            <bk-radio :disabled="Boolean(uploadProgress)" class="ml20" :value="false">{{ $t('notAllow') }}</bk-radio>
                        </bk-radio-group>
                    </bk-form-item>
                    <!-- <bk-form-item :label="$t('express')" :required="true" :property="'expires'">
                        <bk-input :disabled="Boolean(uploadProgress)" :placeholder="$t('uploadExpiresPlaceholder')" v-model="file.expires"></bk-input>
                    </bk-form-item> -->
                </bk-form>
                <bk-progress v-if="uploadProgress" class="mt20" :show-text="false" :theme="uploadStatus" :percent="uploadProgress"></bk-progress>
            </div>
            <i v-if="!uploadProgress" class="devops-icon icon-close hover-btn" @click="reset"></i>
        </template>
        <template v-else>
            <input ref="artifactoryUploadInput" type="file" @change="selectFile" :multiple="multiple">
            <i class="mr10 bk-icon upload-icon icon-upload-cloud"></i>
            <span>{{ $t('uploadPlaceholder') }}</span>
        </template>
    </div>
</template>
<script>
    import { convertFileSize } from '@repository/utils'
    import { getIconName } from '@repository/store/publicEnum'
    export default {
        name: 'artifactoryUpload',
        props: {
            uploadStatus: {
                type: String,
                default: 'primary'
            },
            uploadProgress: {
                type: Number,
                default: 0
            },
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
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('fileName'),
                            trigger: 'blur'
                        }
                    ],
                    expires: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('expire'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[0-9]+$/,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('legit') + this.$t('space') + this.$t('expire'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        methods: {
            getIconName,
            async getFiles () {
                if (!this.file.blob) {
                    this.$bkMessage({
                        message: this.$t('selectFileTip'),
                        theme: 'error'
                    })
                    return
                }
                await this.$refs.fileName.validate()
                return this.file
            },
            reset () {
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
            }
        }
    }
</script>
<style lang="scss" scoped>
.artifactory-upload-container {
    position: relative;
    height: 150px;
    padding: 10px 20px;
    border: 1px dashed;
    border-radius: 10px;
    &:hover {
        border-color: var(--iconPrimaryColor);
    }
    .upload-file-info {
        flex: 1;
    }
    .icon-upload-cloud {
        font-size: 20px;
    }
    .icon-close {
        position: absolute;
        font-size: 12px;
        top: 10px;
        right: 10px;
    }
}
</style>
