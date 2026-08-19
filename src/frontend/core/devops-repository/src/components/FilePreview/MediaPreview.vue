<template>
    <div class="media-preview" :class="{ 'is-audio': isAudio }">
        <template v-if="isAudio">
            <div class="media-preview-audio-stage">
                <div
                    class="media-preview-waveform"
                    role="slider"
                    :aria-valuemin="0"
                    :aria-valuemax="durationSeconds"
                    :aria-valuenow="currentTime"
                    :aria-label="$t('previewMediaProgress')"
                    tabindex="0"
                    @click="onWaveformClick"
                    @keydown="onSeekKeydown"
                >
                    <span
                        v-for="(h, i) in bars"
                        :key="i"
                        class="media-preview-wave-bar"
                        :class="{ 'is-played': barPlayed(i) }"
                        :style="{ height: waveBarHeight(h) }"
                    />
                </div>
            </div>
            <div class="media-preview-controls">
                <button
                    type="button"
                    class="media-preview-play"
                    :aria-label="playing ? $t('previewMediaPause') : $t('previewMediaPlay')"
                    @click="togglePlay"
                >
                    <svg v-if="!playing" width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <polygon points="8,5 19,12 8,19" />
                    </svg>
                    <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <rect x="6" y="5" width="4" height="14" rx="1" />
                        <rect x="14" y="5" width="4" height="14" rx="1" />
                    </svg>
                </button>
                <span class="media-preview-time">{{ currentTimeLabel }}</span>
                <div
                    class="media-preview-scrub"
                    role="slider"
                    :aria-valuemin="0"
                    :aria-valuemax="durationSeconds"
                    :aria-valuenow="currentTime"
                    :aria-label="$t('previewMediaScrub')"
                    tabindex="0"
                    @click="onScrubClick"
                    @keydown="onSeekKeydown"
                >
                    <div class="media-preview-scrub-track">
                        <div class="media-preview-scrub-fill" :style="{ width: progressPercent + '%' }" />
                    </div>
                </div>
                <span class="media-preview-time is-total">{{ durationLabel }}</span>
            </div>
            <audio
                ref="audioEl"
                class="media-preview-audio-el"
                :src="src"
                preload="metadata"
                @loadedmetadata="onAudioMetadata"
                @timeupdate="onTimeUpdate"
                @ended="onEnded"
                @play="playing = true"
                @pause="playing = false"
                @error="onMediaError"
            />
        </template>
        <div v-else class="media-preview-video-wrap">
            <video
                ref="videoEl"
                class="media-preview-video"
                :src="src"
                controls
                playsinline
                preload="metadata"
                @error="onMediaError"
            />
        </div>
    </div>
</template>
<script>
    import { formatMediaDuration, waveformBarsForSeed } from '@repository/utils/mediaPreview'

    export default {
        name: 'MediaPreview',
        props: {
            src: {
                type: String,
                required: true
            },
            kind: {
                type: String,
                default: 'video'
            },
            seed: {
                type: String,
                default: ''
            }
        },
        data () {
            return {
                playing: false,
                currentTime: 0,
                durationSeconds: 0
            }
        },
        computed: {
            isAudio () {
                return this.kind === 'audio'
            },
            bars () {
                return waveformBarsForSeed(this.seed || this.src, 64)
            },
            progressPercent () {
                if (this.durationSeconds <= 0) {
                    return 0
                }
                return Math.min(100, (this.currentTime / this.durationSeconds) * 100)
            },
            currentTimeLabel () {
                return formatMediaDuration(this.currentTime)
            },
            durationLabel () {
                return formatMediaDuration(this.durationSeconds)
            }
        },
        watch: {
            src () {
                this.resetPlayback()
            }
        },
        beforeDestroy () {
            this.pauseMedia()
        },
        methods: {
            waveBarHeight (value) {
                return `${Math.round(value * 100)}%`
            },
            barPlayed (index) {
                if (this.durationSeconds <= 0) {
                    return false
                }
                const threshold = (index + 1) / this.bars.length
                return this.currentTime / this.durationSeconds >= threshold
            },
            togglePlay () {
                const el = this.$refs.audioEl
                if (!el) {
                    return
                }
                if (el.paused) {
                    const playResult = el.play()
                    if (playResult && typeof playResult.catch === 'function') {
                        playResult.catch(() => {})
                    }
                } else {
                    el.pause()
                }
            },
            seekTo (ratio) {
                const el = this.$refs.audioEl
                if (!el || this.durationSeconds <= 0) {
                    return
                }
                const clamped = Math.max(0, Math.min(1, ratio))
                el.currentTime = clamped * this.durationSeconds
                this.currentTime = el.currentTime
            },
            seekFromPointer (clientX, element) {
                if (!element) {
                    return
                }
                const rect = element.getBoundingClientRect()
                if (rect.width <= 0) {
                    return
                }
                this.seekTo((clientX - rect.left) / rect.width)
            },
            onWaveformClick (event) {
                this.seekFromPointer(event.clientX, event.currentTarget)
            },
            onScrubClick (event) {
                this.seekFromPointer(event.clientX, event.currentTarget)
            },
            onSeekKeydown (event) {
                const step = this.durationSeconds > 0 ? this.durationSeconds * 0.05 : 1
                if (event.key === 'ArrowRight') {
                    event.preventDefault()
                    this.seekTo((this.currentTime + step) / Math.max(this.durationSeconds, 1))
                } else if (event.key === 'ArrowLeft') {
                    event.preventDefault()
                    this.seekTo((this.currentTime - step) / Math.max(this.durationSeconds, 1))
                } else if (event.key === ' ' || event.key === 'Enter') {
                    event.preventDefault()
                    this.togglePlay()
                }
            },
            onAudioMetadata () {
                const el = this.$refs.audioEl
                if (!el || !Number.isFinite(el.duration) || el.duration <= 0) {
                    return
                }
                this.durationSeconds = Math.round(el.duration)
            },
            onTimeUpdate () {
                const el = this.$refs.audioEl
                if (!el) {
                    return
                }
                this.currentTime = el.currentTime
            },
            onEnded () {
                this.playing = false
                this.currentTime = 0
                const el = this.$refs.audioEl
                if (el) {
                    el.currentTime = 0
                }
            },
            pauseMedia () {
                if (this.$refs.audioEl) {
                    this.$refs.audioEl.pause()
                }
                if (this.$refs.videoEl) {
                    this.$refs.videoEl.pause()
                }
                this.playing = false
            },
            resetPlayback () {
                this.pauseMedia()
                this.currentTime = 0
                this.durationSeconds = 0
            },
            onMediaError () {
                this.$emit('error')
            }
        }
    }
