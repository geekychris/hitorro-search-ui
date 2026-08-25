import { describe, it, expect, beforeEach } from 'vitest'
import {
  registerTypeRenderer, unregisterTypeRenderer,
  getTypeRenderer, listRegisteredTypes,
} from './registry'

// Registry is module-scoped state — reset by unregistering everything
// this test registered. Vitest's default `isolate: true` per-file
// helps, but be defensive.
const Dummy = () => null
const Other = () => null

beforeEach(() => {
  listRegisteredTypes().forEach(unregisterTypeRenderer)
})

describe('renderer registry', () => {
  it('returns null for unregistered types', () => {
    expect(getTypeRenderer('nonesuch')).toBeNull()
    expect(getTypeRenderer(null)).toBeNull()
    expect(getTypeRenderer(undefined)).toBeNull()
  })

  it('register then look up by name', () => {
    registerTypeRenderer('mail_email', Dummy)
    expect(getTypeRenderer('mail_email')).toBe(Dummy)
  })

  it('re-registering the same type overwrites (last-write-wins)', () => {
    registerTypeRenderer('mail_email', Dummy)
    registerTypeRenderer('mail_email', Other)
    expect(getTypeRenderer('mail_email')).toBe(Other)
  })

  it('unregister removes the entry', () => {
    registerTypeRenderer('mail_email', Dummy)
    unregisterTypeRenderer('mail_email')
    expect(getTypeRenderer('mail_email')).toBeNull()
  })

  it('same component can be registered under multiple names', () => {
    registerTypeRenderer('mail_email', Dummy)
    registerTypeRenderer('mail_message', Dummy)
    expect(getTypeRenderer('mail_email')).toBe(Dummy)
    expect(getTypeRenderer('mail_message')).toBe(Dummy)
    expect(listRegisteredTypes().sort()).toEqual(['mail_email', 'mail_message'])
  })
})
