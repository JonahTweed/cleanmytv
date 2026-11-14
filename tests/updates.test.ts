import { describe, expect, it, vi } from 'vitest'
import { check, isNewer } from '../src/main/updates'

const reply = (tag: string) => (async () => new Response(JSON.stringify({ tag_name: tag }), { status: 200 })) as unknown as typeof fetch

describe('update check', () => {
  it('compares versions number by number', () => {
    expect(isNewer('0.10.0', '0.9.9')).toBe(true)
    expect(isNewer('v0.2.1', '0.2.1')).toBe(false)
    expect(isNewer('0.2.0', '0.2.1')).toBe(false)
    expect(isNewer('nightly', '0.2.1')).toBe(false)
  })

  it('asks GitHub, then answers from the cache for 12 hours', async () => {
    const first = await check('0.2.1', {}, 1000, reply('v0.3.0'))
    expect(first.info).toEqual({ current: '0.2.1', latest: '0.3.0', available: true, url: 'https://github.com/JonahTweed/cleanmytv/releases/tag/v0.3.0' })
    const cached = await check('0.2.1', first.cache, 2000, (async () => { throw new Error('should not fetch') }) as unknown as typeof fetch)
    expect(cached.info.latest).toBe('0.3.0')
  })

  it('keeps quiet when offline or when GitHub answers nonsense', async () => {
    const off = await check('0.2.1', {}, 1000, (async () => { throw new Error('offline') }) as unknown as typeof fetch)
    expect(off.info.available).toBe(false)
    const odd = await check('0.2.1', {}, 1000, reply('<script>'))
    expect(odd.info).toMatchObject({ available: false, latest: undefined })
  })
})

describe('Enhanced release channel', () => {
  it('orders beta numbers, release candidates and stable releases', () => {
    expect(isNewer('0.8.0-beta.10', '0.8.0-beta.2')).toBe(true)
    expect(isNewer('0.8.0-rc.1', '0.8.0-beta.10')).toBe(true)
    expect(isNewer('0.8.0', '0.8.0-rc.1')).toBe(true)
    expect(isNewer('0.8.0-beta.1', '0.8.0')).toBe(false)
  })
  it('uses the fork and ignores drafts in the beta channel', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify([
      { tag_name: 'v0.8.0-beta.3', draft: false }, { tag_name: 'v9.0.0', draft: true }, { tag_name: 'v0.8.0-beta.2' }
    ])))
    const result = await check('0.8.0-beta.1', {}, 123, fetcher as unknown as typeof fetch)
    expect(fetcher.mock.calls[0]).toBeDefined()
    expect(result.info.latest).toBe('0.8.0-beta.3')
    expect(result.info.url).toContain('github.com/JonahTweed/cleanmytv/')
  })
})
