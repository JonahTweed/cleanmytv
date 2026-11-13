import { describe, expect, it } from 'vitest'
import { clean } from '../src/main/home'

describe('automatic theme schedule validation', () => {
  it('accepts installed-theme IDs and valid local times', () => {
    expect(clean({ auto_theme: true, day_theme: 'golden-glass-sunrise', night_theme: 'midnight-glass-peaks', day_time: '07:00', night_time: '19:30' })).toEqual({
      auto_theme: true, day_theme: 'golden-glass-sunrise', night_theme: 'midnight-glass-peaks', day_time: '07:00', night_time: '19:30'
    })
  })
  it('rejects invalid IDs and times', () => {
    expect(() => clean({ day_theme: '../../file' })).toThrow()
    expect(() => clean({ night_time: '25:70' })).toThrow()
    expect(() => clean({ auto_theme: 'true' })).toThrow()
    expect(clean({ night_time: '25:70', auto_theme: 'true' }, true)).toEqual({})
  })
})
