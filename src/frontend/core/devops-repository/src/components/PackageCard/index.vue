<template>
    <div class="package-card-container flex-align-center">
        <Icon class="mr20 card-icon" size="70" :name="cardData.type ? cardData.type.toLowerCase() : getIconName(cardData.name)" />
        <div class="mr20 package-card-main flex-column">
            <div class="flex-align-center">
                <span class="card-name text-overflow" :title="cardData.name">{{ cardData.name }}</span>
                <span class="ml10 repo-tag" v-if="['MAVEN'].includes(cardData.type)">{{ cardData.key.replace(/^.*\/\/(.+):.*$/, '$1') }}</span>
                <scan-tag class="ml10"
                    v-if="showRepoScan"
                    :status="(cardData.metadata || {}).scanStatus"
                    readonly>
                </scan-tag>
                <forbid-tag class="ml10"
                    v-if="!cardData.type && (cardData.metadata || {}).forbidStatus"
                    v-bind="cardData.metadata">
                </forbid-tag>
            </div>
            <span class="package-card-description text-overflow" :title="cardData.description">{{ cardData.description }}</span>
            <div class="package-card-data" v-if="cardData.type">
                <template>
                    <div class="card-metadata" :title="$t('latestVersion') + ':' + `${cardData.latest}`"></div>
                    <div class="card-metadata" :title="$t('lastModified') + ':' + `${formatDate(cardData.lastModifiedDate)}`"></div>
                    <div class="card-metadata" :title="$t('versions') + ':' + `${cardData.versions}`"></div>
                    <div class="card-metadata" :title="$t('downloadStats') + ':' + `${cardData.downloads}`"></div>
                </template>
            </div>
            <div class="package-card-data-more" v-else>
                <template>
                    <div class="card-metadata" :title="$t('repo') + ':' + `${cardData.repoName}`"></div>
                    <div class="card-metadata" :title="$t('filePath') + ':' + `${cardData.fullPath}`"></div>
                    <div class="card-metadata" :title="$t('fileSize') + ':' + `${convertFileSize(cardData.size)}`"></div>
                    <div class="card-metadata" :title="$t('lastModified') + ':' + `${formatDate(cardData.lastModifiedDate)}`"></div>
                    <div class="card-metadata" :title="$t('created') + ':' + `${formatDate(cardData.createdDate)}`"></div>
                </template>
            </div>
        </div>
        <div class="card-operation flex-center">
            <Icon class="hover-btn" v-if="!readonly" size="24" name="icon-delete" @click.native.stop="deleteCard" />
            <operation-list
                v-if="!cardData.type"
                :list="[
                    { label: $t('detail'), clickEvent: () => detail() },
                    !(cardData.metadata || {}).forbidStatus && { label: $t('download'), clickEvent: () => download() },
                    !community && !(cardData.metadata || {}).forbidStatus && { label: $t('share'), clickEvent: () => share() }
                ]"></operation-list>
        </div>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import ScanTag from '@repository/views/repoScan/scanTag'
    import forbidTag from '@repository/components/ForbidTag'
    import { mapGetters } from 'vuex'
    import { convertFileSize, formatDate } from '@repository/utils'
    import { getIconName } from '@repository/store/publicEnum'
    export default {
        name: 'packageCard',
        components: { OperationList, ScanTag, forbidTag },
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
        computed: {
            ...mapGetters(['isEnterprise']),
            community () {
                return RELEASE_MODE === 'community'
            },
            showRepoScan () {
                const show = this.isEnterprise && !this.community && !this.cardData.type && /\.(ipa)|(apk)|(jar)$/.test(this.cardData.name)
                return show || SHOW_ANALYST_MENU
            }
        },
        methods: {
            convertFileSize,
            formatDate,
            getIconName,
            deleteCard () {
                this.$emit('delete-card')
            },
            detail () {
                this.$emit('show-detail', this.cardData)
            },
            download () {
                const url = `/generic/${this.cardData.projectId}/${this.cardData.repoName}/${this.cardData.fullPath}?download=true`
                this.$ajax.head(url).then(() => {
                    window.open(
                        '/web' + url + `&x-bkrepo-project-id=${this.cardData.projectId}`,
                        '_self'
                    )
                }).catch(e => {
                    const message = e.status === 403 ? this.$t('fileDownloadError', [this.$route.params.projectId]) : this.$t('fileError')
                    this.$bkMessage({
                        theme: 'error',
                        message
                    })
                })
            },
            share () {
                this.$emit('share', this.cardData)
            }
        }
    }
</script>
<style lang="scss" scoped>
.package-card-container {
    flex: 1;
    height: 100px;
    padding: 16px 20px;
    border-radius: 5px;
    background-color: var(--bgLighterColor);
    cursor: pointer;
    .card-icon {
        padding: 15px;
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
        .package-card-description {
            font-size: 12px;
            color: var(--fontSubsidiaryColor);
        }
        .package-card-data {
            display: grid;
            grid-template: auto / repeat(4, 1fr);
            .card-metadata {
                padding: 0 5px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:after {
                    content: attr(title);
                    color: var(--fontSubsidiaryColor);
                }
            }
        }
        .package-card-data-more {
            display: grid;
            grid-template: auto / repeat(5, 1fr);
            .card-metadata {
                padding: 0 5px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:after {
                    content: attr(title);
                    color: var(--fontSubsidiaryColor);
                }
            }
        }
    }
    .card-operation {
        visibility: hidden;
        flex-basis: 50px;
    }
    &:hover {
        background-color: var(--bgHoverLighterColor);
        // box-shadow: 0px 0px 6px 0px var(--primaryBoxShadowColor);
        .repo-tag {
            border-color: var(--primaryBoxShadowColor);
        }
        .card-operation {
            visibility: visible;
        }
    }
}
</style>
