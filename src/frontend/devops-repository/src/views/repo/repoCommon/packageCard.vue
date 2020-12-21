<template>
    <div class="package-card-container flex-align-center">
        <div class="package-card-main flex-column">
            <div class="package-card-name flex-align-center">
                <icon class="mr10" size="14" :name="cardIcon" />
                {{ cardData.name }}
                <span class="ml10 repo-tag" v-if="cardData.type === 'MAVEN'">{{ cardData.key.replace(/^.*\/\/(.+):.*$/, '$1') }}</span>
            </div>
            <div class="package-card-data flex-align-center">
                <div class="flex-align-center" :title="cardData.latest"><icon class="mr5" size="16" name="latest-version" />{{ cardData.latest }}</div>
                <div class="flex-align-center"><icon class="mr5" size="16" name="versions" />{{ cardData.versions }}</div>
                <div class="flex-align-center"><icon class="mr5" size="16" name="downloads" />{{ cardData.downloads }}</div>
                <div class="flex-align-center"><icon class="mr5" size="16" name="time" />{{ formatDate(cardData.lastModifiedDate) }}</div>
                <div class="flex-align-center"><icon class="mr5" size="16" name="updater" />{{ userList[cardData.lastModifiedBy] ? userList[cardData.lastModifiedBy].name : cardData.lastModifiedBy }}</div>
            </div>
        </div>
        <i class="devops-icon icon-delete package-card-delete hover-btn" @click.stop="deleteCard"></i>
    </div>
</template>
<script>
    import { formatDate } from '@/utils'
    import { mapState } from 'vuex'
    export default {
        name: 'packageCard',
        props: {
            cardData: {
                type: Object,
                default: {}
            },
            cardIcon: {
                type: String,
                default: 'default-docker'
            }
        },
        computed: {
            ...mapState(['userList'])
        },
        methods: {
            formatDate,
            deleteCard () {
                this.$emit('delete-card')
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.package-card-container {
    height: 70px;
    padding: 5px 20px;
    border: 1px solid $borderWeightColor;
    border-radius: 5px;
    background-color: #fdfdfe;
    cursor: pointer;
    &:hover {
        border-color: $iconPrimaryColor;
    }
    .package-card-main {
        flex: 1;
        height: 100%;
        justify-content: space-around;
        .package-card-name {
            color: #222222;
            font-size: 12px;
            font-weight: bold;
            .repo-tag {
                font-weight: normal;
            }
        }
        .package-card-data {
            color: $fontWeightColor;
            font-size: 14px;
            font-weight: normal;
            div {
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:nth-child(1) {
                    flex-basis: 250px;
                }
                &:nth-child(2) {
                    flex-basis: 120px;
                }
                &:nth-child(3) {
                    flex-basis: 140px;
                }
                &:nth-child(4) {
                    flex-basis: 275px;
                }
                &:nth-child(5) {
                    flex-basis: 175px;
                }
            }
        }
    }
    .package-card-delete {
        flex-basis: 30px;
        font-size: 16px;
    }
}
</style>
