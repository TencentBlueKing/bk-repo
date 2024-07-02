<template>
    <div>
        <notice-component :api-url="apiUrl" @show-alert-change="showAlertChange" />
    </div>
</template>
<script>
    import NoticeComponent from '@blueking/notice-component-vue2'

    export default {
        name: 'bkrepoNotice',
        components: {
            NoticeComponent
        },
        data () {
            return {
                apiUrl: '/web/repository/api/notice/page/1/1',
                showAlert: true
            }
        },
        methods: {
            showAlertChange (isShow) {
                this.showAlert = isShow
            }
        }
    }
</script>
<style lang="scss">
.bk-notice-popover-con {
    max-height: calc(100vh - 120px);
    word-break: break-all;
    overflow-y: auto
}

.bk-notice-popover-con * {
    display: inline-block;
    margin-block-start: 0;
    margin-block-end: 0;
    margin-inline-start: 0;
    margin-inline-end: 0
}

.bk-notice-popover-con span {
    display: inline
}

.bk-notice-component-alert {
    padding: 0 40px;
    height: 40px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #FAF0D2;
    border-radius: 2px;
    font-size: 12px;
    color: #63656e
}

.bk-notice-component-alert .notice-icon {
    color: #e6aa00;
    margin-right: 8px
}

.bk-notice-component-alert .bk-notice-content-container {
    height: 40px;
    display: flex;
    align-items: center;
    flex: 1;
    white-space: nowrap;
    overflow: hidden
}

.bk-notice-component-alert .bk-notice-content-container * {
    margin-block-start: 0;
    margin-block-end: 0;
    margin-inline-start: 0;
    margin-inline-end: 0
}

.bk-notice-component-alert .bk-notice-content-container .bk-notice-content,.bk-notice-component-alert .bk-notice-content-container .bk-notice-content * {
    display: inline-block
}

.bk-notice-component-alert .operate-icon {
    cursor: pointer
}

.bk-notice-component-alert .alert-pagination {
    width: 100px;
    text-align: right;
    margin-left: 8px
}

.bk-notice-component-alert .alert-pagination span {
    padding: 4px;
    font-size: 12px
}

.bk-notice-component-alert .alert-pagination .alert-num {
    cursor: default;
    color: #979ba5
}

.bk-notice-component-alert .alert-pagination .operate-icon {
    cursor: pointer;
    font-weight: 800;
    color: #3a84ff
}

.bk-notice-component-alert .alert-pagination .operate-icon.disabled {
    cursor: not-allowed;
    color: #a3c5fd
}

.bk-notice-component-dialog.bk-dialog-wrapper .bk-dialog-tool,.bk-notice-component-dialog.bk-dialog-wrapper .bk-dialog-header {
    display: none
}

.bk-notice-component-dialog.bk-dialog-wrapper .bk-dialog-body {
    min-height: 220px;
    max-height: 420px;
    padding: 32px 24px 0;
    overflow: auto;
    overflow-x: hidden
}

.bk-notice-component-dialog.bk-dialog-wrapper .bk-dialog-body .notice-content {
    word-wrap: break-word
}

.bk-notice-component-dialog.bk-dialog-wrapper .bk-dialog-body p {
    margin: 0
}

.bk-notice-component-dialog.bk-dialog-wrapper .bk-dialog-footer {
    background-color: #fff;
    border-top: none;
    padding: 0;
    height: 32px;
    line-height: 32px;
    margin: 24px
}

.bk-notice-component-dialog.bk-dialog-wrapper .bk-dialog-footer .notice-buttons .bk-button {
    width: 88px;
    margin-left: 8px
}
</style>
