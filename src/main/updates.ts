// Is there a newer CleanMyTV? One request to GitHub's latest-release API, at most every 12 hours,
// with the answer cached in the settings file. Nothing is downloaded: the app links to the release page.
import type { UpdateInfo } from '@shared/api'

const URL_LATEST = 'https://api.github.com/repos/JonahTweed/cleanmytv/releases/latest'
const RELEASES = 'https://github.com/JonahTweed/cleanmytv/releases/'
const EVERY_MS = 12 * 60 * 60 * 1000

export interface UpdateCache {
  checkedAt?: number
  latest?: string
  channel?: string
}

/** Parses "v1.2.3" or "1.2.3"; null for anything else. */
export function parseVersion(v: string): number[] | null {
  const m = /^v?(\d+)\.(\d+)\.(\d+)(?:-(?:beta|rc)\.\d+)?$/.exec(v.trim())
  return m ? m.slice(1, 4).map(Number) : null
}

export function isNewer(candidate: string, current: string): boolean {
  const a = parseVersion(candidate)
  const b = parseVersion(current)
  if (!a || !b) return false
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] > b[i]
  const pre = (v: string): [number, number] => {
    const m = /-(beta|rc)\.(\d+)$/.exec(v)
    return m ? [m[1] === 'beta' ? 0 : 1, Number(m[2])] : [2, 0]
  }
  const x = pre(candidate), y = pre(current)
  return x[0] !== y[0] ? x[0] > y[0] : x[1] > y[1]
}

/**
 * The newest release, from the cache when it is fresh. Returns the cache to store. A failed check keeps
 * the old answer and is retried next launch: being offline is not an error worth showing.
 */
export async function check(current: string, cache: UpdateCache, now = Date.now(), fetchImpl: typeof fetch = fetch): Promise<{ info: UpdateInfo; cache: UpdateCache }> {
  const channel = current.includes('-') ? 'beta' : 'stable'
  let next = cache.channel === channel ? cache : {} as UpdateCache
  if (!next.checkedAt || now - next.checkedAt > EVERY_MS || now < next.checkedAt) {
    try {
      const res = await fetchImpl(channel === 'beta' ? URL_LATEST.replace('/latest', '?per_page=30') : URL_LATEST, { headers: { accept: 'application/vnd.github+json' }, signal: AbortSignal.timeout(10000) })
      if (res.ok) {
        const payload = await res.json()
        const entries = Array.isArray(payload) ? payload : [payload]
        const tags = entries.filter((r: { draft?: boolean; tag_name?: unknown }) => !r.draft && typeof r.tag_name === 'string').map((r: { tag_name: string }) => r.tag_name).filter((tag: string) => parseVersion(tag))
        const tag = tags.reduce((best: string, tag: string) => !best || isNewer(tag, best) ? tag : best, '')
        if (tag) next = { checkedAt: now, latest: tag.replace(/^v/, ''), channel }
      }
    } catch {
      // offline, or GitHub is slow: try again next launch
    }
  }
  const latest = next.latest
  const available = !!latest && isNewer(latest, current)
  return {
    info: { current, latest, available, url: available ? `${RELEASES}tag/v${latest}` : undefined },
    cache: next
  }
}
