# OSC Calendar — Plan C: OSC-Cloud sync-backend (betaalde gezins-sync)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bouw de betaalde OSC-sync: een kleine FastAPI-service die per gezin een account op een **gedeelde OSC-Nextcloud** provisioneert (OCS-API), een **Mollie-abonnement per gezinslid** met **30-dagen-trial zonder kaart** beheert, en de app een OIDC-beveiligde API geeft (trial starten, status pollen, CalDAV-gegevens ophalen). De app vult zijn `OscCloudProvider`-stub in en degradeert zacht naar lokaal bij niet-betaling.

**Architecture:** Nieuwe service `osc-cloud-sync` (Python 3.12 + FastAPI + PostgreSQL/SQLAlchemy + Alembic + httpx), in de stijl van het bestaande `startnextcloud-provisioning`. De service is een **OAuth2 resource server**: hij valideert de Authentik-uitgegeven access-tokens (JWKS) en gebruikt `sub` als eigenaar-id. Provisioning is **licht** (één Nextcloud-user per gezin via OCS, geen container-per-gezin). Billing is Mollie (per-lid bedrag = €1 × actieve profielen; jaar = −20%). Een trial-state-machine (TRIAL→ACTIVE / PAST_DUE / PAUSED) stuurt of de sync leeft; de app leest `GET /v1/sync/status` en zet `SyncScope` terug naar LOCAL bij PAUSED. Geen kind-PII in de backend — alleen `member_count` (getal), de eigenaar-`sub`, en de gegenereerde Nextcloud-username.

**Tech Stack:** Python 3.12, FastAPI, Uvicorn, SQLAlchemy 2.x + Alembic, httpx, python-jose (JWT/JWKS), pytest + respx (httpx-mock). App-kant: Kotlin (bestaande calendar-repo). Mollie REST v2 via httpx (geen extra SDK — spec: minimale dependencies).

## Global Constraints

- **Twee repos:** backend in nieuwe repo/dir `/mnt/nvme1tb/projects/osc-cloud-sync`; app-taken in `/mnt/nvme1tb/projects/openschoolcloud-calendar` (branch `spec/osc-cloud-sync-backend`).
- **Contract met Plan A/B:** de app authenticeert met een Authentik-access-token (issuer `https://id.openschoolcloud.nl/application/o/osc-calendar/`, audience/client `osc-calendar`). De backend valideert daartegen. CalDAV-gegevens die de backend teruggeeft worden door de app in de bestaande `CredentialStorage` (Nextcloud-app-password, gescheiden van de OIDC-tokens) opgeslagen en via de bestaande CalDAV-syncstack gebruikt.
- **Prijs:** €1,00 per actief profiel per maand; jaarplan = €1 × leden × 12 × 0,8. Bedrag beweegt mee met `member_count`.
- **Trial:** 30 dagen, **geen kaart vooraf**; app toont betaalprompt rond dag 25; niet betaald → status PAUSED, Nextcloud-user disabled, **data blijft** (niet verwijderd), app degradeert naar LOCAL; betalen heractiveert.
- **Privacy:** geen kind-PII in de backend. Alleen `member_count`, eigenaar-`sub`, provisioned NC-username, Mollie-ids. Kind-namen/agenda blijven client-side of in de Nextcloud-data (van de klant), niet in de billing-DB.
- **Secrets via env, nooit in code/repo/chat:** `MOLLIE_API_KEY` (test-placeholder `test_QkEj…D7x`, live-key alleen server-side env), `NEXTCLOUD_ADMIN_USER`/`NEXTCLOUD_ADMIN_PASS`, `OIDC_ISSUER`, `OIDC_JWKS_URL`, DB-URL, `MOLLIE_WEBHOOK_*`. `.env.example` met dummy-waarden; echte `.env` in `.gitignore`.
- **Soevereiniteit:** shared OSC-Nextcloud op EU-infra (Cyso/Fuga, zoals de bestaande orchestrator). Geen US-cloud defaults.
- **Stijl:** Python type-hints overal; `ruff` clean; geen em-dashes ("—") in user-facing strings (mails/prompts); functies klein; geen over-abstractie.
- **Idempotentie:** trial-start en provisioning zijn idempotent op `sub` (herhaalde calls mogen geen tweede NC-user/subscription maken).

