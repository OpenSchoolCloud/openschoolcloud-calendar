# OSC Calendar — Plan B: Authentik identity-backend (Blueprints)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sta de OSC-identiteit op in de bestaande self-hosted Authentik als een versiebeheerde blueprint: een OIDC-provider + application voor OSC Calendar, met verplichte TOTP-MFA in de enrollment- én authenticatie-flow, een recovery-flow, en een reproduceerbaar apply-/verify-/admin-reset-runbook.

**Architecture:** Eén Authentik **blueprint-YAML** (declaratief, idempotent, auto-toegepast door Authentik's blueprint-loader) definieert een OSC-**brand** op `id.openschoolcloud.nl`, een **public OAuth2/OIDC-provider** (Authorization Code + PKCE, geen client secret) met slug `osc-calendar`, en dedicated **enrollment/authentication/recovery-flows** waarin TOTP niet-optioneel is. Een apart **runbook** dekt DNS, het laden van de blueprint op de VPS, de handmatige admin-MFA-reset, en de live verificatie. De app (Plan A) verandert niet: de OIDC-contractwaarden zijn hier exact overgenomen.

**Tech Stack:** Authentik (self-hosted, blueprint schema `version: 1`), Docker-compose op de NIXFact-VPS (Caddy fronting Authentik), Hostinger DNS. Geen app-code.

## Global Constraints

Deze waarden MOETEN exact matchen met wat Plan A hardcodeert in `android/app/build.gradle.kts` (`BuildConfig`), anders faalt de login:

- **Issuer:** `https://id.openschoolcloud.nl/application/o/osc-calendar/` → dus application **slug = `osc-calendar`** (Authentik leidt de issuer-pad af uit de slug) en de brand-domein = `id.openschoolcloud.nl`.
- **client_id:** `osc-calendar`
- **client_type:** `public` (geen secret — AppAuth mobiel met PKCE)
- **redirect_uri:** `nl.openschoolcloud.calendar://oidc` (exact, geen trailing slash)
- **scopes:** `openid profile email offline_access` (offline_access ⇒ refresh token)
- **Token-levensduur:** access-token kort (`minutes=5`), refresh-token `days=30` (spec §6).
- **MFA verplicht in de flow** (niet in de app): TOTP-stage niet-optioneel bij enrollment; authenticatie-flow verplicht TOTP-validatie.
- **Geen kind-PII op de IdP:** de IdP kent alleen ouder-accounts (e-mail, wachtwoord-hash, TOTP-secret). Blueprint bevat geen enkel kindveld.
- **Privacy/soevereiniteit:** draait op eigen infra; geen externe IdP-federatie in deze blueprint.
- **Authentik-host:** `88.222.220.64` (PTR `cloud.aldewereldconsultancy.nl`, Hostinger-VPS, door de eigenaar bevestigd als de Caddy-vóór-Authentik-host). `id.openschoolcloud.nl` → A `88.222.220.64`. IPv6/AAAA optioneel (`<AUTHENTIK_PUBLIC_IPV6>`, alleen zetten als de host publiek IPv6 serveert).

**Uitvoergrens:** Alle *bestanden* (blueprint + runbook) worden in de repo geauthord en zijn hier volledig te maken. De *apply + live verificatie* (DNS zetten, blueprint op de VPS laden, enrollment testen) gebeurt op de VPS door de eigenaar (of via `!`-SSH) — die stappen zijn als runbook opgenomen, niet als iets dat in deze sessie draait.

**Authentik-versiegevoeligheid:** het blueprint-schema is over versies geëvolueerd (met name `redirect_uris` werd rond 2024.x een lijst van objecten i.p.v. een newline-string, en flows kregen een verplichte `authentication`/`invalidation_flow`). De blueprint hieronder is geschreven voor **Authentik 2024.8+**. Taak 4 bevat een expliciete versie-check tegen de draaiende instance vóór apply.

---

### Task 1: OIDC-provider + application + OSC-brand

**Files:**
- Create: `identity/authentik/blueprints/osc-calendar.yaml` (skeleton + provider + application + brand entries)

**Interfaces:**
- Produces: een Authentik `oauth2provider` (naam `osc-calendar`), een `application` (slug `osc-calendar`) en een `brand` (domein `id.openschoolcloud.nl`). Taken 2/3 voegen flows toe en verwijzen naar deze provider via blueprint-`id` refs.

- [ ] **Step 1: Schrijf de blueprint-header + signing key lookup**

Maak `identity/authentik/blueprints/osc-calendar.yaml`:

```yaml
# OSC Calendar — Authentik identity blueprint
# Applied by Authentik's blueprint loader (mount into /blueprints or import via UI/API).
# OIDC contract values MUST match the OSC Calendar app BuildConfig (Plan A).
version: 1
metadata:
  name: osc-calendar-identity
  labels:
    blueprints.goauthentik.io/description: "OSC Calendar OIDC provider, app, brand and MFA-enforced flows"
entries:
  # --- Reuse the default RSA signing key that ships with Authentik ---
  - model: authentik_crypto.certificatekeypair
    id: osc-signing-key
    identifiers:
      name: authentik Self-signed Certificate
    state: present
```

Note: `state: present` with only `identifiers` (no `attrs`) means "find the existing object and bind it to id `osc-signing-key`" — it does not create/modify. If the instance has renamed its default keypair, adjust the `name` identifier during Task 4's pre-apply check.

- [ ] **Step 2: Append the OAuth2/OIDC provider**

Append to `entries:`:

```yaml
  - model: authentik_providers_oauth2.oauth2provider
    id: osc-provider
    identifiers:
      name: osc-calendar
    attrs:
      name: osc-calendar
      client_type: public
      client_id: osc-calendar
      authorization_flow: !KeyOf osc-authentication-flow
      invalidation_flow: !Find [authentik_flows.flow, [slug, default-provider-invalidation-flow]]
      signing_key: !KeyOf osc-signing-key
      sub_mode: hashed_user_id
      include_claims_in_id_token: true
      issuer_mode: per_provider
      access_code_validity: "minutes=1"
      access_token_validity: "minutes=5"
      refresh_token_validity: "days=30"
      redirect_uris:
        - matching_mode: strict
          url: "nl.openschoolcloud.calendar://oidc"
      property_mappings:
        - !Find [authentik_providers_oauth2.scopemapping, [scope_name, openid]]
        - !Find [authentik_providers_oauth2.scopemapping, [scope_name, profile]]
        - !Find [authentik_providers_oauth2.scopemapping, [scope_name, email]]
        - !Find [authentik_providers_oauth2.scopemapping, [scope_name, offline_access]]
```

`authorization_flow: !KeyOf osc-authentication-flow` forward-references the flow created in Task 3 — blueprints resolve refs across the whole file, order-independent. If the running Authentik has no `offline_access` scope mapping, Task 4's check adds it (older versions grant refresh tokens whenever the client requests `offline_access` without a dedicated mapping — verify per version).

- [ ] **Step 3: Append the application (slug drives the issuer path)**

```yaml
  - model: authentik_core.application
    id: osc-application
    identifiers:
      slug: osc-calendar
    attrs:
      name: OSC Calendar
      slug: osc-calendar
      provider: !KeyOf osc-provider
      meta_launch_url: ""
      policy_engine_mode: any
```

- [ ] **Step 4: Append the OSC brand for id.openschoolcloud.nl**

```yaml
  - model: authentik_core.brand
    id: osc-brand
    identifiers:
      domain: id.openschoolcloud.nl
    attrs:
      domain: id.openschoolcloud.nl
      branding_title: OpenSchoolCloud
      default: false
      flow_authentication: !KeyOf osc-authentication-flow
      flow_enrollment: !KeyOf osc-enrollment-flow
      flow_recovery: !KeyOf osc-recovery-flow
```

A dedicated brand (not `default: true`) scopes OSC's flows/branding to the `id.openschoolcloud.nl` host without touching the existing NIXFact brand — this is the "aparte brand" blast-radius separation from the spec.

- [ ] **Step 5: Validate YAML syntax locally**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('identity/authentik/blueprints/osc-calendar.yaml'))" ; echo "note: !KeyOf/!Find are Authentik custom tags; a plain safe_load will error on them — instead validate with the loader below"`

