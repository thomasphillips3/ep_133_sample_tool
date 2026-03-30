# Deferred Items — Phase 02 Plan 02

## Pre-existing Lint Errors (Out of Scope)

### MutableImplicitPendingIntent in MIDIManager.kt:157
- **File:** `AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIManager.kt:157`
- **Issue:** `PendingIntent.getBroadcast()` uses `FLAG_MUTABLE` for USB permission requests. Android Lint flags this as `MutableImplicitPendingIntent`.
- **Context:** Pre-existing before this plan. The intent uses `Intent(ACTION_USB_PERMISSION)` which is an internal action, and `FLAG_MUTABLE` is required because `UsbManager.requestPermission()` needs to modify the PendingIntent. The fix is to make the Intent explicit (add package/component) or to create a lint baseline.
- **Recommended fix:** Add package to the Intent: `Intent(ACTION_USB_PERMISSION).apply { setPackage(context.packageName) }` and change to `FLAG_IMMUTABLE`.
- **Discovered during:** Task 3-02 lint run
- **Priority:** Medium — does not affect current functionality (API 29+) but will cause issues on future Android versions

## Notes
- 26 lint warnings also present (style/deprecation warnings) — all pre-existing
- These items are tracked here per the deviation rules scope boundary
