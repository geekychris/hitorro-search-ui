# Using hitorro-search-ui

Everything you can do in the shipped UI at `http://localhost:8100`.

## The header

```
[ hitorro search  |  [Index ▾]  [Lang ▾]  ────────────  [👤 End-user | 🔬 Analyst] ]
```

- **Index picker** — every Lucene index the mesh has produced under `~/.hitorro/pipelines/lucene/`. Only indexes with a `.jvs-type.json` sidecar are fully JVS-aware (the picker shows all of them anyway; unknown-type indexes fall back to the generic renderer).
- **Language dropdown** — ISO 639-1 codes. Picks which `mls` entry to display for `core_mls` fields and drives per-language analyzer selection in the query.
- **Mode switch** — flips the main body between end-user cards + facets and analyst table + query bar.

## End-user mode

Layout: **facet sidebar** on the left, **search box + results** on the right.

### Search box

- Debounced 300 ms (so mid-word typing doesn't fire a query per keystroke). Enter fires immediately.
- Bare terms fan out across `body.mls.clean` and `title.mls.clean` OR'd together, so `chase` matches both subject and body.
- Fielded queries (containing `:`) pass through to Lucene unchanged. `sender_domain:redfin.com` is treated as a fielded query.
- `*:*` returns everything (match-all).

### Facets

Auto-populated from the index schema's `facetable` fields (anything with `groups[].method: identifier` in the JVS type):

- Each panel shows the top 20 values with counts.
- Checkboxes; multiple checks within a panel = OR; across panels = AND.
- "clear" link on any panel with an active selection removes all its filters.

Facets recompute on every query change. If a panel goes empty after a filter, its checkboxes stay so you can uncheck.

### Result cards

Each hit renders via one of:

1. **Registered per-type card** — matched by `hit.htType`. `mail_email` and `mail_message` both render via `MailEmailCard` (subject headline + sender chip + received date + snippet + NER entities).
2. **Auto-renderer fallback** — title (from `doc.title` mls or `doc.name` or `doc.subject`) + type + id + snippet + identifier chips + NER entities.

Snippets are query-focused excerpts. When `summarize` returns nothing (short bodies, no matches), the BFF's local highlighter grabs a ±100-char window around the first match term. Every match is wrapped in a subtle yellow highlight in the card.

### Result card interactions

- **Click** → opens a detail drawer with the full JVS document pulled from the paired KV store (`<index>-idx` auto-maps to `<index>-kv`). Full JSON pretty-printed for debugging.
- **Facet chip in a card** → not a click target; use the facet panel to filter.
- **NER entity chip** → visual only in v1; wiring to filter by entity is a planned enhancement.

### Pagination

Prev/next below the results header. Page size is configurable via `?size=` on the search request (default 20, max 100). Infinite scroll is a Phase-2 enhancement.

### Sort

Defaults to relevance (Lucene score). The BFF accepts `sort=date_desc | date_asc | <field>:<dir>` — v1 UI doesn't expose the picker yet; drop into the analyst mode's query bar to test sort behaviour via a raw Lucene query.

## Analyst mode

Layout: **facet tree** on the left, **query bar + results table** on the right.

### Query bar

- Free-form Lucene syntax. No debounce — Enter or the Run button submits.
- Logical field paths (`body.mls.segmented_ner:NE_Person`) are expanded server-side via the JVS type sidecar to the physical projected names (`body.mls.segmented_ner.textmarkup_en_m`).
- Physical names also parse — backward compat.

Handy queries against a typical mail index:

- `*:*` — match all
- `body.mls.clean:chase` — full-text over cleaned English body
- `body.mls.segmented_ner:NE_Person` — every doc where NER found a person
- `sender_domain:github.com` — exact-match identifier field
- `+is_newsletter:false +body.mls.clean:kubernetes` — boolean combine

### Facet tree

Same fields as end-user facets, but flatter. Clicking a value **appends** `+field:"value"` to the query bar so the analyst can build queries iteratively via clicks. No auto-submit — hit Enter after clicking.

### Results table

- Auto-picked columns: `id`, `ht_type`, plus the first 4 `identifier`-hint fields from the schema.
- Column picker is a Phase-2 enhancement.
- **Click a row** → opens the row inspector with the full JVS as pretty-printed JSON.
- Pagination + status line (row count, stages used, took ms) at the top.

### Row inspector

- Right-side drawer with the raw JVS doc for the clicked row.
- Copy the JSON with `Cmd-C`.
- Doesn't call `/api/docs/*` — the search response already carries the full doc, so this is instant.

## Language switching

Flipping the header's Lang dropdown re-issues the search with `lang:` set. The coordinator's IndexRetriever selects the matching language-suffixed field (e.g. `title.mls.clean.text_de_m` for German). For MLS-typed fields, the renderer picks the matching lang's entry from the `mls[]` array.

If your index only has English data, switching to `de` returns zero hits — expected. Populate multi-lang indexes via a pipeline that sets `mls: [{lang: 'en', text: ...}, {lang: 'de', text: ...}]`.

## Keyboard

- `Enter` in end-user search box — force-fire query (bypass debounce)
- `Enter` in analyst query bar — submit
- `Cmd-Shift-R` — hard-refresh the SPA (picks up a new deploy)
- `Escape` — currently not wired to close drawers; workaround: click the ✕ or the darkened overlay

## Troubleshooting

- **"no indexes yet — run a pipeline"** — the driver's `~/.hitorro/pipelines/lucene/` is empty. Run one: `~/hitorro/hitorro-mesh-examples/scripts/mail-enrich-search.sh` seeds `mail-enriched-idx`.
- **Empty facet sidebar** — index has no sidecar, or the type has no identifier-method fields. The auto-renderer still works; facets just don't populate.
- **"fleet-retrieval unreachable"** — check `MESH_FLEET_RETRIEVAL=0` isn't set; check `/tmp/hitorro-mesh-smoke/logs/fleet-retrieval.log` for the reason.
- **404s on `/api/docs/*`** — the index's paired KV store isn't there. The BFF auto-tries `<name>-kv` when `<name>-idx` misses; if your pipeline uses a different naming pattern, edit `backend/.../api/DocController.java`.
