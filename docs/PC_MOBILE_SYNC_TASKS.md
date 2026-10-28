# PC / Mobile Sync Tasks

- [x] Snapshot protocol v3 on Java and Go with bounded binary codecs.
- [x] Android Room v10 migration with per-record update times and durable deletion tombstones.
- [x] Encrypted bidirectional transfer: phone uploads, PC merges, phone receives and applies the result.
- [x] Desktop CRUD for sleep, meals, water, agenda and private records.
- [x] Last-write-wins record merge, deletion propagation and active-session completion handling.
- [x] Reusable pairing code for the lifetime of the PC app; Android remembers the last successful address and code.
- [x] Java/Go codec, merge, tombstone and encrypted server round-trip tests.
- [x] Android unit tests, linted optimized build, emulator launch and Macrobenchmark run.
- [x] Windows production build and responsive desktop UI browser verification.
- [x] Agenda parity: date navigation, not-done/completed states, postpone, two-way drag/drop, smart text import, task templates, favorites and category management.
- [x] Snapshot protocol v5 synchronizes agenda catalogs, hidden suggestions and completion streak dates in both directions.
- [x] PC generates a selectable-address pairing QR; Android scans it offline with CameraX and bundled ML Kit, then starts sync automatically.
- [ ] Long-lived cryptographic device pairing that survives a PC restart.
- [ ] Automatic/background discovery and synchronization without pressing the sync button.
- [ ] Causal conflict UI for concurrent offline edits; live v3 currently resolves by update time.
- [ ] Physical Samsung-to-PC end-to-end run; only the Android emulator was connected for this release.

## Release

- Android: `releases/GunlukTakip-5.1-Android.apk`
- Windows: `releases/GunlukTakipPC-2.1-Windows.exe`

The applications work independently without a server or internet connection. Synchronization is user-initiated over the local network and transfers changes in both directions.
