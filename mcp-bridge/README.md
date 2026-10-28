# Gunluk Takip MCP Bridge v1

Local stdio MCP server using the official `modelcontextprotocol/go-sdk v1.8.0`.
It calls the Android app's repository API through a caller-checked ContentProvider
over an explicitly selected ADB device. No public HTTP endpoint, desktop GUI,
root, cloud backend or LLM API key is required.

## Build

Requires Go 1.25+ and Android SDK platform-tools. The existing Wails module is
independent; this module does not upgrade its dependencies.

```powershell
go test ./...
go build -o gunluk-takip-mcp.exe .
```

## Pair

1. Install Android version 5.8 or newer without clearing application data.
2. Open Settings > Developer > AI / MCP access on the phone.
3. Enable MCP. Enable record changes if needed. Private access and irreversible
   deletion are separate, disabled-by-default permissions.
4. Select PC pairing to open a single-use, 60-second pairing window.
5. Run the bridge with `--pair --device` and the phone's hardware serial.

For the emulator-only profile verified on this workstation:

```powershell
.\gunluk-takip-mcp.exe --pair --device emulator-5554 --config "$env:APPDATA\GunlukTakipMcp\emulator.json"
```

The command saves the key in the selected private profile, without printing it.
Without `--config`, the default is `%APPDATA%\GunlukTakipMcp\config.json` on Windows.
Credential files get a user/System-only ACL on Windows. No token belongs in
Git, MCP client config, screenshots, command-line flags or logs.

Use `--config` for a separate profile. `--adb` accepts an absolute platform-tools
path. An exact ADB serial also works; a hardware serial is preferable for
wireless devices because their IP/port can change. Rediscovery only reconnects
an mDNS service matching the configured hardware serial, never an arbitrary phone.

To pair AND register with Codex in one step, open the pairing window, then run:

```powershell
.\Install-Codex.ps1 -Device emulator-5554 -BridgeConfig "$env:APPDATA\GunlukTakipMcp\emulator.json"
```

The helper backs up the existing Codex config before adding the server. It does
not overwrite an existing server with the same name pointing to another executable.

Disabling MCP rejects subsequent calls. Rotating the key invalidates all clients
using the previous key. V1 uses one shared app key and app-wide scopes, not
independent per-client keys. Already accepted transactions may complete when
access is revoked; revocation is not rollback.

## MCP client

Run the executable as a stdio MCP server, not as an HTTP URL. For Codex:

```powershell
codex mcp add gunluk_takip -- 'C:\path\to\repo\mcp-bridge\gunluk-takip-mcp.exe' --config "$env:APPDATA\GunlukTakipMcp\emulator.json"
```

`client-config.example.json` is the equivalent configuration for clients using
the `mcpServers` JSON format. A running chat may need a new session/app restart
to discover a newly configured MCP. A cloud-only client cannot directly launch
a local executable without a local connector.

## Tools and guarantees

`contract.json` is the canonical tool/schema definition. Gradle copies this one
file into Android assets; Go embeds the same file. There are 17 tools covering:

- App health, permissions and device-local timezone.
- Agenda list/search/history, tasks, notes, goals, batch creation, status,
  recurring counts, postponement, subtasks, timers and merge.
- Sleep history, active sleep, creation, correction, start and finish.
- Ideas and idea completion into the agenda.
- Short/long-term shopping and purchased state.
- Task templates, favorites and categories.
- Read-only sleep settings and calculated statistics.
- Private tracking behind a separate permission.
- Preview and explicitly permissioned deletion of one record. There is no wipe tool.

Every mutation requires a `requestId`. Retry an interrupted operation using the
EXACT same ID and arguments. The result and database changes are committed in
one Room transaction. Reusing the ID for different arguments is rejected.
Do not generate a new ID merely because an ADB response was lost.

Reads return content-hash `revision` values. Existing-record changes require
`expectedRevision`; merge additionally requires `targetRevision`. A mismatch
returns `REVISION_CONFLICT`. Refresh and review before changing the request.
An idempotent retry returns the original committed result, which is not a claim
that the record has remained unchanged since then; read again for current state.