**Uitvoergrens:** de FastAPI-service is hier met pytest (gemockte httpx via `respx`) end-to-end testbaar. **Live** provisioning tegen de echte Nextcloud, echte Mollie-betalingen en Authentik-JWKS draaien pas op de VPS — die stappen staan als runbook (laatste taak), niet als iets dat in deze sessie live draait.

---

### Task 1: Service-scaffold (FastAPI, config, Docker, pytest)

**Files (repo `/mnt/nvme1tb/projects/osc-cloud-sync`):**
- Create: `pyproject.toml`, `app/__init__.py`, `app/main.py`, `app/config.py`, `.env.example`, `.gitignore`, `Dockerfile`, `tests/__init__.py`, `tests/conftest.py`, `tests/test_health.py`

**Interfaces:**
- Produces: `app.main:app` (FastAPI), `app.config.Settings` (pydantic-settings) with all env vars. Consumed by every later task.

- [ ] **Step 1: Init the repo + pyproject**

```bash
mkdir -p /mnt/nvme1tb/projects/osc-cloud-sync && cd /mnt/nvme1tb/projects/osc-cloud-sync && git init -q
```

Create `pyproject.toml`:

```toml
[project]
name = "osc-cloud-sync"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
  "fastapi>=0.115",
  "uvicorn[standard]>=0.30",
  "sqlalchemy>=2.0",
  "alembic>=1.13",
  "psycopg[binary]>=3.2",
  "httpx>=0.27",
  "python-jose[cryptography]>=3.3",
  "pydantic-settings>=2.4",
]

[project.optional-dependencies]
dev = ["pytest>=8", "respx>=0.21", "ruff>=0.6", "anyio>=4"]

[tool.ruff]
target-version = "py312"
line-length = 100
```

- [ ] **Step 2: Config**

Create `app/config.py`:

```python
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+psycopg://osc:osc@localhost:5432/osc_cloud_sync"

    # OIDC (Authentik) — resource-server validation
    oidc_issuer: str = "https://id.openschoolcloud.nl/application/o/osc-calendar/"
    oidc_jwks_url: str = (
        "https://id.openschoolcloud.nl/application/o/osc-calendar/jwks/"
    )
    oidc_audience: str = "osc-calendar"

    # Shared OSC Nextcloud (OCS provisioning)
    nextcloud_base_url: str = "https://cloud.openschoolcloud.nl"
    nextcloud_admin_user: str = "admin"
    nextcloud_admin_pass: str = "change-me"

    # Mollie
    mollie_api_key: str = "test_placeholder"
    mollie_webhook_url: str = "https://sync.openschoolcloud.nl/webhooks/mollie"
    public_base_url: str = "https://sync.openschoolcloud.nl"

    # Pricing (cents)
    price_per_member_cents: int = 100  # EUR 1.00 / member / month
    annual_discount: float = 0.20
    trial_days: int = 30


def get_settings() -> Settings:
    return Settings()
```

- [ ] **Step 3: App + health route**

Create `app/main.py`:

```python
from fastapi import FastAPI

app = FastAPI(title="OSC Cloud Sync", version="0.1.0")


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}
```

- [ ] **Step 4: .env.example, .gitignore, Dockerfile**

`.env.example` (dummy values, no real secrets):
```
DATABASE_URL=postgresql+psycopg://osc:osc@localhost:5432/osc_cloud_sync
OIDC_ISSUER=https://id.openschoolcloud.nl/application/o/osc-calendar/
OIDC_JWKS_URL=https://id.openschoolcloud.nl/application/o/osc-calendar/jwks/
OIDC_AUDIENCE=osc-calendar
NEXTCLOUD_BASE_URL=https://cloud.openschoolcloud.nl
NEXTCLOUD_ADMIN_USER=admin
NEXTCLOUD_ADMIN_PASS=change-me
MOLLIE_API_KEY=test_replace_me
MOLLIE_WEBHOOK_URL=https://sync.openschoolcloud.nl/webhooks/mollie
PUBLIC_BASE_URL=https://sync.openschoolcloud.nl
```
`.gitignore`: `.env`, `.venv/`, `__pycache__/`, `*.pyc`, `.pytest_cache/`, `.ruff_cache/`.
`Dockerfile`: python:3.12-slim, install `.`, run `uvicorn app.main:app --host 0.0.0.0 --port 8000`.

