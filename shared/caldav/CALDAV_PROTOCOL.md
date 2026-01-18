# CalDAV Protocol Reference

Quick reference for implementing CalDAV client for Nextcloud.

## Relevant RFCs

- **RFC 4791** — CalDAV (Calendaring Extensions to WebDAV)
- **RFC 5545** — iCalendar (Data Format)
- **RFC 6638** — CalDAV Scheduling (iTIP over CalDAV)
- **RFC 6764** — Locating Services (SRV records, well-known)

---

## Discovery Flow

### Step 1: Find CalDAV endpoint

Try well-known first:
```
PROPFIND /.well-known/caldav HTTP/1.1
```

Fallback for Nextcloud:
```
PROPFIND /remote.php/dav HTTP/1.1
```

### Step 2: Find current user principal

```xml
PROPFIND /remote.php/dav HTTP/1.1
Depth: 0

<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
    <d:prop>
        <d:current-user-principal/>
    </d:prop>
</d:propfind>
```

Response contains:
```xml
<d:current-user-principal>
    <d:href>/remote.php/dav/principals/users/username/</d:href>
</d:current-user-principal>
```

### Step 3: Find calendar home set

```xml
PROPFIND /remote.php/dav/principals/users/username/ HTTP/1.1
Depth: 0

<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
    <d:prop>
        <cal:calendar-home-set/>
    </d:prop>
</d:propfind>
```

Response:
```xml
<cal:calendar-home-set>
    <d:href>/remote.php/dav/calendars/username/</d:href>
</cal:calendar-home-set>
```

### Step 4: List calendars

```xml
PROPFIND /remote.php/dav/calendars/username/ HTTP/1.1
Depth: 1

<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" 
            xmlns:cs="http://calendarserver.org/ns/">
    <d:prop>
        <d:resourcetype/>
        <d:displayname/>
        <cal:calendar-color/>
        <cs:getctag/>
        <d:sync-token/>
        <cal:supported-calendar-component-set/>
        <d:current-user-privilege-set/>
    </d:prop>
</d:propfind>
```

---

## Sync Strategy

### CTag-based sync

1. Store `ctag` per calendar
2. On sync: `PROPFIND` calendar for current `ctag`
3. If different: run sync-collection report
4. Update local `ctag`

### Sync-collection report

```xml
REPORT /remote.php/dav/calendars/username/personal/ HTTP/1.1

<?xml version="1.0" encoding="utf-8"?>
<d:sync-collection xmlns:d="DAV:">
    <d:sync-token>previous-sync-token-or-empty</d:sync-token>
    <d:sync-level>1</d:sync-level>
    <d:prop>
        <d:getetag/>
    </d:prop>
</d:sync-collection>
```

Response includes:
- New/modified event URLs with ETags
- Deleted event URLs (404 status)
- New sync-token for next sync

---

## Event Operations

### GET event

```
GET /remote.php/dav/calendars/username/personal/event-uid.ics HTTP/1.1
```

Returns iCalendar data.

### Create event

```
PUT /remote.php/dav/calendars/username/personal/new-event-uid.ics HTTP/1.1
Content-Type: text/calendar
If-None-Match: *

BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//OpenSchoolCloud//Calendar//EN
BEGIN:VEVENT
UID:new-event-uid
DTSTART:20250115T090000Z
DTEND:20250115T100000Z
SUMMARY:Team meeting
END:VEVENT
END:VCALENDAR
```

### Update event

```
PUT /remote.php/dav/calendars/username/personal/event-uid.ics HTTP/1.1
Content-Type: text/calendar
If-Match: "previous-etag"

[iCalendar data]
```

Use `If-Match` with ETag for optimistic locking.

### Delete event

```
DELETE /remote.php/dav/calendars/username/personal/event-uid.ics HTTP/1.1
If-Match: "current-etag"
```

---

## Scheduling (Invites)

CalDAV Scheduling (RFC 6638) uses the Outbox for sending invites.

### Send invite

