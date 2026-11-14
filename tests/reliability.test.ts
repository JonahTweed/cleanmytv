import { describe, expect, it } from 'vitest'
import { clean, parse } from '../src/main/home'
import { pickLook } from '../src/shared/themes'

describe('independent Dock configuration', () => {
  it('round-trips explicit membership independently of folder placements', () => {
    const cfg = parse(`Bundle[{config=${JSON.stringify({ dock_apps: ['org.a.app', 'org.b.app'], folders: [{ name: 'Tools', apps: ['org.a.app'] }] })}}]`)
    expect(cfg.dock_apps).toEqual(['org.a.app', 'org.b.app'])
    expect(cfg.folders[0].apps).toEqual(['org.a.app'])
  })
  it('supports legacy implicit membership and an explicit empty Dock', () => {
    expect(clean({ dock_apps: null })).toEqual({ dock_apps: null })
    expect(clean({ dock_apps: [] })).toEqual({ dock_apps: [] })
    expect(clean({ accent: '#112233' })).not.toHaveProperty('dock_apps')
  })
  it('rejects malformed and oversized Dock membership', () => {
    expect(() => clean({ dock_apps: ['../wrong'] })).toThrow()
    expect(() => clean({ dock_apps: Array.from({ length: 9 }, (_, i) => `org.a${i}.app`) })).toThrow()
    expect(clean({ dock_apps: ['org.a.app', 'org.a.app'] })).toEqual({ dock_apps: ['org.a.app'] })
  })
  it('never exports device layout inside a portable theme', () => {
    expect(pickLook({ dock_apps: ['org.a.app'] })).toEqual({})
  })
})
