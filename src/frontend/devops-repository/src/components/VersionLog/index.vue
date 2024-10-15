<template>
    <bk-dialog
        class="version-log-dialog"
        :close-icon="true"
        :show-footer="false"
        v-model="show"
        width="900"
        height-num="603"
        @cancel="cancel">
        <div class="log-version" style="display: flex;">
            <div class="log-version-left">
                <ul class="left-list">
                    <li
                        v-for="(item,index) in versionLogs"
                        :key="index"
                        class="left-list-item"
                        :class="{ 'item-active': index === active }"
                        @click="handleItemClick(item, index)">
                        <span class="item-title">{{ item.version }}</span>
                        <span class="item-date">{{ item.time }}</span>
                        <span v-if="index === 0" class="item-current">{{ $t('currentVersion') }}</span>
                    </li>
                </ul>
            </div>
            <div class="log-version-right">
                <div
                    class="markdowm-container"
                    v-html="markdownToHtml" />
            </div>
        </div>
    </bk-dialog>
</template>
<script>
    import { marked } from 'marked'
    import cookies from 'js-cookie'

    export default {
        name: 'VersionLog',
        props: {
            title: String,
            heightNum: {
                type: [String, Number],
                default: 600
            }
        },
        data () {
            return {
                show: false,
                versionLogs: [],
                markdown: '',
                active: 0
            }
        },
        computed: {
            markdownToHtml () {
                return marked(this.markdown)
            }
        },
        methods: {
            cancel () {
                const BK_CI_DOMAIN = location.host.split('.').slice(1).join('.')
                cookies.set('hasShowLog', this.versionLogs[0].version, { domain: BK_CI_DOMAIN, path: '/', expires: 366 })
                this.show = false
            },
            handleItemClick (item, index) {
                this.active = index
                this.markdown = item.content
            }
        }
    }
</script>
<style lang="scss" scoped>
.version-log-dialog{
    ::v-deep.bk-dialog-wrapper .bk-dialog-content  {
        height: 600px;
    }
}
.log-version {
    display: flex;
    height: 70vh;
    margin: -33px -24px -26px;

    .log-version-left {
        position: absolute;
        top: 0;
        bottom: 0;
        width: 200px;
        padding: 40px 0;
        overflow: hidden;
        font-size: 12px;
        background-color: #fafbfd;
        border-right: 1px solid #dcdee5;

        .left-list {
            display: flex;
            width: 100%;
            height: 520px;
            overflow: auto;
            border-top: 1px solid #dcdee5 !important;
            flex-direction: column;

            .left-list-item {
                position: relative;
                display: flex;
                padding-left: 30px;
                cursor: pointer;
                border-bottom: 1px solid #dcdee5;
                flex: 0 0 54px;
                flex-direction: column;
                justify-content: center;

                .left-list-item:hover {
                    cursor: pointer;
                    background-color: #fff;
                }

                .item-title {
                    font-size: 16px;
                    color: #313238;
                }

                .item-date {
                    color: #979ba5;
                }

                .item-current {
                    position: absolute;
                    top: 8px;
                    right: 2px;
                    display: flex;
                    width: 58px;
                    height: 20px;
                    line-height: 9px;
                    color: #fff;
                    background-color: #699df4;
                    border-radius: 2px;
                    align-items: center;
                    justify-content: center;
                }
            }

            .left-list-item.item-active {
                background-color: #fff;
            }

            .left-list-item.item-active::before {
                position: absolute;
                top: 0;
                bottom: 0;
                left: 0;
                width: 6px;
                background-color: #3a84ff;
                content: ' ';
            }
        }
    }

    .log-version-right {
        flex: 1;
        padding: 28px 30px 50px 45px;
        margin-left: 200px;
    }

    .markdowm-container {
        font-size: 14px;
        color: #313238;

        h1,
        h2,
        h3,
        h4,
        h5 {
            height: auto;
            margin: 10px 0;
            font: normal 14px/1.5
            'Helvetica Neue',
            Helvetica,
            Arial,
            'Lantinghei SC',
            'Hiragino Sans GB',
            'Microsoft Yahei',
            sans-serif;
            font-weight: bold;
            color: #34383e;
        }

        h1 {
            font-size: 30px;
        }

        h2 {
            font-size: 24px;
        }

        h3 {
            font-size: 18px;
        }

        h4 {
            font-size: 16px;
        }

        h5 {
            font-size: 14px;
        }

        em {
            font-style: italic;
        }

        div,
        p,
        font,
        span,
        li {
            line-height: 1.3;
        }

        p {
            margin: 0 0 1em;
        }

        table,
        table p {
            margin: 0;
        }

        ul,
        ol {
            padding: 0;
            margin: 0 0 1em 2em;
            text-indent: 0;
        }

        ul {
            padding: 0;
            margin: 10px 0 10px 15px;
            list-style-type: none;
        }

        ol {
            padding: 0;
            margin: 10px 0 10px 25px;
        }

        ol > li {
            line-height: 1.8;
            white-space: normal;
        }

        ul > li {
            padding-left: 15px !important;
            line-height: 1.8;
            white-space: normal;

            &::before {
                float: left;
                width: 6px;
                height: 6px;
                margin-top: calc(.9em - 5px);
                margin-left: -15px;
                background: #000;
                border-radius: 50%;
                content: '';
            }
        }

        li > ul {
            margin-bottom: 10px;
        }

        li ol {
            padding-left: 20px !important;
        }

        ul ul,
        ul ol,
        ol ol,
        ol ul {
            margin-bottom: 0;
            margin-left: 20px;
        }

        ul.list-type-1 > li {
            padding-left: 0 !important;
            margin-left: 15px !important;
            list-style: circle !important;
            background: none !important;
        }

        ul.list-type-2 > li {
            padding-left: 0 !important;
            margin-left: 15px !important;
            list-style: square !important;
            background: none !important;
        }

        ol.list-type-1 > li {
            list-style: lower-greek !important;
        }

        ol.list-type-2 > li {
            list-style: upper-roman !important;
        }

        ol.list-type-3 > li {
            list-style: cjk-ideographic !important;
        }

        pre,
        code {
            width: 95%;
            padding: 0 3px 2px;
            font-family: Monaco, Menlo, Consolas, 'Courier New', monospace;
            font-size: 14px;
            color: #333;
            border-radius: 3px;
        }

        code {
            padding: 2px 4px;
            font-family: Consolas, monospace, tahoma, Arial;
            color: #d14;
            border: 1px solid #e1e1e8;
        }

        pre {
            display: block;
            padding: 9.5px;
            margin: 0 0 10px;
            font-family: Consolas, monospace, tahoma, Arial;
            font-size: 13px;
            word-break: break-all;
            word-wrap: break-word;
            white-space: pre-wrap;
            background-color: #f6f6f6;
            border: 1px solid #ddd;
            border: 1px solid rgb(0 0 0 / 15%);
            border-radius: 2px;
        }

        pre code {
            padding: 0;
            white-space: pre-wrap;
            border: 0;
        }

        blockquote {
            padding: 0 0 0 14px;
            margin: 0 0 20px;
            border-left: 5px solid #dfdfdf;
        }

        blockquote p {
            margin-bottom: 0;
            font-size: 14px;
            font-weight: 300;
            line-height: 25px;
        }

        blockquote small {
            display: block;
            line-height: 20px;
            color: #999;
        }

        blockquote small::before {
            content: '\2014 \00A0';
        }

        blockquote::before,
        blockquote::after {
            content: '';
        }
    }
}
</style>
