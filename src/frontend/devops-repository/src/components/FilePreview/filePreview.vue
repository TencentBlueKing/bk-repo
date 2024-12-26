<template>
    <div>
        <vue-office-excel
            v-if="previewExcel"
            :src="dataSource"
            :options="excelOptions"
            style="max-height: 100vh; overflow-y: auto"
        />
        <iframe v-if="showFrame" :src="pageUrl" frameborder="0" style="width: 100%; height: 100%"></iframe>
        <div v-if="previewBasic" class="flex-column flex-center">
            <div class="preview-file-tips">{{ $t('previewFileTips') }}</div>
            <textarea v-model="basicFileText" class="textarea" readonly></textarea>
        </div>
        <div v-if="hasError" class="empty-data-container flex-center" style="background-color: white; height: 100%">
            <div class="flex-column flex-center">
                <img width="480" height="240" style="float: left;margin-right: 3px" src="/ui/440.svg" />
                <span class="mt5 error-data-title">{{ $t('previewErrorTip') }}</span>
            </div>
        </div>
        <div v-if="loading" class="mainBody">
            <div class="iconBody">
                <Icon name="loading" size="80" class="svg-loading" />
                <span class="mainMessage">{{ $t('fileLoadingTip') }}</span>
            </div>
        </div>
    </div>
