# Visual upgrade — changed files and icon notes

## What was NOT touched

No change was made to the Room schema, entities, DAOs, migrations (`MIGRATION_1_2`,
`MIGRATION_2_3`), database version (still 3), invoice-number rules, clinic
relationships, reminder scheduling, WhatsApp behaviour, report maths, search
queries or status calculations. `data/Database.kt` and `reminders/Reminders.kt` are
byte-for-byte identical to the previous release.

The status colour rules were **moved**, not changed. `collectionState()` and
`stateLabel()` were private helpers inside `MainActivity.kt`; they are now public
functions in `BusinessLogic.kt` so that unit tests can lock the rules down:

| Colour | Rule |
|---|---|
| Green | paid or completed, or on track |
| Orange | the collection date has arrived or passed, while the due date has not |
| Red | the due date has passed and the invoice is unpaid |

Five new tests assert exactly this, including that a manual Paid override always wins.

## Changed files

### Modified
| File | Change |
|---|---|
| `app/build.gradle.kts` | added `androidx.core:core-splashscreen:1.0.1` |
| `app/src/main/AndroidManifest.xml` | launcher activity now starts in `Theme.ClinicCollections.Splash` |
| `app/src/main/java/.../MainActivity.kt` | rewritten: splash handoff, bottom navigation, overlay back stack |
| `app/src/main/java/.../BusinessLogic.kt` | appended `collectionState`, `stateLabel`, `formatDate`, `formatMonth`, `normalizeSearchQuery`, `aed`, `dashboardCounts` |
| `app/src/test/java/.../BusinessLogicTest.kt` | 9 → 19 tests |
| `app/src/main/res/values/colors.xml` | design-system palette |
| `app/src/main/res/values/themes.xml` | app theme plus splash theme |
| `app/src/main/res/values/strings.xml` | added `app_tagline` |

### Added — UI package
`ui/Theme.kt`, `ui/Components.kt`, `ui/HomeScreen.kt`, `ui/ClinicsScreen.kt`,
`ui/ClinicDetailScreen.kt`, `ui/InvoiceFormScreen.kt`, `ui/InvoiceDetailScreen.kt`,
`ui/ReportsScreen.kt`, `ui/AboutScreen.kt`, `ui/BrandSplash.kt`.

The previous single 506-line `MainActivity.kt` is now a 216-line navigation host
plus ten focused files.

### Added — icon resources
```
res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
    ic_launcher.png            48/72/96/144/192 px, rounded square, legacy
    ic_launcher_round.png      circular, legacy round launchers
    ic_launcher_foreground.png 108/162/216/324/432 px, adaptive foreground
    ic_launcher_monochrome.png white silhouette, Android 13 themed icons
res/mipmap-anydpi-v26/
    ic_launcher.xml            adaptive-icon: background + foreground + monochrome
    ic_launcher_round.xml      same
res/drawable-{mdpi..xxxhdpi}/ic_notification.png   24dp white status-bar silhouette
res/drawable-nodpi/ic_splash_logo.png              transparent symbol for splash and headers
```

### Removed
`res/drawable/ic_launcher_foreground.xml` and `res/drawable/ic_notification.xml`
(the old placeholder vectors, replaced by artwork derived from the supplied PNG).

## How the launcher icon was built

1. The emerald plate was measured from the 1254×1254 source: **#01351F**. That exact
   value is now `@color/ic_launcher_background` and `@color/splash_background`.
2. The gold cross and ivory receipt were separated from the plate with a luminance
   ramp, then **un-matted** — for every partially transparent edge pixel the emerald
   contribution is subtracted, so the symbol carries no dark green fringe when it is
   composited over a different background.
3. The symbol was trimmed to its bounding box and scaled **without distortion**
   (aspect ratio preserved) onto each canvas.
4. On the 108dp adaptive canvas the symbol's longest side is 60dp. Measured result:
   the furthest content pixel sits 126.5px from centre against a 132px radius for
   the 66dp key-content circle, so **nothing is clipped** by a circle, squircle,
   rounded square or square mask.
5. `<monochrome>` uses the same geometry as a flat white silhouette, which is what
   Android 13+ themed icons expect.

The legacy PNGs exist for launchers older than API 26; on Android 10 and up the
adaptive icon in `mipmap-anydpi-v26` is what actually gets used.

## Splash screen

`androidx.core:core-splashscreen` renders the system splash natively on Android 12+
and reproduces it on Android 10 and 11. `installSplashScreen()` is called before
`super.onCreate()`, and the theme hands over to `Theme.ClinicCollections`
immediately — there is no artificial delay in the platform splash.

The platform splash cannot draw text, so the app name and the "Smart Payment
Tracking" tagline are shown by a Compose screen (`ui/BrandSplash.kt`) that lasts
**600 ms** and cross-fades into the app. That is the only deliberate wait anywhere
in startup; if you would rather have none at all, delete the `delay(600)` line and
the app opens straight onto Home.

## Navigation

Bottom navigation with Home, Clinics, Reports and About. Add / Edit invoice, invoice
details and clinic details are pushed as overlays and hide the bottom bar so they
keep full height. Physical back pops the overlay stack, then returns to Home, then
lets the system close the app.
