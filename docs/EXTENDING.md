# Extending hitorro-search-ui

Adding per-type card templates, customizing the shell, adding backend endpoints, restyling.

## Add a per-type card template

Every hit gets rendered by either a registered per-type component or the generic `AutoRenderer` fallback. To ship a polished experience for a new type, drop in a component and register it.

### The 3-step pattern

**1. Create the component** under `frontend/packages/demo-app/src/overrides/`:

```tsx
// frontend/packages/demo-app/src/overrides/PhotoAssetCard.tsx
import React from 'react'
import {
  type TypeRendererProps,
  pickMls, fmtDate, HighlightedText,
} from '@hitorro/search-ui-core'

export function PhotoAssetCard({ hit, lang, query, onOpen }: TypeRendererProps) {
  const doc = hit.doc ?? {}
  const filename = doc.filename ?? '(no filename)'
  const taken = doc.taken_iso ? fmtDate(doc.taken_iso) : null
  const isVideo = doc.kind_name === 'video'

  return (
    <div
      className="p-4 rounded-lg border border-slate-200 hover:border-hitorro-primary hover:shadow-md cursor-pointer bg-white"
      onClick={onOpen}
    >
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-base font-semibold truncate">
          {isVideo ? '🎬' : '📷'} {filename}
        </h3>
        {taken && <span className="shrink-0 text-[11px] text-slate-400 font-mono">{taken}</span>}
      </div>

      <div className="mt-1 flex flex-wrap gap-2 text-xs text-slate-500">
        {doc.megapixels && <span>{doc.megapixels} MP</span>}
        {doc.width && doc.height && <span>{doc.width}×{doc.height}</span>}
        {doc.orientation && <span>{doc.orientation}</span>}
        {doc.duration && <span>{Math.round(doc.duration)}s</span>}
        {doc.is_favorite && <span className="px-1.5 py-0.5 bg-pink-100 text-pink-700 rounded">♥ favorite</span>}
        {doc.has_location && <span className="px-1.5 py-0.5 bg-sky-100 text-sky-700 rounded">📍 geo</span>}
      </div>

      {hit.snippet && (
        <p className="mt-2 text-sm text-slate-700"><HighlightedText text={hit.snippet} query={query} /></p>
      )}
    </div>
  )
}
```

**2. Register in `main.tsx`:**

```tsx
import { PhotoAssetCard } from './overrides/PhotoAssetCard'
registerTypeRenderer('photo_asset', PhotoAssetCard)
```

**3. Rebuild:** `mvn install` — the new SPA lands in the fat jar automatically.

### What helpers are available

Exported from `@hitorro/search-ui-core` for use in overrides:

| Helper | Purpose |
| --- | --- |
| `pickMls(mlsField, lang, subField?)` | Pull the right-language `text` / `clean` / `segmented_ner` from a `core_mls` field. Default subField is `text`. |
| `collectNer(mlsEntries, lang)` | Parse `[{term&&NE_Type}]` brackets out of `segmented_ner` arrays — returns dedup'd `{term, type}[]`. |
| `entityColor(neType)` | Consistent chip colour per entity type (Person=emerald, Location=sky, Organization=violet, Date=amber, other=slate). |
| `fmtDate(value)` | Format ISO / epoch-ms / string into a compact localised timestamp. |
| `<HighlightedText text={s} query={q} />` | Wrap the first bare query term in a yellow `<mark>` — matches AutoRenderer's snippet highlight. |
| `<AutoRenderer />` | The generic fallback component — export it if you want to embed it inside a custom card as, say, an "unknown fields" expander. |

### How the type-name matching works

`TypeRenderer` looks up `hit.htType` in the registry. If a component is registered under that exact string, it renders. Otherwise `AutoRenderer` renders. Multiple type names can share a component:

```tsx
registerTypeRenderer('mail_email',   MailEmailCard)
registerTypeRenderer('mail_message', MailEmailCard)   // same shape, same card
```

### When to add a per-type card vs. rely on AutoRenderer

**Use AutoRenderer** when:
- The type has few enough fields that a generic list of chips is readable
- Title + body are the primary content and the extras don't need visual grouping
- You're prototyping and don't want to spend time on layout

**Ship a per-type card** when:
- The type has a canonical layout users expect (email → subject + sender + date + snippet)
- Some fields are more important than others and deserve visual prominence
- You want emoji/icon prefixes, colour semantics, or nested content (image thumbnail, embedded video preview)

### Testing a new card