```
POST /remote.php/dav/calendars/username/outbox/ HTTP/1.1
Content-Type: text/calendar

BEGIN:VCALENDAR
VERSION:2.0
METHOD:REQUEST
BEGIN:VEVENT
UID:meeting-123
ORGANIZER:mailto:organizer@school.nl
ATTENDEE;PARTSTAT=NEEDS-ACTION:mailto:attendee@school.nl
DTSTART:20250115T090000Z
SUMMARY:Team meeting
END:VEVENT
END:VCALENDAR
```

Nextcloud handles delivery to attendees.

---

## iCalendar Quick Reference

### Minimal event

```
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//OpenSchoolCloud//Calendar//EN
BEGIN:VEVENT
UID:unique-id-here
DTSTAMP:20250115T120000Z
DTSTART:20250115T090000Z
DTEND:20250115T100000Z
SUMMARY:Event title
END:VEVENT
END:VCALENDAR
```

### All-day event

Use `VALUE=DATE` instead of datetime:

```
DTSTART;VALUE=DATE:20250115
DTEND;VALUE=DATE:20250116
```

Note: end date is exclusive (next day for single-day event).

### Recurring event

```
RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20250630
```

### Attendees

```
ORGANIZER;CN="Teacher Name":mailto:teacher@school.nl
ATTENDEE;PARTSTAT=NEEDS-ACTION;CN="Parent":mailto:parent@example.com
ATTENDEE;PARTSTAT=ACCEPTED;CN="Co-teacher":mailto:co@school.nl
```

PARTSTAT values:
- `NEEDS-ACTION` — No response yet
- `ACCEPTED` — Accepted
- `DECLINED` — Declined  
- `TENTATIVE` — Maybe

### Reminders (VALARM)

```
BEGIN:VALARM
ACTION:DISPLAY
TRIGGER:-PT15M
DESCRIPTION:Event reminder
END:VALARM
```

`-PT15M` = 15 minutes before start.

---

## Nextcloud Specifics

### Namespaces

```xml
xmlns:d="DAV:"
xmlns:cal="urn:ietf:params:xml:ns:caldav"
xmlns:cs="http://calendarserver.org/ns/"
xmlns:oc="http://owncloud.org/ns"
xmlns:nc="http://nextcloud.org/ns"
```

### Calendar color

Nextcloud uses `calendar-color` in CalendarServer namespace:

```xml
<cal:calendar-color xmlns:cal="http://apple.com/ns/ical/">#0082c9</cal:calendar-color>
```

Or Apple namespace. Check both.

### Read-only calendars

Check `current-user-privilege-set` for write access:

```xml
<d:current-user-privilege-set>
    <d:privilege><d:read/></d:privilege>
    <d:privilege><d:write/></d:privilege>
</d:current-user-privilege-set>
```

No `<d:write/>` = read-only.

---

## Authentication

### Basic Auth

```
Authorization: Basic base64(username:password)
```

### App Passwords

Recommended for security. User creates in Nextcloud Settings → Security.

Same as Basic Auth, but with app-password instead of main password.

---

## Common Errors

| HTTP Code | Meaning |
|-----------|---------|
| 401 | Bad credentials |
| 403 | No permission |
| 404 | Not found |
| 409 | Conflict (ETag mismatch) |
| 412 | Precondition failed (If-Match failed) |
| 507 | Insufficient storage |

---

## Testing

### Test servers

- Demo Nextcloud: Use Nextcloud's demo servers
- Local: `docker run -p 8080:80 nextcloud`

### curl examples

```bash
# Discovery
curl -u user:pass -X PROPFIND \
  -H "Depth: 0" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>' \
  https://cloud.example.com/remote.php/dav

# Get event
curl -u user:pass \
  https://cloud.example.com/remote.php/dav/calendars/user/personal/event.ics
```

---

## Libraries

### Android (Kotlin/Java)
- **ical4j** — iCalendar parsing/generation
- **OkHttp** — HTTP client

### iOS (Swift)
- **Foundation** — URLSession for HTTP
- Custom iCal parser or port of ical4j

### Both
- Consider generating shared CalDAV parsing code