Because the file uses Authentik's custom YAML tags (`!KeyOf`, `!Find`), a plain `yaml.safe_load` fails. Validate structure with a tag-tolerant load instead:

Run:
```bash
python3 - <<'PY'
import yaml
class T(yaml.SafeLoader): pass
for tag in ("!KeyOf","!Find","!Format","!Context","!Env"):
    T.add_constructor(tag, lambda l,n: None)
# register multi/implicit
yaml.add_multi_constructor("!", lambda l,s,n: None, Loader=T)
d=yaml.load(open("identity/authentik/blueprints/osc-calendar.yaml"),Loader=T)
assert d["version"]==1 and d["entries"], "structure invalid"
print("entries:", len(d["entries"]))
PY
```
Expected: prints `entries: 4` (so far), no exception.

- [ ] **Step 6: Commit**

```bash
git add identity/authentik/blueprints/osc-calendar.yaml
git commit -m "Plan B T1: OSC OIDC provider + application + brand blueprint entries"
```

---

### Task 2: Enrollment-flow met verplichte TOTP + statische herstelcodes

**Files:**
- Modify: `identity/authentik/blueprints/osc-calendar.yaml` (append enrollment flow + stages + bindings)

**Interfaces:**
- Consumes: nothing (self-contained flow).
- Produces: `authentik_flows.flow` with blueprint id `osc-enrollment-flow`, referenced by the brand (T1) and used self-service.

