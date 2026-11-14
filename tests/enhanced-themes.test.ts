import { describe, expect, it, vi, beforeEach } from 'vitest'
import { clean, parse, themePackBegin, themePackPut, saveCurrentThemeToTv } from '../src/main/home'
import { pickLook, themeToFile, parseTheme } from '../src/shared/themes'
import * as T from '../src/main/tv'
vi.mock('../src/main/tv', async importOriginal => ({ ...await importOriginal<typeof T>(), tv: vi.fn(), getStatus: () => ({ state: 'disconnected' }) }))

const answer = (cfg: object) => `Bundle[{config=${JSON.stringify(cfg)}}]`
describe('per-theme storage and portable themes', () => {
  it('keeps distinct overrides and zero opacity', () => {
    expect(clean({ theme_opacity: { sunrise: 0, peaks: 73 } })).toEqual({ theme_opacity: { sunrise: 0, peaks: 73 } })
    expect(parse(answer({ theme_opacity: { sunrise: 0 }, tile_opacity: 0, _revision: 12, pack_merge: true }))._revision).toBe(12)
  })
  it.each([{ '../escape': 50 }, { valid: 101 }, { valid: -1 }, { valid: '50' }, { valid: 1.5 }, []])('rejects malformed override maps: %j', m => {
    expect(() => clean({ theme_opacity: m })).toThrow()
    expect(clean({ theme_opacity: m }, true)).toEqual({})
  })
  it('reset is an empty map; exports carry effective opacity, never device-wide maps or layout', () => {
    expect(clean({ theme_opacity: {} })).toEqual({ theme_opacity: {} })
    const portable = parseTheme(themeToFile({ name: 'Glass', look: { tile_opacity: 18, top_space: 12, theme_opacity: { private: 0 }, rows: [{ name: 'Private', apps: [] }] } }))
    expect(portable.look).toEqual({ tile_opacity: 18, top_space: 12 })
    expect(pickLook({ _revision: 12 })).toEqual({})
  })
})

describe('non-destructive theme transport', () => {
  const shell = vi.fn()
  beforeEach(() => { shell.mockReset(); vi.mocked(T.tv).mockReturnValue({ shell, maxData: 4096 } as unknown as ReturnType<typeof T.tv>) })
  it('blocks upload on older launchers before calling the destructive old endpoint', async () => {
    shell.mockResolvedValue(answer({ themes_on_tv: 6 }))
    await expect(themePackBegin()).rejects.toThrow(/Update/)
    expect(shell).toHaveBeenCalledTimes(1)
    expect(shell.mock.calls[0][0]).toContain('--method get')
  })
  it('starts a merge only after positive capability detection', async () => {
    shell.mockResolvedValue(answer({ pack_merge: true, themes_on_tv: 6 }))
    await themePackBegin()
    expect(shell.mock.calls[1][0]).toContain('--method pack_begin')
  })
  it('saves current look on the TV without wallpaper download or a replacement upload', async () => {
    shell.mockResolvedValue(answer({ pack_merge: true, themes_on_tv: 7 }))
    await saveCurrentThemeToTv('glass', 'Golden Glass')
    expect(shell.mock.calls[1][0]).toContain('--method pack_save_current')
    expect(shell.mock.calls.some(c => /pack_clear|wallpaper_read|pack_begin/.test(c[0]))).toBe(false)
  })
  it('stops immediately when the TV rejects metadata', async () => {
    shell.mockResolvedValue('Error: storage full')
    await expect(themePackPut({ id: 'glass', name: 'Glass', look: {}, picture: 0, jpeg: '/9j/AA==' })).rejects.toThrow()
    expect(shell).toHaveBeenCalledTimes(1)
  })
})