- [ ] **Step 5: pytest health test**

`tests/conftest.py`:
```python
import pytest
from fastapi.testclient import TestClient
from app.main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)
```
`tests/test_health.py`:
```python
def test_healthz(client):
    r = client.get("/healthz")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}
```

- [ ] **Step 6: Install + run**

```bash
cd /mnt/nvme1tb/projects/osc-cloud-sync
python3 -m venv .venv && . .venv/bin/activate && pip install -q -e ".[dev]"
pytest -q
```
Expected: 1 passed.

- [ ] **Step 7: Commit**
```bash
git add -A && git commit -m "Plan C T1: FastAPI scaffold, config, health route, pytest"
```

---

### Task 2: OIDC resource-server auth dependency

**Files:**
- Create: `app/auth.py`, `tests/test_auth.py`

**Interfaces:**
- Produces: `require_owner()` FastAPI dependency returning `Owner(sub: str, email: str | None)`. Validates the bearer JWT against Authentik's JWKS (issuer + audience + signature + expiry). Consumed by all `/v1/*` endpoints (Tasks 6-7).

- [ ] **Step 1: Write the failing test**

`tests/test_auth.py` — generate an RSA keypair in-test, sign a token, serve a JWKS via `respx`, assert valid token → Owner and tampered/expired → 401:

```python
import time
import pytest
import respx
import httpx
from jose import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
from app.config import get_settings
from app.auth import decode_token, AuthError

def _keypair():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    priv = key.private_bytes(serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8, serialization.NoEncryption()).decode()
    pub_numbers = key.public_key().public_numbers()
    import base64
    def b64(n): 
        b = n.to_bytes((n.bit_length()+7)//8, "big")
        return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
    jwk = {"kty":"RSA","kid":"test","use":"sig","alg":"RS256",
           "n":b64(pub_numbers.n),"e":b64(pub_numbers.e)}
    return priv, {"keys":[jwk]}

@respx.mock
def test_valid_token_returns_owner():
    s = get_settings()
    priv, jwks = _keypair()
    respx.get(s.oidc_jwks_url).mock(return_value=httpx.Response(200, json=jwks))
    tok = jwt.encode({"sub":"abc","email":"a@b.nl","iss":s.oidc_issuer,
        "aud":s.oidc_audience,"exp":int(time.time())+300}, priv,
        algorithm="RS256", headers={"kid":"test"})
    owner = decode_token(tok)
    assert owner.sub == "abc" and owner.email == "a@b.nl"

@respx.mock
def test_expired_token_rejected():
    s = get_settings()
    priv, jwks = _keypair()
    respx.get(s.oidc_jwks_url).mock(return_value=httpx.Response(200, json=jwks))
    tok = jwt.encode({"sub":"abc","iss":s.oidc_issuer,"aud":s.oidc_audience,
        "exp":int(time.time())-10}, priv, algorithm="RS256", headers={"kid":"test"})
    with pytest.raises(AuthError):
        decode_token(tok)
```

- [ ] **Step 2: Run → fails** (`app.auth` missing). `pytest -q tests/test_auth.py` → FAIL.

- [ ] **Step 3: Implement `app/auth.py`**

```python
from dataclasses import dataclass
import httpx
from fastapi import Depends, Header
from fastapi import HTTPException
from jose import jwt, JWTError
from app.config import get_settings

_jwks_cache: dict | None = None


class AuthError(Exception):
    pass


@dataclass
class Owner:
    sub: str
    email: str | None


def _jwks() -> dict:
    global _jwks_cache
    if _jwks_cache is None:
        s = get_settings()
        _jwks_cache = httpx.get(s.oidc_jwks_url, timeout=10).json()
    return _jwks_cache


def decode_token(token: str) -> Owner:
    s = get_settings()
    try:
        claims = jwt.decode(
            token, _jwks(), algorithms=["RS256"],
            audience=s.oidc_audience, issuer=s.oidc_issuer,
        )
    except JWTError as e:
        raise AuthError(str(e)) from e
    sub = claims.get("sub")
    if not sub:
        raise AuthError("no sub")
    return Owner(sub=sub, email=claims.get("email"))


def require_owner(authorization: str = Header(default="")) -> Owner:
    if not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="missing bearer token")
    try:
        return decode_token(authorization[7:])
    except AuthError as e:
        raise HTTPException(status_code=401, detail=str(e)) from e
```

