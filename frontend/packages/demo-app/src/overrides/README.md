# Per-type card templates

Each file in this directory is a React component that renders a hit for
a specific JVS `ht_type`. They're registered in `main.tsx` via
`registerTypeRenderer(htTypeName, component)`; the `TypeRenderer` inside
the library then picks the right one at render time based on
`hit.htType`. Unregistered types fall through to `AutoRenderer` — which
already renders title + body + identifier chips + dates + NER entities
from the schema hints.

## Adding a new type card

1. Create `<TypeName>Card.tsx` (e.g. `PhotoAssetCard.tsx`) in this
   directory. Copy the shape of `MailEmailCard.tsx`.

2. Pull whatever helpers you need from `@hitorro/search-ui-core`:
   ```ts
   import {
     type TypeRendererProps,
     pickMls, collectNer, entityColor, fmtDate, HighlightedText,
   } from '@hitorro/search-ui-core'
   ```

3. Extract the type-specific top-level fields from `hit.doc` and render
   them with sensible layout. Always render `title` + `body` (or their
   type-specific equivalents) so the row is meaningful even when the
   extras aren't populated.

4. Register in `main.tsx`:
   ```ts
   import { PhotoAssetCard } from './overrides/PhotoAssetCard'
   registerTypeRenderer('photo_asset', PhotoAssetCard)
   ```

## What "extra fields" to lean on

The type's `.jvs-type.json` sidecar declares every field's type +
`groups[].method`. Fields marked `identifier` are exact scalars —
perfect for chips (sender_domain, tags, kind). Fields marked `long`
work for badges + math (size, count, rating). Fields marked `date` want
`fmtDate()`. Fields declared `core_mls` want `pickMls(doc.field, lang)`.

Check the schema at runtime by opening `/api/indexes/<name>/schema`
in your browser — the `rendererHints` section tells you exactly
what's facet-friendly / date-formatted / text-searchable.

## Currently registered

| Type                     | Card               | Notes                                   |
| ------------------------ | ------------------ | --------------------------------------- |
| `mail_email`             | MailEmailCard      | Sender chip, received date, size, NER   |
| `mail_message`           | MailEmailCard      | Same shape as mail_email                |
| _(any other type)_       | AutoRenderer       | Generic — walks schema, shows chips     |

## Speculative future cards (not yet needed — no data)

When you add jvs-lucene sinks for these types, drop in a matching card:

| Type                | Fields worth highlighting                       |
| ------------------- | ----------------------------------------------- |
| `photo_asset`       | filename, taken_iso, kind, megapixels, has_location, is_favorite, thumbnail |
| `message`           | contact, service (iMessage/SMS), is_group, sent_iso, chat_name |
| `safari_visit`      | domain, visited_iso, lifetime_visits, url as a click-out |
| `screentime_event`  | app_bundle, app_category, duration_sec, started_iso |
| `article` / `doc`   | author, published, category, word_count         |
