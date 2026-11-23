# OSC Calendar - Authentik identity runbook

Operational steps to bring the OSC identity live. The blueprint
(`blueprints/osc-calendar.yaml`) is declarative and idempotent; these steps
put it on the running Authentik and verify it.

## 0. Pre-apply version check
- Confirm Authentik version: `docker compose exec server ak version` (or Admin UI → System).
- This blueprint targets **2024.8+**. If older, note that `redirect_uris` may need the
  legacy newline-string form and flows may not require `invalidation_flow`.
- Confirm the default signing keypair name matches the `osc-signing-key` identifier
  (`name: authentik Self-signed Certificate`); adjust if renamed.
- Confirm the stock invalidation flow slug `default-provider-invalidation-flow` exists
  (Admin UI → Flows). The provider `!Find`s it; if renamed/absent the apply fails.
- Confirm the built-in scope mappings exist for openid/profile/email/offline_access; if
  `offline_access` has no mapping on this version, add one (else no refresh token is issued).

## 1. DNS
- Create an **A record**: `id.openschoolcloud.nl` → `88.222.220.64`
  (the Caddy host fronting Authentik, PTR `cloud.aldewereldconsultancy.nl`).
  Add **AAAA** → `<AUTHENTIK_PUBLIC_IPV6>` only if that host serves public IPv6.
- In the Caddy that fronts Authentik, add a site block for `id.openschoolcloud.nl`
  reverse-proxying to the Authentik server (same upstream as your existing Authentik host),
  so Caddy provisions TLS for the new hostname.
- Verify: `curl -sI https://id.openschoolcloud.nl/ | head -1` returns a 200/302 from Authentik.

## 2. Apply the blueprint
Two options (pick one):
- **File discovery (recommended, IaC):** mount this repo's `identity/authentik/blueprints/`
  into the Authentik server + worker containers at `/blueprints/osc/` (compose volume),
  then Authentik auto-discovers and applies it. Trigger/inspect: Admin UI →
  Customization → Blueprints → find `osc-calendar-identity` → state should be
  **Successful**. Or CLI: `docker compose exec worker ak blueprints_apply /blueprints/osc/osc-calendar.yaml`.
- **One-off import:** Admin UI → Customization → Blueprints → Create → paste/upload the YAML.

After apply, confirm the objects exist: Admin UI shows Application **OSC Calendar**
(slug `osc-calendar`), Provider **osc-calendar** (public), Brand **id.openschoolcloud.nl**,
and flows **osc-enrollment / osc-authentication / osc-recovery**.

## 3. Live verification (the acceptance gate)
- **Discovery matches the app contract:**
  `curl -s https://id.openschoolcloud.nl/application/o/osc-calendar/.well-known/openid-configuration | jq '{issuer, authorization_endpoint, token_endpoint, scopes_supported}'`
  → `issuer` must equal `https://id.openschoolcloud.nl/application/o/osc-calendar/`;
  scopes include `openid profile email offline_access`.
- **MFA is mandatory (enrollment):** open `https://id.openschoolcloud.nl/if/flow/osc-enrollment/`,
  register a throwaway account → you MUST be forced through TOTP setup and shown static
  recovery codes; there is no skip.
- **MFA is mandatory (login):** log in with that account → after password you MUST be asked
  for the TOTP code. Confirm an account with no TOTP device is denied (not silently let in).
- **Redirect + PKCE:** from the OSC Calendar debug app, tap "Account maken"/"Inloggen";
  the Custom Tab must load the OSC branding and, after auth, return to
  `nl.openschoolcloud.calendar://oidc` with a code that the app exchanges for tokens
  (refresh token present because `offline_access` was granted).
- **Recovery:** trigger "Wachtwoord vergeten" → email link → set new password → login works.

## 4. Admin MFA-reset procedure (support path)
When a user loses BOTH their authenticator and their recovery codes, MFA reset is a
deliberate, logged admin action - never self-service (else MFA is theatre):
1. Verify the person's identity out-of-band (not over the same email that may be compromised).
2. Admin UI → Directory → Users → select user → **MFA Devices** → delete their TOTP (and static) device.
3. Record the reset: who requested, how identity was verified, who performed it, when.
   (Authentik writes an Event for the deletion; keep your own note alongside it.)
4. On next login the authentication flow's TOTP-validate stage (`not_configured_action: deny`)
   blocks them until they re-enroll a TOTP device → direct them to re-run enrollment or
   configure a device in their user settings.

## 5. Rollback
Blueprints are additive/idempotent. To remove: set the top-level entries' `state: absent`
in a copy and re-apply, or delete the Application/Provider/Brand/flows in the Admin UI.
Deleting the brand first detaches `id.openschoolcloud.nl` from OSC flows.

## Contract with Plan A (do not drift)
issuer `https://id.openschoolcloud.nl/application/o/osc-calendar/` · client_id `osc-calendar`
· public client (PKCE) · redirect `nl.openschoolcloud.calendar://oidc` · scopes
`openid profile email offline_access` · access 5m / refresh 30d. Changing any of these
means changing `android/app/build.gradle.kts` BuildConfig in lockstep.
