import React from 'react'
import ReactDOM from 'react-dom/client'
import { AppShell, registerTypeRenderer } from '@hitorro/search-ui-core'
import { MailEmailCard } from './overrides/MailEmailCard'
import './index.css'

// Register per-type overrides BEFORE mounting AppShell so the first
// render already picks them up. See overrides/README.md for the
// pattern; add more here as your data grows — PhotoAssetCard,
// MessageBubble, ArticleCard, etc.
//
// Multiple type names can share a card — mail_email and mail_message
// are structurally identical (same sysobject-inherited fields + same
// mail-specific top-level extras), so both render via MailEmailCard.
registerTypeRenderer('mail_email',   MailEmailCard)
registerTypeRenderer('mail_message', MailEmailCard)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppShell title="hitorro search" />
  </React.StrictMode>
)