</template>
<script>
    import VueOfficeExcel from '@vue-office/excel'
    import {
        customizePreviewLocalOfficeFile,
        customizePreviewOfficeFile,
        customizePreviewRemoteOfficeFile,
        getPreviewLocalOfficeFileInfo, getPreviewRemoteOfficeFileInfo
    } from '@/utils/previewOfficeFile'
    import { mapActions } from 'vuex'
    import { Base64 } from 'js-base64'

    export default {
        name: 'FilePreview',
        components: { VueOfficeExcel },
        props: {
            repoType: String,
            extraParam: String,
            repoName: String,
            filePath: String
        },
        data () {
            return {
                dataSource: '',
                previewDialog: {
                    title: '',
                    show: false,
                    isLoading: false
                },
                dialogWidth: window.innerWidth - 1000,
                excelOptions: {
                    xls: false, // 预览xlsx文件设为false；预览xls文件设为true
                    minColLength: 0, // excel最少渲染多少列，如果想实现xlsx文件内容有几列，就渲染几列，可以将此值设置为0.
                    minRowLength: 0, // excel最少渲染多少行，如果想实现根据xlsx实际函数渲染，可以将此值设置为0.
                    widthOffset: 10, // 如果渲染出来的结果感觉单元格宽度不够，可以在默认渲染的列表宽度上再加 Npx宽
                    heightOffset: 10, // 在默认渲染的列表高度上再加 Npx高
                    beforeTransformData: (workbookData) => {
                        return workbookData
                    }, // 底层通过exceljs获取excel文件内容，通过该钩子函数，可以对获取的excel文件内容进行修改，比如某个单元格的数据显示不正确，可以在此自行修改每个单元格的value值。
                    transformData: (workbookData) => {
                        return workbookData
                    } // 将获取到的excel数据进行处理之后且渲染到页面之前，可通过transformData对即将渲染的数据及样式进行修改，此时每个单元格的text值就是即将渲染到页面上的内容
                },
                previewExcel: false,
                previewBasic: false,
                basicFileText: '',
                hasError: false,
                pageUrl: '',
                showFrame: false,
                loading: false
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        async created () {
            this.loading = true
            if (this.filePath.endsWith('txt')
                || this.filePath.endsWith('sh')
                || this.filePath.endsWith('bat')
                || this.filePath.endsWith('json')
                || this.filePath.endsWith('yaml')
                || this.filePath.endsWith('yml')
                || this.filePath.endsWith('xml')
                || this.filePath.endsWith('log')
                || this.filePath.endsWith('ini')
                || this.filePath.endsWith('log')
                || this.filePath.endsWith('properties')
                || this.filePath.endsWith('toml')) {
                this.previewBasicFile({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    path: '/' + this.filePath
                }).then(res => {
                    this.loading = false
                    this.previewBasic = true
                    this.basicFileText = res
                }).catch((e) => {
                    this.loading = false
                    this.hasError = true
                })
            } else if (this.filePath.endsWith('.xlsx')) {
                customizePreviewOfficeFile(this.projectId, this.repoName, '/' + this.filePath).then(res => {
                    this.loading = false
                    this.previewExcel = true
                    this.dataSource = res.data
                }).catch((e) => {
                    this.loading = false
                    this.hasError = true
                })
            } else if (this.filePath.endsWith('docx')
                || this.filePath.endsWith('pdf')
                || this.filePath.endsWith('wps')
                || this.filePath.endsWith('doc')
                || this.filePath.endsWith('docm')
                || this.filePath.endsWith('xls')
                || this.filePath.endsWith('xlsm')
                || this.filePath.endsWith('ppt')
                || this.filePath.endsWith('pptx')
                || this.filePath.endsWith('vsd')
                || this.filePath.endsWith('rtf')
                || this.filePath.endsWith('odt')
                || this.filePath.endsWith('wmf')
                || this.filePath.endsWith('emf')
                || this.filePath.endsWith('dps')
                || this.filePath.endsWith('et')
                || this.filePath.endsWith('ods')
                || this.filePath.endsWith('ots')
                || this.filePath.endsWith('tsv')
                || this.filePath.endsWith('odp')
                || this.filePath.endsWith('otp')
                || this.filePath.endsWith('sxi')
                || this.filePath.endsWith('ott')
                || this.filePath.endsWith('vsdx')
                || this.filePath.endsWith('fodt')
                || this.filePath.endsWith('fods')
                || this.filePath.endsWith('xltx')
                || this.filePath.endsWith('tga')
                || this.filePath.endsWith('psd')
                || this.filePath.endsWith('dotm')
                || this.filePath.endsWith('ett')
                || this.filePath.endsWith('xlt')
                || this.filePath.endsWith('xltm')
                || this.filePath.endsWith('wpt')
                || this.filePath.endsWith('dot')
                || this.filePath.endsWith('xlam')
                || this.filePath.endsWith('dotx')
                || this.filePath.endsWith('xla')
                || this.filePath.endsWith('pages')
                || this.filePath.endsWith('eps')) {
                if (this.repoType === 'local') {
                    getPreviewLocalOfficeFileInfo(this.projectId, this.repoName, '/' + this.filePath).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                    customizePreviewLocalOfficeFile(this.projectId, this.repoName, '/' + this.filePath).then(async res => {
                        this.loading = false
                        const url = URL.createObjectURL(res.data) + '#toolbar=0&navpanes=0'
                        this.showFrame = true
                        this.pageUrl = url
                    }).catch(() => {
                        this.loading = false
                        this.hasError = true
                    })
                } else {
                    getPreviewRemoteOfficeFileInfo(Base64.encode(Base64.decode(this.extraParam))).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                    customizePreviewRemoteOfficeFile(Base64.encode(Base64.decode(this.extraParam))).then(res => {
                        this.loading = false
                        const url = URL.createObjectURL(res.data)+ '#toolbar=0&navpanes=0'
                        this.showFrame = true
                        this.pageUrl = url
                    }).catch(() => {
                        this.loading = false
                        this.hasError = true
                    })
                }
            } else {
                this.loading = false
                this.hasError = true
            }
        },
        destroyed () {
            this.cancel()
        },
        methods: {
            ...mapActions([
                'previewBasicFile'
            ]),
            cancel () {
                this.dataSource = ''
                this.previewExcel = false
                this.previewBasic = false
                this.showFrame = false
                this.pageUrl = ''
                this.hasError = false
            },
            initWaterMark (param) {
                window.initWaterMark(param)
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@vue-office/docx/lib/index.css';
@import '@vue-office/excel/lib/index.css';
.preview-file-tips {
    margin-bottom: 10px;
    color: #707070;
}
.textarea {
    resize: none;
    width: 100%;
    height: 700px;
    max-height: 700px;
    overflow-y: auto;
    border: 1px solid #ccc;
    padding: 0 5px;
}
.error-data-title {
    margin-top: 18px;
    font-size: 24px;
    line-height: 32px;
}
.mainBody {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    .svg-loading {
        margin-right: 10px;
        animation: rotate-loading 1s linear infinite;
    }
    .iconBody{
        display: flex;
        align-items: center;
        justify-content: center;
    }
    .mainMessage{
        font-size: 16px;
    }
    @keyframes rotate-loading {
        0% {
            transform: rotateZ(0);
        }

        100% {
            transform: rotateZ(360deg);
        }
    }
}
</style>