- [ ] **Step 1: Append the enrollment flow object**

```yaml
  - model: authentik_flows.flow
    id: osc-enrollment-flow
    identifiers:
      slug: osc-enrollment
    attrs:
      name: OSC Calendar Registratie
      title: Maak je OSC-account
      designation: enrollment
      authentication: none
      layout: stacked
```

- [ ] **Step 2: Append the prompt stage (email + password) and its fields**

```yaml
  - model: authentik_stages_prompt.prompt
    id: osc-field-email
    identifiers:
      field_key: email
    attrs:
      label: E-mailadres
      type: email
      required: true
      placeholder: "jij@voorbeeld.nl"
      order: 0
  - model: authentik_stages_prompt.prompt
    id: osc-field-password
    identifiers:
      field_key: password
    attrs:
      label: Wachtwoord
      type: password
      required: true
      order: 1
  - model: authentik_stages_prompt.prompt
    id: osc-field-password-repeat
    identifiers:
      field_key: password_repeat
    attrs:
      label: Herhaal wachtwoord
      type: password
      required: true
      order: 2
  - model: authentik_stages_prompt.promptstage
    id: osc-enroll-prompt
    identifiers:
      name: osc-enroll-prompt
    attrs:
      name: osc-enroll-prompt
      fields:
        - !KeyOf osc-field-email
        - !KeyOf osc-field-password
        - !KeyOf osc-field-password-repeat
```

- [ ] **Step 3: Append user-write, mandatory TOTP, static recovery codes, and login stages**

```yaml
  - model: authentik_stages_user_write.userwritestage
    id: osc-user-write
    identifiers:
      name: osc-user-write
    attrs:
      name: osc-user-write
      create_users_as_inactive: false
      user_creation_mode: always_create
  - model: authentik_stages_authenticator_totp.authenticatortotpstage
    id: osc-totp-setup
    identifiers:
      name: osc-totp-setup
    attrs:
      name: osc-totp-setup
      digits: 6
  - model: authentik_stages_authenticator_static.authenticatorstaticstage
    id: osc-static-codes
    identifiers:
      name: osc-static-codes
    attrs:
      name: osc-static-codes
      token_count: 6
  - model: authentik_stages_user_login.userloginstage
    id: osc-user-login
    identifiers:
      name: osc-user-login
    attrs:
      name: osc-user-login
      session_duration: "days=30"
```

The TOTP setup stage sits in the enrollment flow with no "skip" binding (Step 4), which is what makes MFA **mandatory at signup**. The static stage generates the one-time recovery codes the app's RecoveryCodesScreen (Plan A) tells the user to save.

- [ ] **Step 4: Bind the stages to the enrollment flow in order**

```yaml
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-enrollment-flow
      stage: !KeyOf osc-enroll-prompt
      order: 10
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-enrollment-flow
      stage: !KeyOf osc-user-write
      order: 20
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-enrollment-flow
      stage: !KeyOf osc-totp-setup
      order: 30
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-enrollment-flow
      stage: !KeyOf osc-static-codes
      order: 40
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-enrollment-flow
      stage: !KeyOf osc-user-login
      order: 50
```

- [ ] **Step 5: Validate + commit**

Run the tag-tolerant validator from Task 1 Step 5 (expected `entries` count now ~19).
```bash
git add identity/authentik/blueprints/osc-calendar.yaml
git commit -m "Plan B T2: enrollment flow with mandatory TOTP + static recovery codes"
```

---

### Task 3: Authenticatie-flow (verplichte TOTP) + recovery-flow

**Files:**
- Modify: `identity/authentik/blueprints/osc-calendar.yaml` (append auth flow + recovery flow + stages + bindings)

**Interfaces:**
- Produces: `osc-authentication-flow` (referenced by provider T1 + brand T1) and `osc-recovery-flow` (referenced by brand T1).

- [ ] **Step 1: Append the authentication flow + identification/password stages**

