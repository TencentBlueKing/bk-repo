<template>
    <div class="package-card-container flex-align-center">
        <div class="mr20 card-icon flex-center">
            <Icon size="48" :name="cardData.type ? cardData.type.toLowerCase() : getIconName(cardData.name)" />
        </div>
        <div class="mr20 package-card-main flex-column">
            <div class="flex-align-center">
                <span class="pr10 card-name text-overflow" :title="cardData.name">{{ cardData.name }}</span>
                <span class="card-tag" v-if="cardData.type === 'MAVEN'">{{ cardData.key.replace(/^.*\/\/(.+):.*$/, '$1') }}</span>
            </div>
            <span class="package-card-description text-overflow" :title="cardData.description">{{ cardData.description }}</span>
            <div class="package-card-data flex-align-center">
                <template v-if="cardData.type">
                    <div class="flex-align-center" :title="`最新版本：${cardData.latest}`"></div>
                    <div class="flex-align-center" :title="`最后修改：${formatDate(cardData.lastModifiedDate)}`"></div>
                    <div class="flex-align-center" :title="`版本数：${cardData.versions}`"></div>
                    <div class="flex-align-center" :title="`下载统计：${cardData.downloads}`"></div>
                </template>
                <template v-else>
                    <div class="flex-align-center" :title="`文件大小：${convertFileSize(cardData.size)}`"></div>
                    <div class="flex-align-center" :title="`最后修改：${formatDate(cardData.lastModifiedDate)}`"></div>
                </template>
            </div>
        </div>
        <div class="card-operation flex-center">
            <i v-if="!readonly" class="devops-icon icon-delete flex-center operation-btn" @click.stop="deleteCard"></i>
            <i v-if="!cardData.type" class="devops-icon icon-download flex-center operation-btn" @click.stop="download"></i>
        </div>
    </div>
</template>
<script>
    import { convertFileSize, formatDate } from '@repository/utils'
    import { getIconName } from '@repository/store/publicEnum'
    export default {
        name: 'packageCard',
        props: {
            cardData: {
                type: Object,
                default: {}
            },
            readonly: {
                type: Boolean,
                default: false
            }
        },
        methods: {
            convertFileSize,
            formatDate,
            getIconName,
            deleteCard () {
                this.$emit('delete-card')
            },
            download () {
                const url = `/generic/${this.cardData.projectId}/${this.cardData.repoName}/${this.cardData.fullPath}?download=true`
                this.$ajax.head(url).then(() => {
                    window.open(
                        '/web' + url,
                        '_self'
                    )
                }).catch(e => {
                    this.$bkMessage({
                        theme: 'error',
                        message: e.status !== 404 ? e.message : this.$t('fileNotExist')
                    })
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.package-card-container {
    height: 100px;
    margin: 3px;
    padding: 16px 20px;
    border-radius: 5px;
    background-color: var(--bgLighterColor);
    cursor: pointer;
    &:hover {
        background-color: var(--bgHoverLighterColor);
        box-shadow: 0px 0px 6px 0px var(--primaryBoxShadowColor);
    }
    .card-icon {
        width: 68px;
        height: 68px;
        background-color: white;
        border: 1px solid var(--borderColor);
        border-radius: 4px;
    }
    .package-card-main {
        flex: 1;
        height: 100%;
        justify-content: space-around;
        overflow: hidden;
        .card-name {
            font-size: 14px;
            max-width: 500px;
            font-weight: bold;
            &:hover {
                color: var(--primaryColor);
            }
        }
        .card-tag {
            display: inline-block;
            padding: 0 10px;
            line-height: 22px;
            white-space: nowrap;
            border-radius: 2px;
            color: white;
            background-color: var(--primaryHoverColor);
        }
        .package-card-description {
            font-size: 12px;
            color: var(--fontSubsidiaryColor);
        }
        .package-card-data > div {
            width: 300px;
            color: var(--fontSubsidiaryColor);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            &:after {
                content: attr(title);
            }
        }
    }
    .card-operation {
        flex-basis: 50px;
        .operation-btn {
            width: 24px;
            height: 24px;
            font-size: 16px;
            &:hover {
                color: white;
                background-color: var(--dangerColor);
                border-radius: 4px;
            }
            &.icon-delete:hover {
                background-color: var(--dangerColor);
            }
            &.icon-download:hover {
                background-color: var(--primaryColor);
            }
        }
    }
}
</style>