Times are epoch milliseconds. Days/ranges are device-local ISO dates. Reads
return `nextOffset` for pagination. Requests are bounded to 16 KB INCLUDING the
envelope/key; use batches smaller than that limit. Results are bounded and
records are never silently truncated. JSON is base64-framed for ADB transport;
base64 is NOT encryption. ADB pairing is the underlying transport trust boundary.

MCP annotations describe behavior but do not authorize it. The Android provider
requires Android's platform DUMP permission at provider acquisition and checks
caller UID, key and current scope on every operation. The app does not request
that privileged permission; the authorized ADB shell already holds it. Direct SQL, general
shell execution, arbitrary file access and exporting credentials are not tools.
All devices already authorized for ADB are part of the device trust boundary.

## V1 boundaries

- Settings are read-only. Changing goals/reminder schedules, bulk backup/import,
  per-client grants, undo, an audit UI and ADB-free transport are not implemented.
- Agenda create does not schedule a new reminder. Existing reminder metadata is
  preserved; merge/delete cancel the source reminder after the DB transaction.
- Catalog list includes visible templates and categories; a separate historical
  suggestion-list tool is not yet exposed.
- Single-record deletion is irreversible; preview explicitly reports this.
- If an alarm cancellation fails after a committed mutation, retrying the same
  request retries the safe cancellation without duplicating the mutation.
- No claim is made for direct-boot/locked profiles or every OEM's force-stop
  behavior. Process restart and a background-provider call are tested separately.
- Nutrition/hydration remain legacy data, not active v1 MCP tools.
- The bridge reads the actual phone, not the older PC snapshot, so ideas,
  shopping and task time do not depend on PC sync protocol coverage.

## Validation

Unit tests exercise SDK initialization/discovery/schema rejection, response
framing, Unicode/quoting, oversized requests and explicit device selection.

The integration suite REFUSES physical devices and writes only synthetic records:

```powershell
$env:GUNLUK_MCP_TEST_DEVICE = 'emulator-5554'
$env:GUNLUK_MCP_TEST_CONFIG = Join-Path $env:TEMP 'GunlukTakipMcp-emulator\config.json'
go test -tags=integration -run TestEmulatorRoundTrip -count=1 -v .
go build -o gunluk-takip-mcp.exe .
go test -tags=integration -run TestEmulatorStdioSmoke -count=1 -v .
```

`TestEmulatorRoundTrip` requires the DEBUG APK for emulator-only preference setup.
`TestEmulatorStdioSmoke` also works against the optimized APK with that same
emulator configuration. The UI-marker mode is opt-in and emulator-only.

Verified on 2026-10-24 with the API 37 emulator and Android 5.8 / schema 21:

- 34 Android unit tests; 6 Go unit tests; `go vet` passed.
- Official-SDK initialization, 17-tool discovery and rejected invalid schemas.
- Real ADB/Room writes, duplicate retry after app restart, conflicting request IDs,
  stale revisions, invalid batch rollback, timers, idea completion and shopping.
- The built stdio executable worked with both debug and optimized APKs.
- A record created through MCP appeared in Agenda; synthetic records were cleaned.
- Disabled writes, disabled deletion, disabled MCP, invalid keys and private access
  were rejected as expected.
- MCP reads worked with the launcher in front and with the emulator display asleep;
  the calls did not turn the display on.
- Pairing consumed its one-use window, protected the credential ACL and rejected a
  second attempt. The installer backed up/preserved an isolated Codex config;
  repeating installation preserved the existing server entry.

Only the emulator was used for this release. API 26, a physical Samsung, real
Claude/Cursor UI sessions, and performance/power benchmarks remain unverified.
Installer checks first used a temporary `CODEX_HOME` and emulator-only credentials.
After those passed, `gunluk_takip` was registered in the normal Codex profile with
the emulator-only credential profile. Other effective settings were compared
against the backup and preserved (the CLI normalized an omitted empty args list).
No physical-device credentials or installations were added for this release.
The new tools will load in a subsequent Codex session.

## Upstream

The official SDK is reused as a pinned module, rather than reimplementing MCP.
See `third_party/go-sdk-LICENSE` for its full license/transition notice:
https://github.com/modelcontextprotocol/go-sdk/tree/v1.8.0

Other researched Android projects were not copied into this application.
Their debug-only, platform or licensing limitations are documented in
`../docs/MCP_RESEARCH_AND_PLAN.md`.