Note: reset `_jwks_cache = None` in a test fixture teardown if tests interfere; `respx` intercepts the `httpx.get`.

- [ ] **Step 4: Run → passes.** Add a `conftest` autouse fixture resetting `app.auth._jwks_cache = None` between tests.

- [ ] **Step 5: Commit**
```bash
git add -A && git commit -m "Plan C T2: OIDC resource-server auth (JWKS validation)"
```

---

### Task 3: DB model + Alembic migration (family_subscription)

**Files:**
- Create: `app/db.py`, `app/models.py`, `alembic.ini`, `migrations/env.py`, `migrations/versions/0001_family_subscription.py`, `tests/test_models.py`

**Interfaces:**
- Produces: `FamilySubscription` ORM model + `SubStatus` enum (`TRIAL/ACTIVE/PAST_DUE/PAUSED`), `SessionLocal`/`get_db`. Consumed by Tasks 6-8.

- [ ] **Step 1: Model + enum**

`app/models.py`:
```python
import enum
from datetime import datetime
from sqlalchemy import String, Integer, DateTime, Enum
from sqlalchemy.orm import Mapped, mapped_column, DeclarativeBase


class Base(DeclarativeBase):
    pass


class SubStatus(str, enum.Enum):
    TRIAL = "TRIAL"
    ACTIVE = "ACTIVE"
    PAST_DUE = "PAST_DUE"
    PAUSED = "PAUSED"


class FamilySubscription(Base):
    __tablename__ = "family_subscription"
    id: Mapped[int] = mapped_column(primary_key=True)
    owner_sub: Mapped[str] = mapped_column(String, unique=True, index=True)  # Authentik sub
    family_id: Mapped[str] = mapped_column(String)
    member_count: Mapped[int] = mapped_column(Integer, default=1)
    status: Mapped[SubStatus] = mapped_column(Enum(SubStatus), default=SubStatus.TRIAL)
    nextcloud_username: Mapped[str | None] = mapped_column(String, nullable=True)
    nextcloud_caldav_url: Mapped[str | None] = mapped_column(String, nullable=True)
    mollie_customer_id: Mapped[str | None] = mapped_column(String, nullable=True)
    mollie_subscription_id: Mapped[str | None] = mapped_column(String, nullable=True)
    trial_started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    trial_ends_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
```
No child PII — only a count and the owner sub.

- [ ] **Step 2: db.py (engine/session)** — `create_engine(settings.database_url)`, `SessionLocal`, `get_db()` FastAPI dependency yielding a session.

- [ ] **Step 3: Alembic** — init `migrations/`, point `env.py` at `Base.metadata` + `settings.database_url`, author `0001_family_subscription.py` creating the table (mirror the model columns; `owner_sub` unique index).

- [ ] **Step 4: Test with SQLite in-memory** — `tests/test_models.py`: create the tables via `Base.metadata.create_all` on an in-memory SQLite engine, insert a `FamilySubscription`, read it back, assert `status == TRIAL` default and `owner_sub` uniqueness raises on duplicate. Run `pytest -q tests/test_models.py` red→green.

- [ ] **Step 5: Commit**
```bash
git add -A && git commit -m "Plan C T3: family_subscription model + Alembic migration"
```

---

### Task 4: Nextcloud OCS provisioning client

**Files:**
- Create: `app/nextcloud.py`, `tests/test_nextcloud.py`

**Interfaces:**
- Produces: `NextcloudClient` with `create_family_user(owner_sub) -> ProvisionedUser(username, password, caldav_url)`, `disable_user(username)`, `enable_user(username)`. Uses OCS API with admin creds. Consumed by Task 6/8.