The demo-app comes up on `mesh-up.sh` at `:8100`. Point the index picker at an index that has hits of your new type, and your card should render. If it doesn't:

1. Check `hit.htType` in the browser DevTools by dumping the search response
2. Confirm the type-name string in `registerTypeRenderer` matches exactly (case-sensitive)
3. Verify the SPA re-bundled — `curl -sI http://localhost:8100/` should show a different asset hash after your rebuild

## Customize the AppShell

The AppShell's layout is fixed in v1 — header row + two-column body. To customize:

### Change the title / header content

Pass a title:

```tsx
<AppShell title="My company search" />
```

For full-header replacement, don't use AppShell — compose the pieces:

```tsx
import { IndexPicker, ModeSwitch, LangSelect, SearchBox, FacetPanel, ResultsList,
         QueryBar, ResultsTable, FacetTree,
         ClientContext, createSearchClient, useSearchStore } from '@hitorro/search-ui-core'
```

Everything except `<AppShell>` is a standalone component you can compose freely.

### Different default mode

```tsx
import { useSearchStore } from '@hitorro/search-ui-core'
// … before mounting AppShell:
useSearchStore.setState({ mode: 'analyst' })
```

Or via a `useEffect` in your root component:

```tsx
React.useEffect(() => { useSearchStore.getState().setMode('analyst') }, [])
```

### Different default index

```tsx
useSearchStore.setState({ index: 'my-default-index' })
```

## Add a new BFF endpoint

`hitorro-search-ui` backend is a plain Spring Boot 3 app. Add a controller:

```java
// backend/src/main/java/com/hitorro/searchui/api/SavedSearchController.java
@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {
    @GetMapping
    public List<SavedSearch> list() { … }
    @PostMapping
    public SavedSearch save(@RequestBody SavedSearch req) { … }
}
```

The React library's client is minimal by design — add your own hook:

```ts
export function useSavedSearches() {
  return useQuery({
    queryKey: ['saved-searches'],
    queryFn: () => fetch('/api/saved-searches').then(r => r.json()),
  })
}
```

## Restyle

Tailwind is the styling layer. Every colour that matters is a named token in `frontend/tailwind.config.js`:

```js
theme: {
  extend: {
    colors: {
      hitorro: {
        primary: '#0e7490',   // teal — used for hover, focus rings, mode-switch active
        accent:  '#f97316',   // orange — reserved for CTAs, not currently used
        muted:   '#64748b',
      },
    },
  },
}
```

Override in your consumer app's Tailwind config:

```js
// your-app/tailwind.config.js
import base from '@hitorro/search-ui-core/tailwind.config.js'

export default {
  ...base,
  theme: {
    extend: {
      ...base.theme.extend,
      colors: {
        ...base.theme.extend.colors,
        hitorro: {
          primary: '#7c3aed',    // your brand purple
          accent:  '#f59e0b',
          muted:   '#71717a',
        },
      },
    },
  },
}
```

For deeper visual changes, wrap the components in your own styled wrappers — the library components accept standard React children/className/style so you can compose freely.

## Add a new schema-hint bucket

`IndexController.rendererHints()` classifies fields into `facetable`, `textSearch`, `date`, `mls`, `identifier`. To add a new bucket (say `range` for long fields you want to slider-filter on):

1. Edit `backend/.../api/IndexController.classifyField(...)` — check for `type == "core_long"` (or whatever) and add to the new bucket
2. Add the field to the returned JSON's `rendererHints`
3. Extend `RendererHints` in `frontend/packages/core/src/types/api.ts` with the new key
4. Consume it in a new component (say `RangeFilter`) or in an updated FacetPanel

## Package the library for standalone publishing

Today the demo-app consumes `@hitorro/search-ui-core` via pnpm workspace (`workspace:*`). To publish it to a registry:

```bash
cd frontend/packages/core
pnpm build
pnpm publish --access public   # or --tag next / --registry https://your.registry
```

`package.json` already has the right `main` / `module` / `types` / `exports` fields for consumers to `import { AppShell } from '@hitorro/search-ui-core'`. The build emits ESM only — CommonJS consumers can add a `require` conditional to the `exports` field if needed.

## Add a translation

The v1 UI has hardcoded English strings. To add i18n:

1. Install a small i18n lib (`react-i18next` or the `formatjs` set)
2. Wrap the AppShell in your provider
3. Replace the string literals in `frontend/packages/core/src/components/` with `t('key')` calls

Or, cheaper: fork the components you need (they're all small — the biggest is AutoRenderer at ~150 lines) and hardcode the target language.
