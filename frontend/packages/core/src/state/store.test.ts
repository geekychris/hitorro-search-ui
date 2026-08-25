import { describe, it, expect, beforeEach } from 'vitest'
import { useSearchStore } from './store'

// Every test starts from the initial store shape — reset explicitly
// so state from an earlier test can't leak.
beforeEach(() => useSearchStore.getState().reset())

describe('useSearchStore', () => {
  it('setIndex resets extras + page + filters', () => {
    useSearchStore.setState({
      index: 'old', extraIndexes: ['b', 'c'], page: 3, filters: { x: ['y'] },
    })
    useSearchStore.getState().setIndex('new')
    const s = useSearchStore.getState()
    expect(s.index).toBe('new')
    expect(s.extraIndexes).toEqual([])
    expect(s.page).toBe(0)
    expect(s.filters).toEqual({})
  })

  it('toggleFilter adds then removes and clears the field when empty', () => {
    const store = useSearchStore.getState()
    store.toggleFilter('sender_domain', 'redfin.com')
    expect(useSearchStore.getState().filters).toEqual({ sender_domain: ['redfin.com'] })

    store.toggleFilter('sender_domain', 'substack.com')
    expect(useSearchStore.getState().filters).toEqual({ sender_domain: ['redfin.com', 'substack.com'] })

    store.toggleFilter('sender_domain', 'redfin.com')
    expect(useSearchStore.getState().filters).toEqual({ sender_domain: ['substack.com'] })

    store.toggleFilter('sender_domain', 'substack.com')
    expect(useSearchStore.getState().filters).toEqual({})   // empty array → key removed
  })

  it('toggleExtraIndex skips the primary and dedups', () => {
    useSearchStore.getState().setIndex('primary')
    const store = useSearchStore.getState()

    store.toggleExtraIndex('primary')   // no-op — same as primary
    expect(useSearchStore.getState().extraIndexes).toEqual([])

    store.toggleExtraIndex('b')
    store.toggleExtraIndex('c')
    store.toggleExtraIndex('b')   // remove
    expect(useSearchStore.getState().extraIndexes).toEqual(['c'])
  })

  it('setQuery / setMode / setLang reset page to 0', () => {
    useSearchStore.setState({ page: 5 })
    useSearchStore.getState().setQuery('chase')
    expect(useSearchStore.getState().page).toBe(0)

    useSearchStore.setState({ page: 5 })
    useSearchStore.getState().setMode('analyst')
    expect(useSearchStore.getState().page).toBe(0)

    useSearchStore.setState({ page: 5 })
    useSearchStore.getState().setLang('de')
    expect(useSearchStore.getState().page).toBe(0)
  })

  it('clearFilter removes only the named field', () => {
    useSearchStore.setState({ filters: { a: ['1'], b: ['2', '3'] } })
    useSearchStore.getState().clearFilter('a')
    expect(useSearchStore.getState().filters).toEqual({ b: ['2', '3'] })
  })
})