- [ ] **Step 1: Write failing test (respx-mocked OCS)**

`tests/test_nextcloud.py`: mock `POST {base}/ocs/v2.php/cloud/users` → 200 OCS success; assert `create_family_user` returns a username derived deterministically from `owner_sub` (idempotent), a generated password, and `caldav_url == f"{base}/remote.php/dav/"`. Mock the disable endpoint → assert `disable_user` PUTs to `.../users/{username}/disable`.

- [ ] **Step 2: Implement `app/nextcloud.py`**

Key points: OCS requires header `OCS-APIRequest: true` and `Accept: application/json`; auth = admin basic-auth; username = `fam-` + short hash of `owner_sub` (stable → idempotent); password = `secrets.token_urlsafe(24)`; on create, if OCS returns statuscode 102 (user exists) treat as idempotent success and (since we can't read back the password) rotate it via `PUT users/{id}` with a new password, returning that. CalDAV base = `{nextcloud_base_url}/remote.php/dav/`.

```python
import hashlib, secrets
from dataclasses import dataclass
import httpx
from app.config import get_settings

@dataclass
class ProvisionedUser:
    username: str
    password: str
    caldav_url: str

class NextcloudClient:
    def __init__(self, settings=None):
        self.s = settings or get_settings()
    def _auth(self): return (self.s.nextcloud_admin_user, self.s.nextcloud_admin_pass)
    def _headers(self): return {"OCS-APIRequest": "true", "Accept": "application/json"}
    def username_for(self, owner_sub: str) -> str:
        return "fam-" + hashlib.sha256(owner_sub.encode()).hexdigest()[:16]
    def create_family_user(self, owner_sub: str) -> ProvisionedUser:
        u = self.username_for(owner_sub); pw = secrets.token_urlsafe(24)
        r = httpx.post(f"{self.s.nextcloud_base_url}/ocs/v2.php/cloud/users",
            auth=self._auth(), headers=self._headers(),
            data={"userid": u, "password": pw}, timeout=30)
        code = r.json().get("ocs", {}).get("meta", {}).get("statuscode")
        if code == 102:  # already exists -> rotate password (idempotent)
            httpx.put(f"{self.s.nextcloud_base_url}/ocs/v2.php/cloud/users/{u}",
                auth=self._auth(), headers=self._headers(),
                data={"key": "password", "value": pw}, timeout=30)
        elif code != 100:
            raise RuntimeError(f"OCS create failed: {code}")
        return ProvisionedUser(u, pw, f"{self.s.nextcloud_base_url}/remote.php/dav/")
    def disable_user(self, username: str) -> None:
        httpx.put(f"{self.s.nextcloud_base_url}/ocs/v2.php/cloud/users/{username}/disable",
            auth=self._auth(), headers=self._headers(), timeout=30)
    def enable_user(self, username: str) -> None:
        httpx.put(f"{self.s.nextcloud_base_url}/ocs/v2.php/cloud/users/{username}/enable",
            auth=self._auth(), headers=self._headers(), timeout=30)
```

- [ ] **Step 3: Run red→green. Step 4: Commit** `Plan C T4: Nextcloud OCS provisioning client`

---

### Task 5: Mollie client (customer, mandate, per-member subscription)

**Files:**
- Create: `app/mollie.py`, `tests/test_mollie.py`

**Interfaces:**
- Produces: `MollieClient` with `create_customer(email) -> id`, `first_payment(customer_id, amount_cents, description, redirect_url) -> (payment_id, checkout_url)`, `create_subscription(customer_id, amount_cents, interval, description) -> id`, `update_subscription_amount(customer_id, sub_id, amount_cents)`, `cancel_subscription(customer_id, sub_id)`, `get_payment(payment_id) -> dict`. Consumed by Tasks 7-8.

- [ ] **Step 1: Failing tests (respx-mocked Mollie REST v2)** — assert amounts are formatted as `{"currency":"EUR","value":"4.00"}` for 4 members; first payment sets `sequenceType=first` (creates a mandate); subscription uses interval `"1 month"` (or `"12 months"` for annual) and amount = members × price (annual applies −20%). Use the provided Mollie **test key** placeholder via settings; respx intercepts so no real call is made.

- [ ] **Step 2: Implement `app/mollie.py`** — base `https://api.mollie.com/v2`, bearer `settings.mollie_api_key`; helper `cents_to_value(cents) -> f"{cents/100:.2f}"`. `first_payment` posts `/payments` with `sequenceType=first`, `customerId`, `amount`, `description`, `redirectUrl`, `webhookUrl=settings.mollie_webhook_url`; returns `id` + `_links.checkout.href`. `create_subscription` posts `/customers/{id}/subscriptions`. `update_subscription_amount` PATCHes it. All via httpx with type-hinted returns.

- [ ] **Step 3: red→green. Step 4: Commit** `Plan C T5: Mollie client (mandate + per-member subscription)`

---

### Task 6: Trial + status service (state machine) + provisioning on trial start

**Files:**
- Create: `app/service.py`, `tests/test_service.py`

**Interfaces:**
- Consumes: `FamilySubscription`, `NextcloudClient`, settings, a clock (inject `now: datetime` for testability — do NOT call datetime.now() inside pure logic).
- Produces:
  - `start_trial(db, owner, family_id, member_count, now, nc) -> FamilySubscription` (idempotent on owner_sub; provisions NC user; sets TRIAL, trial_ends_at = now + trial_days).
  - `status_view(sub, now) -> dict` = `{status, trial_days_left, member_count, caldav_url}` where trial_days_left = max(0, days until trial_ends_at) and a TRIAL past its end with no active subscription is reported as PAUSED.
  - `set_member_count(db, sub, n, mollie)` (updates count + Mollie amount if a subscription exists).
  - `pause(db, sub, nc)` / `activate(db, sub, nc)` (disable/enable NC user + set status).

- [ ] **Step 1: Failing unit tests** covering: start_trial is idempotent (2nd call returns same row, provisions once); status_view trial_days_left math with an injected `now`; a TRIAL whose `trial_ends_at < now` and no `mollie_subscription_id` → status_view reports `PAUSED`; set_member_count from 3→4 calls Mollie update with 400 cents. Mock NextcloudClient/MollieClient.

- [ ] **Step 2: Implement `app/service.py`** as pure-ish functions taking `now` and the clients as params (no hidden clocks). Keep each function small.

- [ ] **Step 3: red→green. Step 4: Commit** `Plan C T6: trial state machine + provisioning-on-start`

---

### Task 7: App-facing API endpoints (OIDC-protected)

**Files:**
- Create: `app/api.py`; Modify: `app/main.py` (include router); Create: `tests/test_api.py`

**Interfaces:**
- Produces (all require `require_owner`):
  - `POST /v1/sync/trial` body `{family_id, member_count}` → `status_view` (starts trial, provisions, returns caldav_url + generated app-password ONCE in this response).
  - `GET /v1/sync/status` → `status_view` (no password).
  - `POST /v1/sync/members` body `{member_count}` → updated `status_view`.
  - `POST /v1/billing/checkout` → `{checkout_url}` (Mollie first payment to create mandate; day-25 prompt target).

- [ ] **Step 1: Failing tests** with FastAPI `TestClient`, overriding `require_owner` via `app.dependency_overrides` to return a fixed `Owner`, and mocking NC/Mollie. Assert: trial returns 200 with `caldav_url` + `app_password`; status omits `app_password`; members updates the count; checkout returns a `checkout_url`; missing token → 401.

- [ ] **Step 2: Implement `app/api.py`** — an `APIRouter`; each endpoint resolves/creates the `FamilySubscription` for `owner.sub`, calls `app.service`, returns the view. The trial endpoint includes the freshly generated NC password (the ONLY time it is returned); status never returns it. Wire the router in `main.py`.

- [ ] **Step 3: red→green. Step 4: Commit** `Plan C T7: app-facing sync/billing API`

---

### Task 8: Mollie webhook + status transitions

**Files:**
- Create: `app/webhooks.py`; Modify: `app/main.py`; Create: `tests/test_webhooks.py`

**Interfaces:**
- Produces: `POST /webhooks/mollie` (body `id=<payment_or_subscription_id>`, Mollie posts form-encoded). Fetches the object from Mollie (never trusts the body), then: paid first payment → create subscription (amount = members × price) + status ACTIVE + enable NC user; failed/expired subscription payment → PAST_DUE then PAUSED (+ disable NC user); no valid object → 200 (ack, ignore). Webhook is unauthenticated (Mollie can't send bearer) but verifies by re-fetching from Mollie with the API key.

- [ ] **Step 1: Failing tests** — mock Mollie `get_payment` returning `status=paid` + a `customerId` matching a TRIAL row → assert row becomes ACTIVE, subscription created, NC user enabled. Mock a failed payment → row PAUSED + NC disabled. Unknown id → 200 no-op.

- [ ] **Step 2: Implement `app/webhooks.py`** per the interface. Always return 200 quickly (Mollie retries on non-200); do the work synchronously (single user, cheap).

- [ ] **Step 3: red→green. Step 4: Commit** `Plan C T8: Mollie webhook + ACTIVE/PAUSED transitions`

---

### Task 9: App-side — implement OscCloudProvider + wire trial start & soft-degrade

**Files (repo `/mnt/nvme1tb/projects/openschoolcloud-calendar`):**
- Modify: `android/app/build.gradle.kts` (BuildConfig `OSC_CLOUD_API_BASE`)
- Create: `android/.../data/remote/osccloud/OscCloudApi.kt`, `.../OscCloudSyncProvider.kt` (real impl), test
- Modify: `.../data/sync/OscCloudProvider.kt` (or replace its stub usage), `.../screens/family/SyncChoiceViewModel.kt` (OSC path calls the API), and a status check that degrades to LOCAL

**Interfaces:**
- Consumes: the Authentik access token (from `OidcTokenStore`/`AuthManager`, Plan A), the backend API (Tasks 6-8), `CredentialStorage` (store returned CalDAV app-password), `ProfileRepository.setSyncScope`.
- Produces: `OscCloudApi` (Retrofit/OkHttp or plain OkHttp) with `startTrial(familyId, memberCount)`, `status()`, `checkout()`; `OscCloudSyncProvider` wiring the response into the existing CalDAV account + `SyncScope.OSC_CLOUD`.

- [ ] **Step 1: BuildConfig** — add `buildConfigField("String","OSC_CLOUD_API_BASE","\"https://sync.openschoolcloud.nl\"")`.
- [ ] **Step 2: Failing test** for a small pure mapper (`OscCloudStatus` JSON → domain; `PAUSED`/expired → `shouldDegradeToLocal == true`). Use the existing test stack (JUnit + mockk).
- [ ] **Step 3: Implement** `OscCloudApi` (OkHttp, `Authorization: Bearer <access token>` from AuthManager) + `OscCloudSyncProvider`: on OSC choice → `startTrial` → store returned `caldav_url`+`app_password` via `CredentialStorage` as a Nextcloud account, set family profiles `syncScope=OSC_CLOUD`, kick the existing CalDAV sync. Add a status check (on app start / periodic WorkManager) that, when status is PAUSED/expired, flips profiles back to `SyncScope.LOCAL` (soft-degrade, no data deletion).
- [ ] **Step 4: Wire** `SyncChoiceViewModel.choose(OSC_CLOUD, ...)` to call `OscCloudSyncProvider.startTrial` instead of only setting prefs (behind `FeatureFlags.oscCloudEnabled`, still gated by `ENABLE_OSC_CLOUD`).
- [ ] **Step 5: Verify** `./gradlew :app:testDebugUnitTest :app:assembleDebug` (all green except the known pre-existing ICalParserTest failure). 
- [ ] **Step 6: Commit** `Plan C T9: app OscCloudProvider + trial start + soft-degrade`

---

### Task 10: Deploy config + runbook (docker-compose, env, Mollie live-key, go-live)

**Files (repo `osc-cloud-sync`):**
- Create: `docker-compose.yml` (service + Postgres, Traefik labels for `sync.openschoolcloud.nl`), `RUNBOOK.md`

- [ ] **Step 1: docker-compose** — `osc-cloud-sync` (uvicorn) + `postgres`; env from `.env`; Traefik labels routing `sync.openschoolcloud.nl` → :8000 (same Traefik as the existing orchestrator, EU/Cyso).
- [ ] **Step 2: RUNBOOK.md** covering:
  - DNS `sync.openschoolcloud.nl` → the Cyso host; TLS via Traefik.
  - Shared OSC Nextcloud: create the admin app-password for `NEXTCLOUD_ADMIN_*`; confirm OCS user-provisioning enabled; set a per-family quota policy.
  - Alembic migrate on deploy.
  - **Mollie:** put the **live** key in server env only (never in repo/chat); register the webhook URL; note the Revolut/Mollie webhook-slot caveat if relevant. Test with the Mollie **test key** first (`test_QkEj…D7x`) end-to-end.
  - Register the OSC-Cloud service as an OIDC **audience** in Authentik if needed (the app's token must be accepted; confirm `aud`/scope).
  - Go-live gate: flip the app's `ENABLE_OSC_CLOUD=true` (Plan A flag) only after: trial→provision→sync works with a test account, day-25 checkout works in Mollie test mode, and PAUSED soft-degrades the app to LOCAL.
  - Acceptance checks (curl the API with a real Authentik token; verify a provisioned NC user can CalDAV-sync; simulate a Mollie test payment through the webhook).
- [ ] **Step 3: Commit** `Plan C T10: deploy compose + go-live runbook`

---

## Self-Review

**1. Spec coverage (spec §7 verdienmodel + §7.3 naar C):**
- Betaalde gehoste gezins-sync → T4 (shared NC account per family) + T6/T7 (trial+provision). ✓
- €1/lid, jaar −20%, prijs volgt member_count → T5 (Mollie amount = members×price) + T6 set_member_count + config. ✓
- 30-dagen trial zonder kaart, prompt ~dag 25, zachte degradatie naar lokaal, niets verwijderd → T6 state machine + T7 checkout + T8 webhook + T9 soft-degrade (NC disable, not delete). ✓
- App-facing OIDC-beveiligde API → T2 (JWKS validation) + T7. ✓
- Contractgrens uit Plan A §7.3 (provisioned NC per klant, CalDAV+app-password terug, koppel aan oscSub, subscription quantity=members, webhook) → T4/T6/T7/T8. ✓
- Geen kind-PII in backend → T3 model (only member_count + owner_sub + NC username). ✓
- Soevereiniteit (Cyso/EU, hergebruik bestaande stack) → T1/T10. ✓
- Vult Plan A's `OscCloudProvider`-stub + `ENABLE_OSC_CLOUD`-flag → T9/T10. ✓
- Adresseert deels Plan A's gap (geen AccountEntity/oscSub): de backend keyt op de Authentik `sub`; T9 kan de owner-koppeling in de app afronden. (Volledige AccountEntity-persistentie blijft een app-follow-up.)

**2. Placeholder scan:** the Mollie key placeholder + `<...>`-style env values are intentional secrets-via-env (documented in Global Constraints), not gaps. Mollie/OCS exact field names (`sequenceType`, OCS statuscodes) are given; T10 flags live-verification. No "TBD".

**3. Type consistency:** `Owner(sub,email)` (T2) used by T7; `FamilySubscription`/`SubStatus` (T3) used by T6/T7/T8; `NextcloudClient.create_family_user/disable/enable` (T4) used by T6/T8; `MollieClient` methods (T5) used by T7/T8; `status_view` dict shape (T6) returned by T7 and consumed by T9's mapper. Pricing: `price_per_member_cents=100` consistent across T5/T6/config.

**Known execution boundary (flagged, not assumed):** live Nextcloud OCS behavior, real Mollie flows, and Authentik JWKS/audience acceptance are verified on the VPS per T10; here everything is unit/integration-tested with `respx`/SQLite/TestClient. The shared-NC per-family-**user** model (chosen for €1/member economics) means all of a family's members share one Nextcloud account/calendar home — per-member *identities* are a client-side profile concept (Plan A), not separate NC users; if true per-member NC isolation is later required, that is a re-scope.