</script>
<style lang="scss" scoped>
.media-preview {
    --mp-bg: #f0f2f5;
    --mp-accent: #5c8ef2;
    --mp-accent-mid: #7ba4f5;
    --mp-fill: rgb(120 120 128 / 12%);
    --mp-text-secondary: #4b5563;
    --mp-text-tertiary: #9ca3af;
    --mp-inverse: #fff;
    --mp-controls-bg: #fff;
    --mp-divider: rgb(0 0 0 / 10%);
    --mp-play-bg: rgb(0 0 0 / 45%);
    --mp-video-bg: #1e293b;
    box-sizing: border-box;
    position: fixed;
    inset: 0;
    width: 100%;
    height: 100%;
    min-width: 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--mp-bg);
    z-index: 10000000;
}
.media-preview:not(.is-audio) {
    background: var(--mp-video-bg);
}
.media-preview-audio-stage {
    flex: 1;
    width: 100%;
    min-width: 0;
    min-height: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 32px 24px 16px;
    box-sizing: border-box;
}
.media-preview-waveform {
    box-sizing: border-box;
    width: min(100%, 720px);
    max-width: 100%;
    min-width: 0;
    height: min(42vh, 280px);
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 3px;
    padding: 0 8px;
    cursor: pointer;
    outline: none;
}
.media-preview-waveform:focus-visible,
.media-preview-scrub:focus-visible {
    outline: 2px solid var(--mp-accent);
    outline-offset: 4px;
    border-radius: 8px;
}
.media-preview-wave-bar {
    flex: 1 1 0;
    min-width: 0;
    max-width: 8px;
    min-height: 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--mp-accent) 28%, var(--mp-fill));
    transform-origin: bottom center;
    transition: background .12s ease;
}
.media-preview-wave-bar.is-played {
    background: linear-gradient(180deg, var(--mp-accent-mid) 0%, var(--mp-accent) 100%);
}
.media-preview-controls {
    box-sizing: border-box;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    max-width: 100%;
    min-width: 0;
    overflow: hidden;
    padding: 16px 20px 20px;
    border-top: 1px solid var(--mp-divider);
    background: var(--mp-controls-bg);
}
.media-preview-play {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    padding: 0;
    border: 0;
    border-radius: 50%;
    background: var(--mp-play-bg);
    color: var(--mp-inverse);
    cursor: pointer;
    flex-shrink: 0;
    transition: background .2s ease, box-shadow .2s ease;
}
@media (hover: hover) and (pointer: fine) {
    .media-preview-play:hover {
        background: var(--mp-accent);
        box-shadow: 0 2px 12px color-mix(in srgb, var(--mp-accent) 40%, transparent);
    }
}
.media-preview-play:active {
    transform: scale(.96);
}
.media-preview-play:focus-visible {
    outline: 2px solid var(--mp-accent);
    outline-offset: 2px;
}
.media-preview-time {
    flex-shrink: 0;
    min-width: 36px;
    font-size: 12px;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    color: var(--mp-text-secondary);
}
.media-preview-time.is-total {
    color: var(--mp-text-tertiary);
}
.media-preview-scrub {
    flex: 1;
    min-width: 0;
    padding: 8px 0;
    cursor: pointer;
    outline: none;
}
.media-preview-scrub:focus-visible {
    outline-offset: 2px;
    border-radius: 6px;
}
.media-preview-scrub-track {
    height: 4px;
    border-radius: 999px;
    background: var(--mp-fill);
    overflow: hidden;
}
.media-preview-scrub-fill {
    height: 100%;
    border-radius: inherit;
    background: var(--mp-accent);
}
.media-preview-audio-el {
    display: none;
}
.media-preview-video-wrap {
    flex: 1;
    min-width: 0;
    min-height: 0;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    background: var(--mp-video-bg);
}
.media-preview-video {
    display: block;
    flex: 0 1 auto;
    min-width: 0;
    min-height: 0;
    max-width: 100%;
    max-height: 100%;
    width: auto;
    height: auto;
    border: 0;
    border-radius: 0;
    background: var(--mp-video-bg);
    object-fit: contain;
    object-position: center;
}
</style>
