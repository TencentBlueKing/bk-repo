export function formatMediaDuration (seconds) {
    if (seconds == null || !Number.isFinite(seconds) || seconds < 0) {
        return '—'
    }
    const total = Math.round(seconds)
    const hours = Math.floor(total / 3600)
    const minutes = Math.floor((total % 3600) / 60)
    const secs = total % 60
    const pad = n => String(n).padStart(2, '0')
    if (hours > 0) {
        return `${hours}:${pad(minutes)}:${pad(secs)}`
    }
    return `${minutes}:${pad(secs)}`
}

export function waveformBarsForSeed (seed, count = 64) {
    let hash = 0
    const text = String(seed || '')
    for (let i = 0; i < text.length; i++) {
        hash = (hash * 31 + text.charCodeAt(i)) | 0
    }
    const bars = []
    for (let i = 0; i < count; i++) {
        hash = (hash * 1103515245 + 12345 + i * 17) | 0
        const norm = (Math.abs(hash) % 1000) / 1000
        bars.push(0.15 + norm * 0.85)
    }
    return bars
}
