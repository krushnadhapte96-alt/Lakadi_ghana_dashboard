/**
 * A two-note chime for a new order, produced by WebAudio rather than a bundled asset so it works
 * offline and adds nothing to the bundle. Cheap to call; each call uses its own context so the
 * first tone is never cut off by the second.
 */
export function playNewOrderSound(): void {
  try {
    const AudioContextCtor =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!AudioContextCtor) return
    const ctx = new AudioContextCtor()

    const notes: Array<[frequency: number, start: number, duration: number]> = [
      [880, 0, 0.12],
      [1320, 0.14, 0.18],
    ]
    for (const [frequency, start, duration] of notes) {
      const oscillator = ctx.createOscillator()
      const gain = ctx.createGain()
      oscillator.type = 'sine'
      oscillator.frequency.value = frequency
      const at = ctx.currentTime + start
      gain.gain.setValueAtTime(0.0001, at)
      gain.gain.exponentialRampToValueAtTime(0.25, at + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, at + duration)
      oscillator.connect(gain)
      gain.connect(ctx.destination)
      oscillator.start(at)
      oscillator.stop(at + duration + 0.05)
    }
    // Do not hold the context open forever after the chime has played.
    window.setTimeout(() => void ctx.close(), 1200)
  } catch {
    // Audio is a nicety, never a reason to break the bell.
  }
}

const SOUND_KEY = 'ghanadash.notifications.sound'

export function soundEnabled(): boolean {
  try {
    return localStorage.getItem(SOUND_KEY) !== '0'
  } catch {
    return true
  }
}

export function setSoundEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(SOUND_KEY, enabled ? '1' : '0')
  } catch {
    // Private mode may refuse storage; the toggle still works for this session.
  }
}

