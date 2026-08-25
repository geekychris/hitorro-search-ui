import { describe, it, expect } from 'vitest'
import { pickMls, collectNer, entityColor, fmtDate } from './AutoRenderer'

describe('pickMls', () => {
  it('returns null when mls is missing / malformed', () => {
    expect(pickMls(null, 'en')).toBeNull()
    expect(pickMls({}, 'en')).toBeNull()
    expect(pickMls({ mls: 'nope' }, 'en')).toBeNull()
  })

  it('picks the matching language when present', () => {
    const doc = { mls: [{ lang: 'de', text: 'Hallo' }, { lang: 'en', text: 'Hi' }] }
    expect(pickMls(doc, 'en')).toBe('Hi')
    expect(pickMls(doc, 'de')).toBe('Hallo')
  })

  it('falls back to first mls entry when lang not found', () => {
    const doc = { mls: [{ lang: 'de', text: 'Hallo' }] }
    expect(pickMls(doc, 'en')).toBe('Hallo')   // fallback
  })

  it('pulls the requested sub-field', () => {
    const doc = { mls: [{ lang: 'en', text: 'raw', clean: 'cleaned' }] }
    expect(pickMls(doc, 'en')).toBe('raw')
    expect(pickMls(doc, 'en', 'clean')).toBe('cleaned')
  })
})

describe('collectNer', () => {
  it('parses [{term&&NE_Type}] brackets and dedups', () => {
    const mlses = [{
      lang: 'en',
      segmented_ner: [
        '[{Alice&&NE_Person}] met [{Bob&&NE_Person}] in [{Paris&&NE_Location}] .',
        '[{Alice&&NE_Person}] again',
      ],
    }]
    const ner = collectNer(mlses, 'en')
    expect(ner).toEqual([
      { term: 'Alice', type: 'Person' },
      { term: 'Bob',   type: 'Person' },
      { term: 'Paris', type: 'Location' },
    ])
  })

  it('returns empty when no matching lang', () => {
    const mlses = [{ lang: 'de', segmented_ner: ['[{Alice&&NE_Person}]'] }]
    expect(collectNer(mlses, 'en')).toEqual([])
  })

  it('handles missing / non-array segmented_ner', () => {
    expect(collectNer([{ lang: 'en' }] as any, 'en')).toEqual([])
    expect(collectNer([{ lang: 'en', segmented_ner: 'not-an-array' }] as any, 'en')).toEqual([])
  })
})

describe('entityColor', () => {
  it('assigns consistent classes to known types', () => {
    expect(entityColor('Person')).toContain('emerald')
    expect(entityColor('Location')).toContain('sky')
    expect(entityColor('Organization')).toContain('violet')
    expect(entityColor('Date')).toContain('amber')
  })

  it('falls back to slate for unknown types', () => {
    expect(entityColor('Whatever')).toContain('slate')
  })
})

describe('fmtDate', () => {
  it('handles null / undefined gracefully', () => {
    expect(fmtDate(null)).toBe('')
    expect(fmtDate(undefined)).toBe('')
  })

  it('formats epoch-ms numbers', () => {
    // 2026-08-24T12:00:00Z ≈ 1787328000000. Just check we didn't return
    // the raw number or a NaN string — locale details vary across machines.
    const out = fmtDate(1787328000000)
    expect(out).not.toBe('1787328000000')
    expect(out).toMatch(/\d{4}/)   // has a year
  })

  it('formats ISO strings', () => {
    const out = fmtDate('2026-08-24T12:00:00Z')
    expect(out).toMatch(/2026/)
  })

  it('returns the raw string for unparseable input', () => {
    expect(fmtDate('nope')).toBe('nope')
  })
})