```yaml
  - model: authentik_flows.flow
    id: osc-authentication-flow
    identifiers:
      slug: osc-authentication
    attrs:
      name: OSC Calendar Inloggen
      title: Inloggen bij OSC
      designation: authentication
      authentication: require_unauthenticated
      layout: stacked
  - model: authentik_stages_identification.identificationstage
    id: osc-ident
    identifiers:
      name: osc-ident
    attrs:
      name: osc-ident
      user_fields:
        - email
        - username
      password_stage: !KeyOf osc-password
      enrollment_flow: !KeyOf osc-enrollment-flow
      recovery_flow: !KeyOf osc-recovery-flow
  - model: authentik_stages_password.passwordstage
    id: osc-password
    identifiers:
      name: osc-password
    attrs:
      name: osc-password
      backends:
        - authentik.core.auth.InbuiltBackend
```

Wiring `enrollment_flow`/`recovery_flow` into the identification stage gives the login screen its "Account maken" and "Wachtwoord vergeten" links — so the app's single Custom Tab entry (Plan A launches the same intent for both buttons) lands users who can branch to sign-up or recovery from Authentik's own UI.

- [ ] **Step 2: Append the mandatory TOTP validation stage + login stage**

```yaml
  - model: authentik_stages_authenticator_validate.authenticatorvalidatestage
    id: osc-totp-validate
    identifiers:
      name: osc-totp-validate
    attrs:
      name: osc-totp-validate
      device_classes:
        - totp
        - static
      not_configured_action: deny
      last_auth_threshold: "seconds=0"
```

`not_configured_action: deny` means an account without a configured TOTP device cannot pass login — combined with the enrollment flow forcing TOTP setup, MFA is unconditionally enforced. `static` is included so a user who lost their authenticator can still pass with a recovery code, then re-enroll.

- [ ] **Step 3: Bind the authentication-flow stages**

```yaml
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-authentication-flow
      stage: !KeyOf osc-ident
      order: 10
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-authentication-flow
      stage: !KeyOf osc-password
      order: 20
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-authentication-flow
      stage: !KeyOf osc-totp-validate
      order: 30
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-authentication-flow
      stage: !KeyOf osc-user-login
      order: 40
```

(`osc-user-login` is reused from Task 2.)

- [ ] **Step 4: Append the recovery flow (email link → new password)**

```yaml
  - model: authentik_flows.flow
    id: osc-recovery-flow
    identifiers:
      slug: osc-recovery
    attrs:
      name: OSC Calendar Herstel
      title: Wachtwoord opnieuw instellen
      designation: recovery
      authentication: require_unauthenticated
      layout: stacked
  - model: authentik_stages_identification.identificationstage
    id: osc-recovery-ident
    identifiers:
      name: osc-recovery-ident
    attrs:
      name: osc-recovery-ident
      user_fields:
        - email
  - model: authentik_stages_email.emailstage
    id: osc-recovery-email
    identifiers:
      name: osc-recovery-email
    attrs:
      name: osc-recovery-email
      use_global_settings: true
      token_expiry: 30
      subject: OSC Calendar — wachtwoord opnieuw instellen
  - model: authentik_stages_prompt.prompt
    id: osc-field-newpw
    identifiers:
      field_key: password
    attrs:
      label: Nieuw wachtwoord
      type: password
      required: true
      order: 0
  - model: authentik_stages_prompt.prompt
    id: osc-field-newpw-repeat
    identifiers:
      field_key: password_repeat
    attrs:
      label: Herhaal nieuw wachtwoord
      type: password
      required: true
      order: 1
  - model: authentik_stages_prompt.promptstage
    id: osc-recovery-prompt
    identifiers:
      name: osc-recovery-prompt
    attrs:
      name: osc-recovery-prompt
      fields:
        - !KeyOf osc-field-newpw
        - !KeyOf osc-field-newpw-repeat
  - model: authentik_stages_user_write.userwritestage
    id: osc-recovery-write
    identifiers:
      name: osc-recovery-write
    attrs:
      name: osc-recovery-write
      user_creation_mode: never_create
```

Note the recovery `subject` uses `—`? NO — the OSC style forbids em-dashes in user-facing text. The subject above uses a hyphen with spaces (` - `). Keep it that way; do not introduce `—`.

- [ ] **Step 5: Bind the recovery-flow stages**

```yaml
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-recovery-flow
      stage: !KeyOf osc-recovery-ident
      order: 10
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-recovery-flow
      stage: !KeyOf osc-recovery-email
      order: 20
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-recovery-flow
      stage: !KeyOf osc-recovery-prompt
      order: 30
  - model: authentik_flows.flowstagebinding
    identifiers:
      target: !KeyOf osc-recovery-flow
      stage: !KeyOf osc-recovery-write
      order: 40
```

- [ ] **Step 6: Validate + commit**

Run the tag-tolerant validator (expected `entries` count now ~35). Also grep the file for em-dashes and fail if any: `! grep -n "—" identity/authentik/blueprints/osc-calendar.yaml` (expected: no matches).
```bash
git add identity/authentik/blueprints/osc-calendar.yaml
git commit -m "Plan B T3: authentication flow (mandatory TOTP) + recovery flow"
```

---

### Task 4: Runbook — DNS, apply, admin-reset, live verificatie

**Files:**
- Create: `identity/authentik/RUNBOOK.md`

**Interfaces:**
- Consumes: the finished blueprint. Produces the operational procedure the owner runs on the VPS.

- [ ] **Step 1: Write the runbook**

Create `identity/authentik/RUNBOOK.md`:

````markdown
# OSC Calendar — Authentik identity runbook

Operational steps to bring the OSC identity live. The blueprint
(`blueprints/osc-calendar.yaml`) is declarative and idempotent; these steps
put it on the running Authentik and verify it.

## 0. Pre-apply version check
- Confirm Authentik version: `docker compose exec server ak version` (or Admin UI → System).
- This blueprint targets **2024.8+**. If older, note that `redirect_uris` may need the
  legacy newline-string form and flows may not require `invalidation_flow`.
- Confirm the default signing keypair name matches the `osc-signing-key` identifier
  (`name: authentik Self-signed Certificate`); adjust if renamed.

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
deliberate, logged admin action — never self-service (else MFA is theatre):
1. Verify the person's identity out-of-band (not over the same email that may be compromised).
2. Admin UI → Directory → Users → select user → **MFA Devices** → delete their TOTP (and static) device.
3. Record the reset: who requested, how identity was verified, who performed it, when.
   (Authentik writes an Event for the deletion; keep your own note alongside it.)
4. On next login the authentication flow's TOTP-validate stage (`not_configured_action: deny`)
   blocks them until they re-enroll a TOTP device — direct them to re-run enrollment or
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
````

- [ ] **Step 2: Commit**

```bash
git add identity/authentik/RUNBOOK.md
git commit -m "Plan B T4: DNS/apply/admin-reset/verification runbook"
```

---

## Self-Review

**1. Spec coverage** (spec §5 identity-backend, §6 MFA & herstel):
- OIDC provider/app matching the app contract → T1 (issuer via slug, public+PKCE, redirect, scopes, token lifetimes). ✓
- Aparte OSC-brand (blast-radius) → T1 brand on id.openschoolcloud.nl. ✓
- Enrollment met verplichte TOTP → T2 (TOTP stage bound, no skip). ✓
- Herstelcodes (statisch, 1x) → T2 static stage (feeds Plan A's RecoveryCodesScreen). ✓
- Authenticatie met verplichte TOTP → T3 (authenticatorvalidate, not_configured_action: deny). ✓
- Recovery-flow (e-maillink → nieuw wachtwoord) → T3. ✓
- Admin-reset procedure (gelogd, handmatig, geen self-service) → T4 §4. ✓
- Token-levensduur (access kort, refresh 30d) → T1 provider attrs. ✓
- Geen kind-PII op IdP → blueprint bevat geen kindvelden (alleen ouder email/password/TOTP). ✓
- DNS + TLS voor id.openschoolcloud.nl → T4 §1. ✓
- Live verificatie tegen de app-contractwaarden → T4 §3. ✓

**2. Placeholder scan:** `<AUTHENTIK_PUBLIC_IPV4>`/`<AUTHENTIK_PUBLIC_IPV6>` are intentional owner-supplied values (documented in Global Constraints), not plan gaps. No "TBD". The signing-key name and `offline_access` scope mapping are flagged as version-check items in T4 §0 rather than assumed.

**3. Type consistency:** blueprint `id` refs are consistent across tasks — `osc-provider`, `osc-application`, `osc-brand`, `osc-authentication-flow` (referenced by both provider T1 and brand T1 and defined in T3), `osc-enrollment-flow` (brand T1, defined T2), `osc-recovery-flow` (brand T1, defined T3), `osc-user-login` (defined T2, reused T3). Slug `osc-calendar` (application) ↔ issuer path ↔ Plan A BuildConfig — consistent. redirect/scopes/client_id copied verbatim from Plan A's Global Constraints.

**Known cross-version risk (flagged, not silently assumed):** `redirect_uris` object-list form, `invalidation_flow` requirement, and the `offline_access` scope mapping vary by Authentik version — T4 §0 makes the operator check these against the running instance before apply, because this plan cannot be executed/verified against the live Authentik from the authoring environment.
