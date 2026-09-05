# Maduka Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Maduka rent-management Android app (Java, Firebase Realtime Database + Auth) exactly matching the approved design canvas `Maduka.dc.html` — three roles (Super Admin, Admin, Tenant), a payment record→confirm/reject workflow, Yupo/Hayupo presence tracking, admin notifications, and Kiswahili/English copy — on top of the currently-empty `com.example.maduka` Android Studio scaffold at `D:\AndroidStudio\Maduka`.

**Architecture:** Single-module Android app (Java) using Firebase Realtime Database for data, Firebase Auth for credentials, a thin repository layer per entity, Fragments behind one role-aware `MainActivity` + `BottomNavigationView`, and a plain-Java `AuthRepository` that resolves the login screen's free-text identifier (username/email/phone) to a Firebase Auth email via a public `login_index` node before calling `signInWithEmailAndPassword`. A second Gradle module (`cli/`) holds a console tool that uses the Firebase Admin SDK to register the first super admin, admins, and tenants (no in-app self-registration for tenants/admins other than the Super Admin's in-app forms).

**Tech Stack:** Java 11, AndroidX AppCompat/Material 1.14, Firebase BOM (Auth + Realtime Database + Messaging), AndroidX WorkManager is *not* used for the three-times-daily overdue check (see Task 18 rationale) — `AlarmManager.setExactAndAllowWhileIdle` is used instead. No chart library dependency — two small custom `View`s (`BarChartView`, `DonutView`) draw the Reports screen with `Canvas`. No external icon library — Material Symbols vector drawables substitute for the canvas's Phosphor icons (closest available equivalent; documented per-icon below).

**Testing approach (read before executing):** This project has no Android emulator or instrumented-test runner available to the implementer, and Robolectric does not yet reliably support the project's bleeding-edge `compileSdk 36` / AGP 9.2.1. So: (1) every class with real branching logic and **no Android framework dependency** (date math, status derivation, login-key sanitizing, verse rotation, rejection-reason mapping) gets a real JUnit4 test in `app/src/test/java/...` run via `./gradlew testDebugUnitTest` — full red/green TDD, per the steps below. (2) Classes that must touch Android framework or the Firebase SDK (Activities, Fragments, Adapters, `FirebaseManager`) are **not** unit-tested; instead each of those tasks ends with `./gradlew :app:assembleDebug` (or `:app:compileDebugJavaWithJavac` for a faster check) as the verification step, plus a manual diff of the screen's widgets/copy against the "Layout spec" bullets, which were captured verbatim from the live design canvas. Do not invent instrumented tests that can't actually run in this environment.

## Global Constraints

- **Package / applicationId:** `com.maduka.rentmanager` (must match `app/google-services.json`'s `package_name`, currently mismatched against the scaffold's `com.example.maduka` — Task 1 renames it). Firebase project: `rent-manager-116d8`.
- **Design system is dark-only** — there is no light theme and no theme toggle in the canvas. Do not add one.
- **Exact color tokens** (from the canvas's computed CSS custom properties — use verbatim, do not reinterpret):
  - Background `#161826`, Surface `#232532`, Text `#e9e9ed`, Divider `#e9e9ed` @ 16% alpha.
  - Accent (brand purple) `#9184d9`, Accent-2 `#a7a1db`.
  - Accent scale: 100 `#f5f4ff`, 200 `#e7e5fe`, 300 `#d2cefd`, 400 `#b5abfc`, 500 `#968ae0`, 600 `#796cbf`, 700 `#5d5294`, 800 `#423a6a`, 900 `#2b2741`.
  - Neutral scale: 100 `#f3f5fe`, 200 `#e4e7f5`, 300 `#cfd3e5`, 400 `#b2b6ca`, 500 `#9397ab`, 600 `#75798c`, 700 `#595d6c`, 800 `#3f424d`, 900 `#292b31`.
  - Status "good / Confirmed / Yupo": fg `#6ee7b7`, bg `#20302a`, border `#2f6b52`.
  - Status "waiting / Pending / Inangoja": fg `#fbd77a`, bg `#3a3222`, border `#7a5c14` (accent line `#fbbf24`).
  - Status "bad / Rejected / Hayupo / Overdue": fg `#fca5a5`, bg `#31232a`, border `#7f3a3a` (accent line `#f87171`).
  - Status "Empty": fg/border `#9397ab` (neutral-500), transparent background, outline pill only (no fill) — do not reuse the bad/good/waiting fill styles for Empty.
  - Radius: sm 4dp, md 8dp, lg 14dp. Spacing scale (dp): 3, 6, 8, 11, 17, 22 (i.e. `space-1..8` at 2.8px≈1dp increments — round to whole dp: 3/6/8/11/17/22).
  - Font: Inter (400/500/600/700). Bundle static `.ttf` files under `res/font/` (download once from the `google/fonts` OFL repo during Task 2; do not use downloadable-fonts-via-Play-Services, this app must work offline-first).
- **Brand mark assets (added after Task 1 shipped):** the user supplied real logo SVGs at `maduka-logo-svg/` (project root) — a steel-doorway + warm-keyhole mark. Colors: steel accent `#5980a6`, deep steel `#1d2d3d`, warm ochre `#da9258` (distinct from the UI's purple accent above — the app icon/brand mark intentionally uses its own palette, separate from in-app chrome, same as most real products). Already converted to Android resources: `app/src/main/res/drawable/ic_launcher_background.xml` (solid `#1d2d3d`) + `ic_launcher_foreground.xml` (the mark, scaled/centered per adaptive-icon safe-zone convention) replace the default Android Studio launcher icon — done, do not redo. `app/src/main/res/drawable/ic_maduka_mark.xml` (mono-white version, 48x48 viewport) is available for any future task needing the mark in-app (e.g. Task 7's login header) — use it instead of inventing a generic icon. `maduka-logo-svg/maduka-mark-color.svg` and `maduka-mark-mono-dark.svg` remain as source if a task needs a color or light-ground variant not yet converted.
- **Shop naming:** exactly `A1`–`A10`. `A7` and `A10` are the two vacant (Empty) shops in seed/demo data.
- **Legend / status vocabulary is shared across payments and tenant presence** — same 4-color system means two different things depending on context: green = Confirmed (payment) or Yupo (tenant present); amber = Pending (payment) or Inangoja (awaiting presence confirmation); red = Rejected (payment) or Hayupo (tenant gone); gray = Empty (shop only, never a payment state).
- **Two-person rule:** a payment's `recordedByUid` may never equal the confirming/rejecting user's uid — an admin cannot confirm/reject their own recorded payment. Enforce in both UI (hide Confirm/Reject if `recordedByUid == currentUid`) and Realtime Database rules.
- **Optimistic apply, rollback on reject:** recording a payment immediately updates the tenant's `lastPaymentDate`/`dueDate`/`monthsCovered` (status `pending`); if a second admin rejects it, those three fields roll back to the snapshot taken at record-time (`PaymentRecord.previousLastPaymentDate/previousDueDate/previousMonthsCovered`).
- **Due date math:** `dueDate = lastPaymentDate + monthsCovered` **calendar months** (`Calendar.add(Calendar.MONTH, n)`), never a fixed 30-day block. Verified against canvas sample: Asha M. paid 2 months on 02/09/2026 → due 02/11/2026.
- **Login:** role is chosen first via three tabs (Super Admin / Admin / Tenant) on one login screen, then a single free-text field accepts username, email, or phone, plus password and a "Remember me" checkbox. "Forgot password? Contact admin" is plain text (no action — matches "no self-reset" policy). No self-registration anywhere; only the Super Admin (in-app, Users/Tenants screens) or the `cli` tool register accounts.
- **KJV verse:** shown only on the Super Admin/Admin login tabs and above the Admin/Super Admin dashboard, never on the Tenant side. Rotates each sign-in from a small fixed pool of accurately-quoted King James Version verses about faithfulness (public domain text — quote exactly, see Task 4).
- **Notifications:** Admin/Super Admin get `overdue`, `due_today`, `due_soon`, `payment_confirmed`(as "Payment recorded"), and `system` notifications. Tenants get `due_soon`/`due_today`, `payment_confirmed`, and `receipt_available`. Overdue reminders fire 3×/day at 08:00, 14:00, 18:00 device-local time.
- **Bottom navigation, 5 tabs per role:**
  - Super Admin: Dashboard, Properties, Tenants, Reports, Users.
  - Admin: Dashboard, Properties, Tenants, Reports, Notifications.
  - Tenant: Dashboard, Payments, Details, History, Notifications.
- **Kiswahili terms already fixed by the canvas — reuse verbatim, do not retranslate:** Yupo, Hayupo, Inangoja, Imethibitishwa (Confirmed, used in older doc — canvas itself just says "Confirmed"/"Rejected" in English UI chrome with Yupo/Hayupo/Inangoja as the Swahili status words). Full string translation happens in Task 19 against the exact English strings captured in Task 8–17's Layout specs.
- **No Cloud Functions / server component exists or is in scope.** Push notifications while the app is fully killed are therefore a known limitation (Task 18 documents this rather than silently overpromising); in-foreground/backgrounded delivery via a Firebase listener + `AlarmManager` is what ships.

---

## File Structure

```
D:\AndroidStudio\Maduka\
  settings.gradle.kts                      # add ':cli' module (Task 21)
  gradle\libs.versions.toml                # add Firebase BOM, RecyclerView, SwipeRefresh, google-services plugin
  app\build.gradle.kts                     # applicationId rename, new deps, google-services plugin apply
  app\google-services.json                 # already present, package_name com.maduka.rentmanager
  app\src\main\AndroidManifest.xml         # permissions, MadukaApp, LoginActivity/MainActivity, notification receiver
  app\src\main\java\com\maduka\rentmanager\
    MadukaApp.java
    data\model\UserRole.java
    data\model\PaymentStatus.java
    data\model\PresenceStatus.java
    data\model\RejectionReason.java
    data\model\Shop.java
    data\model\Tenant.java
    data\model\AdminUser.java
    data\model\SuperAdminUser.java
    data\model\PaymentRecord.java
    data\model\AppNotification.java
    data\FirebaseSchema.java
    data\FirebaseManager.java
    data\AuthRepository.java
    data\ShopRepository.java
    data\TenantRepository.java
    data\UserRepository.java
    data\PaymentRepository.java
    data\NotificationRepository.java
    util\DateCalculator.java
    util\LoginKeyUtil.java
    util\StatusPresentation.java
    util\VerseProvider.java
    util\Prefs.java
    util\LocaleHelper.java
    notifications\NotificationHelper.java
    notifications\OverdueCheckReceiver.java
    notifications\NotificationScheduler.java
    ui\login\LoginActivity.java
    ui\shell\MainActivity.java
    ui\common\StatusBadgeView.java
    ui\common\ChangePasswordDialog.java
    ui\admin\dashboard\AdminDashboardFragment.java
    ui\admin\dashboard\PaymentsAdapter.java
    ui\admin\dashboard\ConfirmRejectSheet.java
    ui\admin\dashboard\RecordPaymentSheet.java
    ui\admin\properties\PropertiesFragment.java
    ui\admin\properties\ShopAdapter.java
    ui\admin\tenants\TenantsFragment.java
    ui\admin\tenants\TenantAdapter.java
    ui\admin\tenants\RegisterTenantSheet.java
    ui\admin\reports\ReportsFragment.java
    ui\admin\reports\BarChartView.java
    ui\admin\reports\DonutView.java
    ui\admin\notifications\AdminNotificationsFragment.java
    ui\admin\notifications\NotificationAdapter.java
    ui\superadmin\users\UsersFragment.java
    ui\superadmin\users\UserAdapter.java
    ui\superadmin\users\RegisterAdminSheet.java
    ui\tenant\dashboard\TenantDashboardFragment.java
    ui\tenant\payments\TenantPaymentsFragment.java
    ui\tenant\details\TenantDetailsFragment.java
    ui\tenant\history\TenantHistoryFragment.java
    ui\tenant\notifications\TenantNotificationsFragment.java
  app\src\main\res\
    values\colors.xml, dimens.xml, strings.xml, themes.xml
    values-sw\strings.xml
    font\inter_regular.ttf, inter_medium.ttf, inter_semibold.ttf, inter_bold.ttf, inter.xml
    layout\...                             # one per screen/adapter-row/sheet, named in each task
    drawable\...                           # vector icons, named in each task
    xml\backup_rules.xml, data_extraction_rules.xml   # already exist
  app\src\test\java\com\maduka\rentmanager\
    util\DateCalculatorTest.java
    util\LoginKeyUtilTest.java
    util\StatusPresentationTest.java
    util\VerseProviderTest.java
    data\model\PaymentRecordTest.java
  cli\build.gradle.kts
  cli\src\main\java\com\maduka\rentmanager\cli\RegisterCli.java
  cli\src\main\resources\ (service-account.json goes here, gitignored — user supplies it)
  rules\database.rules.json
```

---

### Task 1: Rename package to `com.maduka.rentmanager`, wire Firebase, base manifest

**Files:**
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Move: `app/src/main/java/com/example/maduka/*.java` → `app/src/main/java/com/maduka/rentmanager/*.java` (package statement updated)
- Move: `app/src/test/java/com/example/maduka/ExampleUnitTest.java` → `app/src/test/java/com/maduka/rentmanager/ExampleUnitTest.java`
- Move: `app/src/androidTest/java/com/example/maduka/ExampleInstrumentedTest.java` → `app/src/androidTest/java/com/maduka/rentmanager/ExampleInstrumentedTest.java`
- Create: `app/src/main/java/com/maduka/rentmanager/MadukaApp.java`

**Interfaces:**
- Produces: `MadukaApp` — Application subclass, referenced by `AndroidManifest.xml`'s `android:name`. All later tasks assume Firebase is already initialized by the time any Activity runs (no manual `FirebaseApp.initializeApp` calls needed elsewhere).

- [ ] **Step 1: Rename directories and package statements**

Move every file under `app/src/main/java/com/example/maduka/` (and the two test files) to the mirrored path under `com/maduka/rentmanager/`, updating each file's `package com.example.maduka;` line to `package com.maduka.rentmanager;`. Delete the now-empty `com/example` tree.

- [ ] **Step 2: Update `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.2.1"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
appcompat = "1.7.1"
material = "1.14.0"
recyclerview = "1.4.0"
swiperefreshlayout = "1.1.0"
firebaseBom = "33.7.0"
googleServices = "4.4.2"

[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
swiperefreshlayout = { group = "androidx.swiperefreshlayout", name = "swiperefreshlayout", version.ref = "swiperefreshlayout" }
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
firebase-database = { group = "com.google.firebase", name = "firebase-database" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 3: Update `build.gradle.kts` (root)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 4: Update `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.maduka.rentmanager"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.maduka.rentmanager"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization { enable = false }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
```

- [ ] **Step 5: `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

    <application
        android:name=".MadukaApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Maduka">

        <activity
            android:name=".ui.login.LoginActivity"
            android:exported="true"
            android:theme="@style/Theme.Maduka">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ui.shell.MainActivity"
            android:exported="false" />

        <receiver
            android:name=".notifications.OverdueCheckReceiver"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 6: `MadukaApp.java`**

```java
package com.maduka.rentmanager;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;
import com.maduka.rentmanager.notifications.NotificationHelper;

public class MadukaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        NotificationHelper.createChannels(this);
    }
}
```

- [ ] **Step 7: Verify the empty shell still builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (the app has no launchable content yet beyond a manifest referencing classes created in later tasks — if Gradle fails because `LoginActivity`/`MainActivity`/`OverdueCheckReceiver`/`NotificationHelper` don't exist yet, stub each with a one-line class body just enough to compile, and let Tasks 6/7/13/18 fill them in for real.)

- [ ] **Step 8: Commit**

```bash
git init
git add -A
git commit -m "chore: rename package to com.maduka.rentmanager, wire Firebase deps"
```

---

### Task 2: Design tokens — colors, dimens, type, theme

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/dimens.xml`
- Create: `app/src/main/res/values/themes.xml` (replaces default generated one)
- Create: `app/src/main/res/font/inter.xml` + four `.ttf` files (downloaded from `github.com/google/fonts/tree/main/ofl/inter`, OFL-1.1 licensed, safe to bundle)
- Modify: `app/src/main/res/values/strings.xml` (`app_name` only for now; screen copy strings land in each screen's task)

**Interfaces:**
- Produces: color resource names `md_bg`, `md_surface`, `md_text`, `md_divider`, `md_accent`, `md_accent_2`, `md_accent_100..900`, `md_neutral_100..900`, `md_status_good_fg/bg/border`, `md_status_wait_fg/bg/border`, `md_status_bad_fg/bg/border`, `md_status_empty_fg`; dimen names `space_1..8`, `radius_sm/md/lg`. Every later layout task references these names — do not invent parallel ad-hoc colors.

- [ ] **Step 1: Download Inter static fonts**

Run:
```bash
mkdir -p app/src/main/res/font
for w in Regular Medium SemiBold Bold; do
  curl -sL "https://raw.githubusercontent.com/google/fonts/main/ofl/inter/static/Inter-$w.ttf" -o "app/src/main/res/font/inter_$(echo $w | tr '[:upper:]' '[:lower:]').ttf"
done
```
Expected: four non-empty `.ttf` files under `app/src/main/res/font/`.

- [ ] **Step 2: `colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="md_bg">#161826</color>
    <color name="md_surface">#232532</color>
    <color name="md_text">#E9E9ED</color>
    <color name="md_divider">#29E9E9ED</color>

    <color name="md_accent">#9184D9</color>
    <color name="md_accent_2">#A7A1DB</color>

    <color name="md_accent_100">#F5F4FF</color>
    <color name="md_accent_200">#E7E5FE</color>
    <color name="md_accent_300">#D2CEFD</color>
    <color name="md_accent_400">#B5ABFC</color>
    <color name="md_accent_500">#968AE0</color>
    <color name="md_accent_600">#796CBF</color>
    <color name="md_accent_700">#5D5294</color>
    <color name="md_accent_800">#423A6A</color>
    <color name="md_accent_900">#2B2741</color>

    <color name="md_neutral_100">#F3F5FE</color>
    <color name="md_neutral_200">#E4E7F5</color>
    <color name="md_neutral_300">#CFD3E5</color>
    <color name="md_neutral_400">#B2B6CA</color>
    <color name="md_neutral_500">#9397AB</color>
    <color name="md_neutral_600">#75798C</color>
    <color name="md_neutral_700">#595D6C</color>
    <color name="md_neutral_800">#3F424D</color>
    <color name="md_neutral_900">#292B31</color>

    <color name="md_status_good_fg">#6EE7B7</color>
    <color name="md_status_good_bg">#20302A</color>
    <color name="md_status_good_border">#2F6B52</color>

    <color name="md_status_wait_fg">#FBD77A</color>
    <color name="md_status_wait_bg">#3A3222</color>
    <color name="md_status_wait_border">#7A5C14</color>

    <color name="md_status_bad_fg">#FCA5A5</color>
    <color name="md_status_bad_bg">#31232A</color>
    <color name="md_status_bad_border">#7F3A3A</color>

    <color name="md_status_empty_fg">#9397AB</color>
</resources>
```

- [ ] **Step 3: `dimens.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="space_1">3dp</dimen>
    <dimen name="space_2">6dp</dimen>
    <dimen name="space_3">8dp</dimen>
    <dimen name="space_4">11dp</dimen>
    <dimen name="space_6">17dp</dimen>
    <dimen name="space_8">22dp</dimen>
    <dimen name="radius_sm">4dp</dimen>
    <dimen name="radius_md">8dp</dimen>
    <dimen name="radius_lg">14dp</dimen>
</resources>
```

- [ ] **Step 4: `font/inter.xml` font family**

```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android">
    <font android:font="@font/inter_regular" android:fontWeight="400" />
    <font android:font="@font/inter_medium" android:fontWeight="500" />
    <font android:font="@font/inter_semibold" android:fontWeight="600" />
    <font android:font="@font/inter_bold" android:fontWeight="700" />
</font-family>
```

- [ ] **Step 5: `themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Maduka" parent="Theme.MaterialComponents.NoActionBar">
        <item name="android:windowBackground">@color/md_bg</item>
        <item name="colorPrimary">@color/md_accent</item>
        <item name="colorPrimaryDark">@color/md_bg</item>
        <item name="colorAccent">@color/md_accent_2</item>
        <item name="colorSurface">@color/md_surface</item>
        <item name="colorOnSurface">@color/md_text</item>
        <item name="colorOnPrimary">@color/md_bg</item>
        <item name="android:textColorPrimary">@color/md_text</item>
        <item name="android:textColor">@color/md_text</item>
        <item name="android:statusBarColor">@color/md_bg</item>
        <item name="android:navigationBarColor">@color/md_bg</item>
        <item name="android:fontFamily">@font/inter</item>
        <item name="fontFamily">@font/inter</item>
    </style>
</resources>
```

- [ ] **Step 6: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, no missing-resource errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values/dimens.xml app/src/main/res/values/themes.xml app/src/main/res/font
git commit -m "feat: add Maduka dark design tokens (colors, spacing, Inter type)"
```

---

### Task 3: Data models and Firebase schema constants

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/UserRole.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/PaymentStatus.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/PresenceStatus.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/RejectionReason.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/Shop.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/Tenant.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/AdminUser.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/SuperAdminUser.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/PaymentRecord.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/model/AppNotification.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/FirebaseSchema.java`
- Test: `app/src/test/java/com/maduka/rentmanager/data/model/PaymentRecordTest.java`

**Interfaces:**
- Produces: every field name below, exactly spelled — every repository task (5) and every UI task (8–17) binds to these getters/setters. Firebase Realtime Database requires a public no-arg constructor and public getters/setters (no `final` fields) on every model, which is why these are plain mutable POJOs rather than records.

- [ ] **Step 1: Enums**

```java
// UserRole.java
package com.maduka.rentmanager.data.model;

public enum UserRole {
    SUPER_ADMIN("super_admins"), ADMIN("admins"), TENANT("tenants");

    public final String node;
    UserRole(String node) { this.node = node; }
}
```

```java
// PaymentStatus.java
package com.maduka.rentmanager.data.model;

public enum PaymentStatus { PENDING, CONFIRMED, REJECTED }
```

```java
// PresenceStatus.java
package com.maduka.rentmanager.data.model;

public enum PresenceStatus { YUPO, HAYUPO }
```

```java
// RejectionReason.java
package com.maduka.rentmanager.data.model;

public enum RejectionReason {
    DUPLICATE_RECORD, INCORRECT_AMOUNT, INCORRECT_DATE, WRONG_TENANT, NO_RECEIPT, OTHER
}
```

- [ ] **Step 2: `Shop.java`**

```java
package com.maduka.rentmanager.data.model;

public class Shop {
    private String shopId;      // "A1".."A10"
    private long monthlyRent;   // 0 when empty
    private boolean occupied;
    private String tenantUid;   // null when empty
    private long createdAt;
    private long updatedAt;

    public Shop() {}

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public long getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(long monthlyRent) { this.monthlyRent = monthlyRent; }
    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }
    public String getTenantUid() { return tenantUid; }
    public void setTenantUid(String tenantUid) { this.tenantUid = tenantUid; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: `Tenant.java`**

```java
package com.maduka.rentmanager.data.model;

public class Tenant {
    private String uid;
    private String name;
    private String phone;
    private String email;          // optional
    private String authEmail;      // Firebase Auth login email (always set)
    private String shopId;         // "A1".."A10"
    private long monthlyRent;
    private long moveInDate;       // epoch millis
    private PresenceStatus presenceStatus = PresenceStatus.YUPO;
    private long lastPresenceCheckAt;
    private long lastPaymentDate;  // epoch millis
    private long dueDate;          // epoch millis
    private int monthsCovered;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String notes;
    private String registeredByUid;
    private long createdAt;
    private long updatedAt;

    public Tenant() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAuthEmail() { return authEmail; }
    public void setAuthEmail(String authEmail) { this.authEmail = authEmail; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public long getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(long monthlyRent) { this.monthlyRent = monthlyRent; }
    public long getMoveInDate() { return moveInDate; }
    public void setMoveInDate(long moveInDate) { this.moveInDate = moveInDate; }
    public PresenceStatus getPresenceStatus() { return presenceStatus; }
    public void setPresenceStatus(PresenceStatus presenceStatus) { this.presenceStatus = presenceStatus; }
    public long getLastPresenceCheckAt() { return lastPresenceCheckAt; }
    public void setLastPresenceCheckAt(long lastPresenceCheckAt) { this.lastPresenceCheckAt = lastPresenceCheckAt; }
    public long getLastPaymentDate() { return lastPaymentDate; }
    public void setLastPaymentDate(long lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }
    public long getDueDate() { return dueDate; }
    public void setDueDate(long dueDate) { this.dueDate = dueDate; }
    public int getMonthsCovered() { return monthsCovered; }
    public void setMonthsCovered(int monthsCovered) { this.monthsCovered = monthsCovered; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRegisteredByUid() { return registeredByUid; }
    public void setRegisteredByUid(String registeredByUid) { this.registeredByUid = registeredByUid; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: `AdminUser.java` and `SuperAdminUser.java`**

```java
package com.maduka.rentmanager.data.model;

import java.util.ArrayList;
import java.util.List;

public class AdminUser {
    private String uid;
    private String name;
    private String username;
    private String email;
    private String phone;
    private String authEmail;
    private List<String> shopsAssigned = new ArrayList<>(); // subset of A1..A10
    private PresenceStatus status = PresenceStatus.YUPO;    // Yupo = active account, Hayupo = disabled
    private long lastLogin;
    private long createdAt;
    private long updatedAt;

    public AdminUser() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAuthEmail() { return authEmail; }
    public void setAuthEmail(String authEmail) { this.authEmail = authEmail; }
    public List<String> getShopsAssigned() { return shopsAssigned; }
    public void setShopsAssigned(List<String> shopsAssigned) { this.shopsAssigned = shopsAssigned; }
    public PresenceStatus getStatus() { return status; }
    public void setStatus(PresenceStatus status) { this.status = status; }
    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
```

```java
package com.maduka.rentmanager.data.model;

public class SuperAdminUser {
    private String uid;
    private String name;
    private String username;
    private String email;
    private String phone;
    private String authEmail;
    private long lastLogin;
    private long createdAt;
    private long updatedAt;

    public SuperAdminUser() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAuthEmail() { return authEmail; }
    public void setAuthEmail(String authEmail) { this.authEmail = authEmail; }
    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 5: `PaymentRecord.java`**

```java
package com.maduka.rentmanager.data.model;

public class PaymentRecord {
    private String paymentId;
    private String tenantUid;
    private String shopId;
    private long amount;
    private int monthsCovered;
    private long paymentDate;
    private String paymentMethod;      // "mpesa" | "bank" | "cash" | "cheque"
    private String receiptReference;
    private String notes;
    private String recordedByUid;
    private String recordedByName;
    private PaymentStatus status = PaymentStatus.PENDING;
    private RejectionReason rejectionReason;
    private String rejectionNote;
    private String confirmedByUid;
    private String confirmedByName;
    // snapshot taken at record time, used to roll back the tenant on reject
    private long previousLastPaymentDate;
    private long previousDueDate;
    private int previousMonthsCovered;
    private long createdAt;
    private long updatedAt;

    public PaymentRecord() {}

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getTenantUid() { return tenantUid; }
    public void setTenantUid(String tenantUid) { this.tenantUid = tenantUid; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public int getMonthsCovered() { return monthsCovered; }
    public void setMonthsCovered(int monthsCovered) { this.monthsCovered = monthsCovered; }
    public long getPaymentDate() { return paymentDate; }
    public void setPaymentDate(long paymentDate) { this.paymentDate = paymentDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReceiptReference() { return receiptReference; }
    public void setReceiptReference(String receiptReference) { this.receiptReference = receiptReference; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRecordedByUid() { return recordedByUid; }
    public void setRecordedByUid(String recordedByUid) { this.recordedByUid = recordedByUid; }
    public String getRecordedByName() { return recordedByName; }
    public void setRecordedByName(String recordedByName) { this.recordedByName = recordedByName; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(RejectionReason rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getRejectionNote() { return rejectionNote; }
    public void setRejectionNote(String rejectionNote) { this.rejectionNote = rejectionNote; }
    public String getConfirmedByUid() { return confirmedByUid; }
    public void setConfirmedByUid(String confirmedByUid) { this.confirmedByUid = confirmedByUid; }
    public String getConfirmedByName() { return confirmedByName; }
    public void setConfirmedByName(String confirmedByName) { this.confirmedByName = confirmedByName; }
    public long getPreviousLastPaymentDate() { return previousLastPaymentDate; }
    public void setPreviousLastPaymentDate(long previousLastPaymentDate) { this.previousLastPaymentDate = previousLastPaymentDate; }
    public long getPreviousDueDate() { return previousDueDate; }
    public void setPreviousDueDate(long previousDueDate) { this.previousDueDate = previousDueDate; }
    public int getPreviousMonthsCovered() { return previousMonthsCovered; }
    public void setPreviousMonthsCovered(int previousMonthsCovered) { this.previousMonthsCovered = previousMonthsCovered; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /** True only when a different user than the recorder is trying to act. Two-person rule. */
    public boolean canBeActionedBy(String uid) {
        return uid != null && !uid.equals(recordedByUid);
    }
}
```

- [ ] **Step 6: `AppNotification.java`**

```java
package com.maduka.rentmanager.data.model;

public class AppNotification {
    public static final String TYPE_OVERDUE = "overdue";
    public static final String TYPE_DUE_TODAY = "due_today";
    public static final String TYPE_DUE_SOON = "due_soon";
    public static final String TYPE_PAYMENT_CONFIRMED = "payment_confirmed";
    public static final String TYPE_PAYMENT_REJECTED = "payment_rejected";
    public static final String TYPE_RECEIPT_AVAILABLE = "receipt_available";
    public static final String TYPE_SYSTEM = "system";

    private String notificationId;
    private String userUid;
    private String type;
    private String title;
    private String message;
    private String relatedTenantUid;
    private String relatedPaymentId;
    private boolean read;
    private long createdAt;

    public AppNotification() {}

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    public String getUserUid() { return userUid; }
    public void setUserUid(String userUid) { this.userUid = userUid; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRelatedTenantUid() { return relatedTenantUid; }
    public void setRelatedTenantUid(String relatedTenantUid) { this.relatedTenantUid = relatedTenantUid; }
    public String getRelatedPaymentId() { return relatedPaymentId; }
    public void setRelatedPaymentId(String relatedPaymentId) { this.relatedPaymentId = relatedPaymentId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 7: `FirebaseSchema.java` — node name constants**

```java
package com.maduka.rentmanager.data;

public final class FirebaseSchema {
    private FirebaseSchema() {}

    public static final String SUPER_ADMINS = "super_admins";
    public static final String ADMINS = "admins";
    public static final String TENANTS = "tenants";
    public static final String SHOPS = "shops";
    public static final String PAYMENTS = "payments";
    public static final String NOTIFICATIONS = "notifications";
    public static final String LOGIN_INDEX = "login_index"; // login_index/{role}/{sanitizedKey} -> authEmail, world-readable
}
```

- [ ] **Step 8: Write the failing test for the two-person rule**

```java
// PaymentRecordTest.java
package com.maduka.rentmanager.data.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentRecordTest {
    @Test
    public void recorderCannotActionTheirOwnPayment() {
        PaymentRecord p = new PaymentRecord();
        p.setRecordedByUid("admin-elias");

        assertFalse(p.canBeActionedBy("admin-elias"));
        assertTrue(p.canBeActionedBy("admin-sarah"));
        assertFalse(p.canBeActionedBy(null));
    }
}
```

- [ ] **Step 9: Run test to verify it fails, then it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.maduka.rentmanager.data.model.PaymentRecordTest"`
Expected first run (before `canBeActionedBy` exists): compile error. After Step 5's `canBeActionedBy` is in place: PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/data app/src/test/java/com/maduka/rentmanager/data
git commit -m "feat: add Firebase data models and two-person-rule test"
```

---

### Task 4: `DateCalculator`, `LoginKeyUtil`, `StatusPresentation`, `VerseProvider` (TDD)

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/util/DateCalculator.java`
- Create: `app/src/main/java/com/maduka/rentmanager/util/LoginKeyUtil.java`
- Create: `app/src/main/java/com/maduka/rentmanager/util/StatusPresentation.java`
- Create: `app/src/main/java/com/maduka/rentmanager/util/VerseProvider.java`
- Test: `app/src/test/java/com/maduka/rentmanager/util/DateCalculatorTest.java`
- Test: `app/src/test/java/com/maduka/rentmanager/util/LoginKeyUtilTest.java`
- Test: `app/src/test/java/com/maduka/rentmanager/util/VerseProviderTest.java`

**Interfaces:**
- Consumes: nothing (pure utility layer).
- Produces: `DateCalculator.addMonths(long epochMillis, int months)`, `.daysBetween(long fromMillis, long toMillis)`, `.isOverdue(long dueDateMillis, long nowMillis)`, `.formatDdMmYyyy(long millis)` — used by every repository and every screen that shows a due date. `LoginKeyUtil.sanitize(String rawIdentifier)` — used by `AuthRepository` (Task 5) to build/read `login_index` keys. `VerseProvider.pickVerse(long seed)` returning a `Verse{String text; String reference;}` — used by `LoginActivity` and the Admin dashboard header.

- [ ] **Step 1: Write the failing tests**

```java
// DateCalculatorTest.java
package com.maduka.rentmanager.util;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class DateCalculatorTest {

    private long ymd(int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    @Test
    public void addMonths_addsCalendarMonths_notFixedThirtyDayBlocks() {
        // Canvas sample: Asha M. paid 2 months on 02/09/2026 -> due 02/11/2026
        long paid = ymd(2026, 9, 2);
        long due = DateCalculator.addMonths(paid, 2);
        assertEquals(ymd(2026, 11, 2), due);
    }

    @Test
    public void isOverdue_trueWhenDueDateInThePast() {
        long due = ymd(2026, 8, 15);
        long now = ymd(2026, 9, 4);
        assertTrue(DateCalculator.isOverdue(due, now));
    }

    @Test
    public void isOverdue_falseWhenDueDateInTheFuture() {
        long due = ymd(2026, 11, 2);
        long now = ymd(2026, 9, 4);
        assertFalse(DateCalculator.isOverdue(due, now));
    }

    @Test
    public void daysBetween_matchesCanvasSample_neemaTwentyDaysOverdue() {
        long due = ymd(2026, 8, 15);
        long now = ymd(2026, 9, 4);
        assertEquals(20, DateCalculator.daysBetween(due, now));
    }

    @Test
    public void formatDdMmYyyy_matchesCanvasFormat() {
        assertEquals("02/09/2026", DateCalculator.formatDdMmYyyy(ymd(2026, 9, 2)));
    }
}
```

```java
// LoginKeyUtilTest.java
package com.maduka.rentmanager.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoginKeyUtilTest {
    @Test
    public void sanitize_lowercasesAndStripsRtdbIllegalChars() {
        assertEquals("eliasnyoni_gmail_com", LoginKeyUtil.sanitize("Elias.Nyoni@Gmail.com"));
    }

    @Test
    public void sanitize_stripsNonDigitsFromPhoneNumbers() {
        assertEquals("0713220441", LoginKeyUtil.sanitize("0713 220 441"));
    }

    @Test
    public void sanitize_lowercasesUsernames() {
        assertEquals("elias", LoginKeyUtil.sanitize("Elias"));
    }
}
```

```java
// VerseProviderTest.java
package com.maduka.rentmanager.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class VerseProviderTest {
    @Test
    public void pickVerse_isDeterministicForSameSeed() {
        assertEquals(VerseProvider.pickVerse(7).reference, VerseProvider.pickVerse(7).reference);
    }

    @Test
    public void pickVerse_cyclesThroughAllVersesAsSeedIncreases() {
        int poolSize = VerseProvider.poolSize();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < poolSize; i++) seen.add(VerseProvider.pickVerse(i).reference);
        assertEquals(poolSize, seen.size());
    }

    @Test
    public void firstVerseMatchesCanvas() {
        VerseProvider.Verse v = VerseProvider.pickVerse(0);
        assertEquals("Proverbs 28:20, KJV", v.reference);
        assertTrue(v.text.startsWith("A faithful man shall abound with blessings"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — `DateCalculator`, `LoginKeyUtil`, `VerseProvider` do not exist yet.

- [ ] **Step 3: Implement `DateCalculator.java`**

```java
package com.maduka.rentmanager.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class DateCalculator {
    private DateCalculator() {}

    public static long addMonths(long epochMillis, int months) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(epochMillis);
        c.add(Calendar.MONTH, months);
        return c.getTimeInMillis();
    }

    /** Whole days between two instants; positive when `to` is after `from`. */
    public static int daysBetween(long fromMillis, long toMillis) {
        return (int) TimeUnit.MILLISECONDS.toDays(toMillis - fromMillis);
    }

    public static boolean isOverdue(long dueDateMillis, long nowMillis) {
        return dueDateMillis < nowMillis;
    }

    public static String formatDdMmYyyy(long millis) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(millis));
    }
}
```

- [ ] **Step 4: Implement `LoginKeyUtil.java`**

```java
package com.maduka.rentmanager.util;

public final class LoginKeyUtil {
    private LoginKeyUtil() {}

    /**
     * Firebase RTDB keys forbid '.', '#', '$', '/', '[', ']'. Also used to
     * normalize phone numbers (strip spaces/dashes) and usernames/emails
     * (lowercase) so the same identifier always resolves to the same
     * login_index key regardless of how the user typed it.
     */
    public static String sanitize(String rawIdentifier) {
        if (rawIdentifier == null) return "";
        String trimmed = rawIdentifier.trim().toLowerCase(java.util.Locale.US);
        boolean looksLikePhone = trimmed.replaceAll("[^0-9]", "").length() >= 7
                && trimmed.replaceAll("[0-9 +()\\-]", "").isEmpty();
        if (looksLikePhone) {
            return trimmed.replaceAll("[^0-9]", "");
        }
        return trimmed.replaceAll("[.#$\\[\\]/]", "_");
    }
}
```

- [ ] **Step 5: Implement `StatusPresentation.java`**

```java
package com.maduka.rentmanager.util;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.PaymentStatus;
import com.maduka.rentmanager.data.model.PresenceStatus;

/** Maps the app's four business statuses onto the shared 4-color status system. */
public final class StatusPresentation {
    private StatusPresentation() {}

    public enum Tone { GOOD, WAIT, BAD, EMPTY }

    public static Tone toneFor(PaymentStatus status) {
        switch (status) {
            case CONFIRMED: return Tone.GOOD;
            case REJECTED: return Tone.BAD;
            default: return Tone.WAIT;
        }
    }

    public static Tone toneFor(PresenceStatus status) {
        return status == PresenceStatus.YUPO ? Tone.GOOD : Tone.BAD;
    }

    public static int fgColorRes(Tone tone) {
        switch (tone) {
            case GOOD: return R.color.md_status_good_fg;
            case WAIT: return R.color.md_status_wait_fg;
            case BAD: return R.color.md_status_bad_fg;
            default: return R.color.md_status_empty_fg;
        }
    }

    public static int bgColorRes(Tone tone) {
        switch (tone) {
            case GOOD: return R.color.md_status_good_bg;
            case WAIT: return R.color.md_status_wait_bg;
            case BAD: return R.color.md_status_bad_bg;
            default: return android.R.color.transparent;
        }
    }

    public static int borderColorRes(Tone tone) {
        switch (tone) {
            case GOOD: return R.color.md_status_good_border;
            case WAIT: return R.color.md_status_wait_border;
            case BAD: return R.color.md_status_bad_border;
            default: return R.color.md_status_empty_fg;
        }
    }
}
```

- [ ] **Step 6: Implement `VerseProvider.java`**

Five King James Version verses about faithfulness, quoted exactly (public domain text). Index 0 matches the canvas's own sample so the deterministic test in Step 1 holds.

```java
package com.maduka.rentmanager.util;

import java.util.ArrayList;
import java.util.List;

public final class VerseProvider {
    private VerseProvider() {}

    public static final class Verse {
        public final String text;
        public final String reference;
        public Verse(String text, String reference) { this.text = text; this.reference = reference; }
    }

    private static final List<Verse> POOL = new ArrayList<>();
    static {
        POOL.add(new Verse(
                "A faithful man shall abound with blessings: but he that maketh haste to be rich shall not be innocent.",
                "Proverbs 28:20, KJV"));
        POOL.add(new Verse(
                "He that is faithful in that which is least is faithful also in much: and he that is unjust in the least is unjust also in much.",
                "Luke 16:10, KJV"));
        POOL.add(new Verse(
                "Moreover it is required in stewards, that a man be found faithful.",
                "1 Corinthians 4:2, KJV"));
        POOL.add(new Verse(
                "Well done, thou good and faithful servant: thou hast been faithful over a few things, I will make thee ruler over many things: enter thou into the joy of thy lord.",
                "Matthew 25:21, KJV"));
        POOL.add(new Verse(
                "Let not mercy and truth forsake thee: bind them about thy neck; write them upon the table of thine heart.",
                "Proverbs 3:3, KJV"));
    }

    public static int poolSize() { return POOL.size(); }

    /** Deterministic pick so the same seed (e.g. a sign-in counter) always yields the same verse. */
    public static Verse pickVerse(long seed) {
        int index = (int) (((seed % POOL.size()) + POOL.size()) % POOL.size());
        return POOL.get(index);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all of `DateCalculatorTest`, `LoginKeyUtilTest`, `VerseProviderTest`; `StatusPresentation` has no test because it only maps enums to Android resource ints — nothing to assert without a real `Resources` instance).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/util app/src/test/java/com/maduka/rentmanager/util
git commit -m "feat: add date/status/verse utilities with unit tests"
```

---

### Task 5: `FirebaseManager`, `AuthRepository`, and the other repositories

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/data/FirebaseManager.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/AuthRepository.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/ShopRepository.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/TenantRepository.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/UserRepository.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/PaymentRepository.java`
- Create: `app/src/main/java/com/maduka/rentmanager/data/NotificationRepository.java`

**Interfaces:**
- Consumes: `FirebaseSchema` (Task 3), `DateCalculator`/`LoginKeyUtil` (Task 4).
- Produces: `AuthRepository.signIn(UserRole role, String identifier, String password, Callback<AuthResult> cb)`, `.signOut()`, `.changePassword(String newPassword, Callback<Void> cb)`, `.currentUid()`, `.currentRole()` (cached from the last successful `signIn`, persisted via `Prefs` in Task 6 — `AuthRepository` itself just exposes the setter/getter). `PaymentRepository.recordPayment(PaymentRecord draft, Callback<Void> cb)` (applies the optimistic tenant update inside a single value-listener read + two writes), `.confirmPayment(String paymentId, String confirmerUid, String confirmerName, Callback<Void> cb)`, `.rejectPayment(String paymentId, RejectionReason reason, String note, String confirmerUid, String confirmerName, Callback<Void> cb)` (performs the rollback described in Global Constraints), `.observePayments(PaymentsListener listener)`. Every later UI task (8+) calls only these repository methods, never `FirebaseDatabase` directly.

- [ ] **Step 1: `FirebaseManager.java` — thin wrapper exposing the root reference and a generic callback shape**

```java
package com.maduka.rentmanager.data;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public final class FirebaseManager {
    private static FirebaseManager instance;
    private final DatabaseReference root;

    private FirebaseManager() {
        root = FirebaseDatabase.getInstance().getReference();
    }

    public static synchronized FirebaseManager get() {
        if (instance == null) instance = new FirebaseManager();
        return instance;
    }

    public DatabaseReference root() { return root; }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }
}
```

- [ ] **Step 2: `AuthRepository.java`**

```java
package com.maduka.rentmanager.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.LoginKeyUtil;

public class AuthRepository {
    private final FirebaseManager fb = FirebaseManager.get();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private UserRole cachedRole;

    public static class AuthResult {
        public final String uid;
        public final UserRole role;
        public AuthResult(String uid, UserRole role) { this.uid = uid; this.role = role; }
    }

    /**
     * Resolves the free-text identifier (username, email, or phone) against
     * the public login_index/{role} node to find the Firebase Auth email,
     * then signs in with that email + the entered password.
     */
    public void signIn(UserRole role, String identifier, String password, FirebaseManager.Callback<AuthResult> cb) {
        String key = LoginKeyUtil.sanitize(identifier);
        fb.root().child(FirebaseSchema.LOGIN_INDEX).child(role.node).child(key)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String authEmail = snapshot.getValue(String.class);
                        if (authEmail == null) {
                            cb.onError("No account found for that role and identifier.");
                            return;
                        }
                        auth.signInWithEmailAndPassword(authEmail, password)
                                .addOnSuccessListener(result -> {
                                    cachedRole = role;
                                    cb.onSuccess(new AuthResult(result.getUser().getUid(), role));
                                })
                                .addOnFailureListener(e -> cb.onError(e.getMessage()));
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        cb.onError(error.getMessage());
                    }
                });
    }

    public void signOut() {
        auth.signOut();
        cachedRole = null;
    }

    public void changePassword(String newPassword, FirebaseManager.Callback<Void> cb) {
        if (auth.getCurrentUser() == null) { cb.onError("Not signed in."); return; }
        auth.getCurrentUser().updatePassword(newPassword)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public String currentUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public UserRole cachedRole() { return cachedRole; }
    public void setCachedRole(UserRole role) { this.cachedRole = role; }
}
```

- [ ] **Step 3: `ShopRepository.java`, `TenantRepository.java`, `UserRepository.java`**

```java
package com.maduka.rentmanager.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.Shop;
import java.util.ArrayList;
import java.util.List;

public class ShopRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface ShopsListener { void onShops(List<Shop> shops); void onError(String message); }

    /** All 10 shops (A1..A10), live-updating. */
    public void observeShops(ShopsListener listener) {
        fb.root().child(FirebaseSchema.SHOPS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Shop> shops = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shop s = child.getValue(Shop.class);
                    if (s != null) shops.add(s);
                }
                shops.sort((a, b) -> a.getShopId().compareTo(b.getShopId()));
                listener.onShops(shops);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }
}
```

```java
package com.maduka.rentmanager.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Tenant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TenantRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface TenantsListener { void onTenants(List<Tenant> tenants); void onError(String message); }
    public interface TenantListener { void onTenant(Tenant tenant); void onError(String message); }

    public void observeTenants(TenantsListener listener) {
        fb.root().child(FirebaseSchema.TENANTS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Tenant> tenants = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Tenant t = child.getValue(Tenant.class);
                    if (t != null) tenants.add(t);
                }
                listener.onTenants(tenants);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    public void observeTenant(String uid, TenantListener listener) {
        fb.root().child(FirebaseSchema.TENANTS).child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) { listener.onTenant(snapshot.getValue(Tenant.class)); }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** Yupo/Hayupo presence-check button handler. */
    public void setPresence(String tenantUid, PresenceStatus status, long nowMillis, FirebaseManager.Callback<Void> cb) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("presenceStatus", status.name());
        updates.put("lastPresenceCheckAt", nowMillis);
        updates.put("updatedAt", nowMillis);
        fb.root().child(FirebaseSchema.TENANTS).child(tenantUid).updateChildren(updates)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
```

```java
package com.maduka.rentmanager.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.AdminUser;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface AdminsListener { void onAdmins(List<AdminUser> admins); void onError(String message); }

    public void observeAdmins(AdminsListener listener) {
        fb.root().child(FirebaseSchema.ADMINS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<AdminUser> admins = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    AdminUser a = child.getValue(AdminUser.class);
                    if (a != null) admins.add(a);
                }
                listener.onAdmins(admins);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }
}
```

- [ ] **Step 4: `PaymentRepository.java` — record / confirm / reject with the optimistic-apply + rollback rule**

```java
package com.maduka.rentmanager.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.PaymentStatus;
import com.maduka.rentmanager.data.model.RejectionReason;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.DateCalculator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface PaymentsListener { void onPayments(List<PaymentRecord> payments); void onError(String message); }

    public void observePayments(PaymentsListener listener) {
        fb.root().child(FirebaseSchema.PAYMENTS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PaymentRecord> payments = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    PaymentRecord p = child.getValue(PaymentRecord.class);
                    if (p != null) payments.add(p);
                }
                payments.sort((a, b) -> Long.compare(b.getPaymentDate(), a.getPaymentDate()));
                listener.onPayments(payments);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** Records a payment, snapshots the tenant's prior state, and optimistically applies the new due date. */
    public void recordPayment(PaymentRecord draft, FirebaseManager.Callback<Void> cb) {
        DatabaseReference tenantRef = fb.root().child(FirebaseSchema.TENANTS).child(draft.getTenantUid());
        tenantRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Tenant tenant = snapshot.getValue(Tenant.class);
                if (tenant == null) { cb.onError("Tenant not found."); return; }

                long now = System.currentTimeMillis();
                draft.setPreviousLastPaymentDate(tenant.getLastPaymentDate());
                draft.setPreviousDueDate(tenant.getDueDate());
                draft.setPreviousMonthsCovered(tenant.getMonthsCovered());
                draft.setStatus(PaymentStatus.PENDING);
                draft.setCreatedAt(now);
                draft.setUpdatedAt(now);

                DatabaseReference paymentRef = fb.root().child(FirebaseSchema.PAYMENTS).push();
                draft.setPaymentId(paymentRef.getKey());

                long newDueDate = DateCalculator.addMonths(draft.getPaymentDate(), draft.getMonthsCovered());
                Map<String, Object> tenantUpdates = new HashMap<>();
                tenantUpdates.put("lastPaymentDate", draft.getPaymentDate());
                tenantUpdates.put("dueDate", newDueDate);
                tenantUpdates.put("monthsCovered", tenant.getMonthsCovered() + draft.getMonthsCovered());
                tenantUpdates.put("updatedAt", now);

                Map<String, Object> multiPath = new HashMap<>();
                multiPath.put("/" + FirebaseSchema.PAYMENTS + "/" + paymentRef.getKey(), draft);
                for (Map.Entry<String, Object> e : tenantUpdates.entrySet()) {
                    multiPath.put("/" + FirebaseSchema.TENANTS + "/" + draft.getTenantUid() + "/" + e.getKey(), e.getValue());
                }

                fb.root().updateChildren(multiPath)
                        .addOnSuccessListener(v -> cb.onSuccess(null))
                        .addOnFailureListener(e -> cb.onError(e.getMessage()));
            }
            @Override
            public void onCancelled(DatabaseError error) { cb.onError(error.getMessage()); }
        });
    }

    public void confirmPayment(String paymentId, String confirmerUid, String confirmerName, FirebaseManager.Callback<Void> cb) {
        DatabaseReference paymentRef = fb.root().child(FirebaseSchema.PAYMENTS).child(paymentId);
        paymentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                PaymentRecord payment = snapshot.getValue(PaymentRecord.class);
                if (payment == null) { cb.onError("Payment not found."); return; }
                if (!payment.canBeActionedBy(confirmerUid)) { cb.onError("You recorded this payment; another admin must confirm it."); return; }

                Map<String, Object> updates = new HashMap<>();
                updates.put("status", PaymentStatus.CONFIRMED.name());
                updates.put("confirmedByUid", confirmerUid);
                updates.put("confirmedByName", confirmerName);
                updates.put("updatedAt", System.currentTimeMillis());
                paymentRef.updateChildren(updates)
                        .addOnSuccessListener(v -> cb.onSuccess(null))
                        .addOnFailureListener(e -> cb.onError(e.getMessage()));
            }
            @Override
            public void onCancelled(DatabaseError error) { cb.onError(error.getMessage()); }
        });
    }

    /** Rejects the payment and rolls the tenant's due-date fields back to their pre-payment snapshot. */
    public void rejectPayment(String paymentId, RejectionReason reason, String note,
                               String confirmerUid, String confirmerName, FirebaseManager.Callback<Void> cb) {
        DatabaseReference paymentRef = fb.root().child(FirebaseSchema.PAYMENTS).child(paymentId);
        paymentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                PaymentRecord payment = snapshot.getValue(PaymentRecord.class);
                if (payment == null) { cb.onError("Payment not found."); return; }
                if (!payment.canBeActionedBy(confirmerUid)) { cb.onError("You recorded this payment; another admin must reject it."); return; }

                long now = System.currentTimeMillis();
                Map<String, Object> multiPath = new HashMap<>();
                String base = "/" + FirebaseSchema.PAYMENTS + "/" + paymentId + "/";
                multiPath.put(base + "status", PaymentStatus.REJECTED.name());
                multiPath.put(base + "rejectionReason", reason.name());
                multiPath.put(base + "rejectionNote", note);
                multiPath.put(base + "confirmedByUid", confirmerUid);
                multiPath.put(base + "confirmedByName", confirmerName);
                multiPath.put(base + "updatedAt", now);

                String tenantBase = "/" + FirebaseSchema.TENANTS + "/" + payment.getTenantUid() + "/";
                multiPath.put(tenantBase + "lastPaymentDate", payment.getPreviousLastPaymentDate());
                multiPath.put(tenantBase + "dueDate", payment.getPreviousDueDate());
                multiPath.put(tenantBase + "monthsCovered", payment.getPreviousMonthsCovered());
                multiPath.put(tenantBase + "updatedAt", now);

                fb.root().updateChildren(multiPath)
                        .addOnSuccessListener(v -> cb.onSuccess(null))
                        .addOnFailureListener(e -> cb.onError(e.getMessage()));
            }
            @Override
            public void onCancelled(DatabaseError error) { cb.onError(error.getMessage()); }
        });
    }
}
```

- [ ] **Step 5: `NotificationRepository.java`**

```java
package com.maduka.rentmanager.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.AppNotification;
import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface NotificationsListener { void onNotifications(List<AppNotification> items); void onError(String message); }

    public void observeFor(String userUid, NotificationsListener listener) {
        fb.root().child(FirebaseSchema.NOTIFICATIONS).child(userUid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<AppNotification> items = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    AppNotification n = child.getValue(AppNotification.class);
                    if (n != null) items.add(n);
                }
                items.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                listener.onNotifications(items);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    public void push(AppNotification notification) {
        DatabaseReference ref = fb.root().child(FirebaseSchema.NOTIFICATIONS)
                .child(notification.getUserUid()).push();
        notification.setNotificationId(ref.getKey());
        notification.setCreatedAt(System.currentTimeMillis());
        ref.setValue(notification);
    }

    public void markRead(String userUid, String notificationId) {
        fb.root().child(FirebaseSchema.NOTIFICATIONS).child(userUid).child(notificationId)
                .child("read").setValue(true);
    }

    public void markAllRead(String userUid, List<AppNotification> current) {
        for (AppNotification n : current) {
            if (!n.isRead()) markRead(userUid, n.getNotificationId());
        }
    }
}
```

- [ ] **Step 6: Verify**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/data
git commit -m "feat: add FirebaseManager and repositories (auth, shops, tenants, payments, notifications)"
```

---

### Task 6: App-wide helpers — `Prefs`, `LocaleHelper`

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/util/Prefs.java`
- Create: `app/src/main/java/com/maduka/rentmanager/util/LocaleHelper.java`

**Interfaces:**
- Produces: `Prefs.get(Context)`, `.isRememberMe()`, `.setRememberMe(boolean, String role, String identifier)`, `.rememberedRole()`, `.rememberedIdentifier()`, `.language()` ("en"|"sw"), `.setLanguage(String)`, `.signInCount()` / `.bumpSignInCount()` (feeds `VerseProvider.pickVerse`). `LocaleHelper.wrap(Context base, String languageTag)` — called from `LoginActivity.attachBaseContext` and `MainActivity.attachBaseContext` (Task 7/19) so the whole app switches locale without a restart-from-scratch.

- [ ] **Step 1: `Prefs.java`**

```java
package com.maduka.rentmanager.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "maduka_prefs";
    private static final String KEY_REMEMBER = "remember_me";
    private static final String KEY_ROLE = "remembered_role";
    private static final String KEY_IDENTIFIER = "remembered_identifier";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_SIGN_IN_COUNT = "sign_in_count";

    private final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static Prefs get(Context context) { return new Prefs(context); }

    public boolean isRememberMe() { return sp.getBoolean(KEY_REMEMBER, false); }

    public void setRememberMe(boolean remember, String role, String identifier) {
        SharedPreferences.Editor e = sp.edit().putBoolean(KEY_REMEMBER, remember);
        if (remember) { e.putString(KEY_ROLE, role).putString(KEY_IDENTIFIER, identifier); }
        else { e.remove(KEY_ROLE).remove(KEY_IDENTIFIER); }
        e.apply();
    }

    public String rememberedRole() { return sp.getString(KEY_ROLE, null); }
    public String rememberedIdentifier() { return sp.getString(KEY_IDENTIFIER, null); }

    public String language() { return sp.getString(KEY_LANGUAGE, "en"); }
    public void setLanguage(String language) { sp.edit().putString(KEY_LANGUAGE, language).apply(); }

    public long signInCount() { return sp.getLong(KEY_SIGN_IN_COUNT, 0); }
    public long bumpSignInCount() {
        long next = signInCount() + 1;
        sp.edit().putLong(KEY_SIGN_IN_COUNT, next).apply();
        return next;
    }
}
```

- [ ] **Step 2: `LocaleHelper.java`**

```java
package com.maduka.rentmanager.util;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

public final class LocaleHelper {
    private LocaleHelper() {}

    public static Context wrap(Context base, String languageTag) {
        Locale locale = new Locale(languageTag);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
```

- [ ] **Step 3: Verify**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/util/Prefs.java app/src/main/java/com/maduka/rentmanager/util/LocaleHelper.java
git commit -m "feat: add SharedPreferences and locale-switching helpers"
```

---

### Task 7: Login screen

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/login/LoginActivity.java`
- Create: `app/src/main/res/layout/activity_login.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Layout spec** (captured verbatim from the canvas login screen, role = Admin/Super Admin variant shown; Tenant variant omits the verse block only):
- App mark: `@drawable/ic_maduka_mark` (real brand mark, already created — see Global Constraints) inside a rounded square badge (background `@color/md_accent_900` or similar dark tint, radius_lg) + "Maduka" (24sp, bold) + "Property management" (14sp, neutral-400) — centered header block. Do not create a new placeholder icon here.
- "Sign in as" label (12sp, neutral-500, uppercase, letter-spaced) above a 3-segment `TabLayout`-style row: **Super Admin / Admin / Tenant** (pill buttons, selected = accent-700 fill + accent text, unselected = surface + neutral-300 text).
- "Username, email or phone" — `TextInputLayout`/`EditText`, single line.
- "Password" — `TextInputLayout`/`EditText`, `inputType="textPassword"`.
- "Remember me" — `CheckBox`, checked by default per canvas.
- "Sign In" — full-width filled button, accent background, radius_md.
- "Forgot password? Contact admin" — centered text below the button, plain (non-clickable) `TextView`, neutral-400 with "Contact admin" in accent color.
- KJV verse block (Admin/Super Admin tabs only, hidden entirely when Tenant tab selected): centered italic quote (13sp, neutral-300) + reference line (11sp, neutral-500), directly under the Sign In / Forgot-password block.

**Interfaces:**
- Consumes: `AuthRepository` (Task 5), `Prefs`/`LocaleHelper` (Task 6), `VerseProvider` (Task 4).
- Produces: on successful sign-in, starts `MainActivity` (Task 8) passing the resolved `UserRole` as an intent extra `EXTRA_ROLE` (role's `name()` string) and finishes itself.

- [ ] **Step 1: `strings.xml` additions**

```xml
<string name="login_title">Maduka</string>
<string name="login_subtitle">Property management</string>
<string name="login_sign_in_as">Sign in as</string>
<string name="role_super_admin">Super Admin</string>
<string name="role_admin">Admin</string>
<string name="role_tenant">Tenant</string>
<string name="login_identifier_hint">Username, email or phone</string>
<string name="login_password_hint">Password</string>
<string name="login_remember_me">Remember me</string>
<string name="login_sign_in">Sign In</string>
<string name="login_forgot_password">Forgot password? Contact admin</string>
<string name="login_error_generic">Could not sign in — check your details and try again.</string>
```

- [ ] **Step 2: `activity_login.xml`**

Build a `ScrollView` > vertical `LinearLayout` (`gravity=center_horizontal`, padding `@dimen/space_8`) containing, in order: the icon+title+subtitle header block; the 3-segment role `RadioGroup` styled as pill toggle buttons (three `Button`s with a `ViewGroup` background selector driven by `LoginActivity` click listeners, since a plain `RadioGroup` doesn't give per-item pill styling for free — track selection in a `UserRole selectedRole` field); `TextInputLayout` (id `tilIdentifier`) wrapping `TextInputEditText` (id `etIdentifier`); `TextInputLayout` (id `tilPassword`) wrapping a password `TextInputEditText` (id `etPassword`); `CheckBox` (id `cbRememberMe`, checked="true"); `Button` (id `btnSignIn`); `TextView` (id `tvForgotPassword`); a `LinearLayout` (id `verseContainer`) holding two `TextView`s (id `tvVerseText`, `tvVerseReference`), `visibility="gone"` by default. Every text color references `@color/md_text`/`@color/md_accent`/neutral tokens from Task 2 — no hardcoded hex in the XML.

- [ ] **Step 3: `LoginActivity.java`**

```java
package com.maduka.rentmanager.ui.login;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.ui.shell.MainActivity;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;
import com.maduka.rentmanager.util.VerseProvider;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class LoginActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "extra_role";

    private final AuthRepository authRepository = new AuthRepository();
    private UserRole selectedRole = UserRole.ADMIN;

    private Button btnSuperAdmin, btnAdmin, btnTenant, btnSignIn;
    private TextInputEditText etIdentifier, etPassword;
    private CheckBox cbRememberMe;
    private View verseContainer;
    private TextView tvVerseText, tvVerseReference;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        btnSuperAdmin = findViewById(R.id.btnRoleSuperAdmin);
        btnAdmin = findViewById(R.id.btnRoleAdmin);
        btnTenant = findViewById(R.id.btnRoleTenant);
        etIdentifier = findViewById(R.id.etIdentifier);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        btnSignIn = findViewById(R.id.btnSignIn);
        verseContainer = findViewById(R.id.verseContainer);
        tvVerseText = findViewById(R.id.tvVerseText);
        tvVerseReference = findViewById(R.id.tvVerseReference);

        btnSuperAdmin.setOnClickListener(v -> selectRole(UserRole.SUPER_ADMIN));
        btnAdmin.setOnClickListener(v -> selectRole(UserRole.ADMIN));
        btnTenant.setOnClickListener(v -> selectRole(UserRole.TENANT));
        btnSignIn.setOnClickListener(v -> signIn());

        Prefs prefs = Prefs.get(this);
        if (prefs.isRememberMe() && prefs.rememberedIdentifier() != null) {
            etIdentifier.setText(prefs.rememberedIdentifier());
            selectRole(UserRole.valueOf(prefs.rememberedRole()));
        } else {
            selectRole(UserRole.ADMIN);
        }
    }

    private void selectRole(UserRole role) {
        selectedRole = role;
        btnSuperAdmin.setSelected(role == UserRole.SUPER_ADMIN);
        btnAdmin.setSelected(role == UserRole.ADMIN);
        btnTenant.setSelected(role == UserRole.TENANT);

        if (role == UserRole.TENANT) {
            verseContainer.setVisibility(View.GONE);
        } else {
            VerseProvider.Verse verse = VerseProvider.pickVerse(Prefs.get(this).signInCount());
            tvVerseText.setText("\u201C" + verse.text + "\u201D");
            tvVerseReference.setText("\u2014 " + verse.reference);
            verseContainer.setVisibility(View.VISIBLE);
        }
    }

    private void signIn() {
        String identifier = String.valueOf(etIdentifier.getText());
        String password = String.valueOf(etPassword.getText());
        btnSignIn.setEnabled(false);

        authRepository.signIn(selectedRole, identifier, password, new FirebaseManager.Callback<AuthRepository.AuthResult>() {
            @Override
            public void onSuccess(AuthRepository.AuthResult result) {
                Prefs prefs = Prefs.get(LoginActivity.this);
                prefs.setRememberMe(cbRememberMe.isChecked(), selectedRole.name(), identifier);
                prefs.bumpSignInCount();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra(EXTRA_ROLE, result.role.name());
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                btnSignIn.setEnabled(true);
                etPassword.setError(getString(R.string.login_error_generic));
            }
        });
    }
}
```

- [ ] **Step 4: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Cross-check `activity_login.xml`'s widget list and copy against the "Layout spec" bullets above — every string must come from `strings.xml`, no hardcoded copy.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/login app/src/main/res/layout/activity_login.xml app/src/main/res/values/strings.xml
git commit -m "feat: build the login screen (role tabs, identifier+password, rotating KJV verse)"
```

---

### Task 8: App shell — `MainActivity`, role-aware bottom navigation, top bar

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/shell/MainActivity.java`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/menu/bottom_nav_admin.xml`, `bottom_nav_super_admin.xml`, `bottom_nav_tenant.xml`
- Create: `app/src/main/res/drawable/ic_dashboard.xml`, `ic_properties.xml`, `ic_tenants.xml`, `ic_reports.xml`, `ic_notifications.xml`, `ic_users.xml`, `ic_payments.xml`, `ic_details.xml`, `ic_history.xml`, `ic_language.xml`, `ic_sign_out.xml` (Material Symbols outline vector drawables — nearest available equivalent to the canvas's Phosphor `ph-house`/`ph-buildings`/`ph-users`/`ph-chart-line`/`ph-bell`/`ph-shield-check`/`ph-user`/`ph-lock-key`/`ph-sign-out` glyphs)
- Create: `app/src/main/java/com/maduka/rentmanager/ui/common/ChangePasswordDialog.java` + `app/src/main/res/layout/dialog_change_password.xml`

**Interfaces:**
- Consumes: `AuthRepository` (Task 5), `LocaleHelper`/`Prefs` (Task 6). Reads `EXTRA_ROLE` from the launching `Intent` (Task 7).
- Produces: swaps in `AdminDashboardFragment`/`PropertiesFragment`/`TenantsFragment`/`ReportsFragment`/`AdminNotificationsFragment`/`UsersFragment`/`TenantDashboardFragment`/`TenantPaymentsFragment`/`TenantDetailsFragment`/`TenantHistoryFragment`/`TenantNotificationsFragment` (Tasks 9–17) into a `FragmentContainerView` (id `fragmentContainer`) based on `BottomNavigationView` selection + current role. Every fragment task below assumes `MainActivity` already exists and hosts it — do not have each fragment task create its own Activity.

- [ ] **Step 1: `activity_main.xml`**

`CoordinatorLayout` root containing: a top bar `LinearLayout` (id `topBar`, horizontal, `md_bg` background, bottom divider) with a language toggle `Button` (id `btnLanguage`, text "EN"/"SW"), a lock-key `ImageButton` (id `btnChangePassword`), a sign-out `ImageButton` (id `btnSignOut`) right-aligned; a `FragmentContainerView` (id `fragmentContainer`, `layout_weight=1`); a `BottomNavigationView` (id `bottomNav`, `app:itemIconTint`/`app:itemTextColor` using a color-state-list that maps `state_checked` to `@color/md_accent` and default to `@color/md_neutral_500`, background `@color/md_surface`).

- [ ] **Step 2: The three bottom-nav menu resources**, item ids `nav_dashboard`, `nav_properties`, `nav_tenants`, `nav_reports`, `nav_notifications`, `nav_users`, `nav_payments`, `nav_details`, `nav_history` (reused across the three menus as applicable — Admin uses dashboard/properties/tenants/reports/notifications; Super Admin swaps notifications for users; Tenant uses dashboard/payments/details/history/notifications).

- [ ] **Step 3: `MainActivity.java`**

```java
package com.maduka.rentmanager.ui.shell;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.ui.admin.dashboard.AdminDashboardFragment;
import com.maduka.rentmanager.ui.admin.notifications.AdminNotificationsFragment;
import com.maduka.rentmanager.ui.admin.properties.PropertiesFragment;
import com.maduka.rentmanager.ui.admin.reports.ReportsFragment;
import com.maduka.rentmanager.ui.admin.tenants.TenantsFragment;
import com.maduka.rentmanager.ui.common.ChangePasswordDialog;
import com.maduka.rentmanager.ui.login.LoginActivity;
import com.maduka.rentmanager.ui.superadmin.users.UsersFragment;
import com.maduka.rentmanager.ui.tenant.dashboard.TenantDashboardFragment;
import com.maduka.rentmanager.ui.tenant.details.TenantDetailsFragment;
import com.maduka.rentmanager.ui.tenant.history.TenantHistoryFragment;
import com.maduka.rentmanager.ui.tenant.notifications.TenantNotificationsFragment;
import com.maduka.rentmanager.ui.tenant.payments.TenantPaymentsFragment;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;
import android.widget.Button;
import android.widget.ImageButton;

public class MainActivity extends AppCompatActivity {
    private UserRole role;
    private BottomNavigationView bottomNav;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String roleName = getIntent().getStringExtra(LoginActivity.EXTRA_ROLE);
        role = roleName != null ? UserRole.valueOf(roleName) : UserRole.ADMIN;

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.inflateMenu(menuFor(role));
        bottomNav.setOnItemSelectedListener(item -> {
            showFragment(fragmentFor(item.getItemId()));
            return true;
        });
        showFragment(fragmentFor(bottomNav.getMenu().getItem(0).getItemId()));

        Button btnLanguage = findViewById(R.id.btnLanguage);
        Prefs prefs = Prefs.get(this);
        btnLanguage.setText(prefs.language().equals("sw") ? "SW" : "EN");
        btnLanguage.setOnClickListener(v -> {
            String next = prefs.language().equals("sw") ? "en" : "sw";
            prefs.setLanguage(next);
            recreate();
        });

        ImageButton btnChangePassword = findViewById(R.id.btnChangePassword);
        btnChangePassword.setOnClickListener(v -> new ChangePasswordDialog().show(getSupportFragmentManager(), "change_password"));

        ImageButton btnSignOut = findViewById(R.id.btnSignOut);
        btnSignOut.setOnClickListener(v -> {
            new AuthRepository().signOut();
            Prefs.get(this).setRememberMe(false, null, null);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private int menuFor(UserRole role) {
        switch (role) {
            case SUPER_ADMIN: return R.menu.bottom_nav_super_admin;
            case TENANT: return R.menu.bottom_nav_tenant;
            default: return R.menu.bottom_nav_admin;
        }
    }

    private Fragment fragmentFor(int itemId) {
        if (itemId == R.id.nav_dashboard) return role == UserRole.TENANT ? new TenantDashboardFragment() : new AdminDashboardFragment();
        if (itemId == R.id.nav_properties) return new PropertiesFragment();
        if (itemId == R.id.nav_tenants) return new TenantsFragment();
        if (itemId == R.id.nav_reports) return new ReportsFragment();
        if (itemId == R.id.nav_notifications) return role == UserRole.TENANT ? new TenantNotificationsFragment() : new AdminNotificationsFragment();
        if (itemId == R.id.nav_users) return new UsersFragment();
        if (itemId == R.id.nav_payments) return new TenantPaymentsFragment();
        if (itemId == R.id.nav_details) return new TenantDetailsFragment();
        if (itemId == R.id.nav_history) return new TenantHistoryFragment();
        throw new IllegalArgumentException("Unknown nav item: " + itemId);
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
```

- [ ] **Step 4: `ChangePasswordDialog.java`** — a `DialogFragment` with current/new/confirm password fields calling `AuthRepository.changePassword`, matching the original spec's password-change modal (current password, new password ≥ 6 chars, confirm match, success/error toast).

- [ ] **Step 5: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL once Tasks 9–17 stub out their fragments (this task can compile against not-yet-fully-implemented fragment classes as long as each has at least an empty `onCreateView` — sequence Task 8 *after* creating empty stubs for all eleven fragments, or implement Tasks 9–17 first and return to Task 8 last; either order is fine, note the dependency here explicitly).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/shell app/src/main/java/com/maduka/rentmanager/ui/common/ChangePasswordDialog.java app/src/main/res/layout/activity_main.xml app/src/main/res/layout/dialog_change_password.xml app/src/main/res/menu app/src/main/res/drawable
git commit -m "feat: add role-aware app shell (bottom nav, top bar, change password, sign out)"
```

---

### Task 9: Admin/Super Admin Dashboard — stats, payments table, Confirm/Reject, Record Payment

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/dashboard/AdminDashboardFragment.java`
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/dashboard/PaymentsAdapter.java`
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/dashboard/ConfirmRejectSheet.java`
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/dashboard/RecordPaymentSheet.java`
- Create: `app/src/main/res/layout/fragment_admin_dashboard.xml`, `item_payment_row.xml`, `sheet_confirm_reject.xml`, `sheet_record_payment.xml`
- Create: `app/src/main/java/com/maduka/rentmanager/ui/common/StatusBadgeView.java` (compound `TextView` subclass: takes a `StatusPresentation.Tone` and renders the pill using Task 4's color mapping — every screen's status pill reuses this one view, do not re-implement pill styling per screen)

**Layout spec** (from the live Dashboard walkthrough, Admin persona "Elias"/Super Admin persona "Mama"):
- Header row: screen title "Dashboard" + "Payments · N records" subtitle, right-aligned language/menu icons (already in the shared top bar from Task 8 — do not duplicate here).
- KJV verse strip (Admin/Super Admin only — reuse `VerseProvider`, same rotation seed as login).
- "Welcome, {firstName}" (18sp, semibold).
- 2×2 stat card grid: **Tenants** (count + "N / 10 occupied"), **Revenue** (formatted "2,175k" style — thousands with a "k" suffix — + "TZS · confirmed"), **Pending** (count + "to confirm"), **Overdue** (count + total TZS amount).
- "Next due" banner: tenant name · shop id, due date · amount, "N days" countdown chip.
- "Payments" section header + "Refresh" (calls `observePayments` again / `SwipeRefreshLayout`) + filter chip row: All / Pending / Confirmed / Rejected.
- `RecyclerView` (id `rvPayments`) of payment rows: date (dd/MM), tenant name, "{shopId} · {N month(s)} · {recordedByFirstName}" subtitle, amount (TZS, thousands-separated), `StatusBadgeView`. Row `onClick`: if `status == PENDING`, open `ConfirmRejectSheet`; otherwise no-op (canvas: "Tap a row for details · pending rows open the confirmation panel" — a details-only view for non-pending rows is out of scope for v1, note this explicitly rather than silently building it).
- "Record payment" full-width button pinned above the bottom nav → opens `RecordPaymentSheet`.

`ConfirmRejectSheet` (BottomSheetDialogFragment) fields, in order: "Confirm payment" title + "Recorded {date} · {recordedByName}" subtitle; read-only rows Tenant/Shop-Unit/Amount/Months covered/Date/Payment method/Receipt-reference/Recorded by/Status; two buttons **Confirm** / **Reject**. Tapping Reject expands the sheet to show a radio list — Duplicate record / Incorrect amount / Incorrect date / Wrong tenant / No receipt / Other — plus **Cancel** / **Send rejection**. If `!payment.canBeActionedBy(currentUid)`, hide Confirm/Reject entirely and show a plain "You recorded this payment — another admin must confirm it." notice instead (two-person rule, Global Constraints).

`RecordPaymentSheet` (BottomSheetDialogFragment) fields, in order: "Record payment" title + "Recorded by {currentUserName}" subtitle; Tenant dropdown (`Spinner`/`AutoCompleteTextView`, options "{name} · {shopId} · TZS {rent}"); Amount (TZS) number field; Number of months field; Payment date (`DatePickerDialog` trigger field, defaults to today); Payment method dropdown (M-Pesa / Bank transfer / Cash / Cheque); Receipt/reference text field; Notes text field; a live summary block ("Tenant", "Months covered: N × TZS rent", "Total: TZS ...", "Next due date: ...", computed via `DateCalculator.addMonths`); "This record carries your name as the recorder; another admin confirms it." notice; **Save payment** / **Cancel**.

**Interfaces:**
- Consumes: `PaymentRepository`, `TenantRepository`, `ShopRepository` (Task 5), `StatusPresentation`/`DateCalculator`/`VerseProvider` (Task 4), `AuthRepository.currentUid()`.
- Produces: `StatusBadgeView` (public API: `setTone(StatusPresentation.Tone tone, String label)`) is reused by every remaining screen task (10–17) — build it here first since Dashboard needs it first, and do not redefine it elsewhere.

- [ ] **Step 1: `StatusBadgeView.java`**

```java
package com.maduka.rentmanager.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.util.StatusPresentation;

public class StatusBadgeView extends AppCompatTextView {
    public StatusBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        int pad = getResources().getDimensionPixelSize(R.dimen.space_3);
        setPadding(pad, pad / 2, pad, pad / 2);
        setTextSize(12);
    }

    public void setTone(StatusPresentation.Tone tone, String label) {
        setText(label);
        setTextColor(ContextCompat.getColor(getContext(), StatusPresentation.fgColorRes(tone)));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(getResources().getDimension(R.dimen.radius_sm));
        bg.setColor(ContextCompat.getColor(getContext(), StatusPresentation.bgColorRes(tone)));
        bg.setStroke(2, ContextCompat.getColor(getContext(), StatusPresentation.borderColorRes(tone)));
        setBackground(bg);
    }
}
```

- [ ] **Step 2–5:** Build `fragment_admin_dashboard.xml`/`AdminDashboardFragment.java`, `item_payment_row.xml`/`PaymentsAdapter.java`, `sheet_confirm_reject.xml`/`ConfirmRejectSheet.java`, `sheet_record_payment.xml`/`RecordPaymentSheet.java` per the Layout spec above, wiring exactly the repository methods named in Task 5 (`observePayments`, `observeTenants`, `observeShops`, `recordPayment`, `confirmPayment`, `rejectPayment`) — no direct `FirebaseDatabase` calls in this package.

- [ ] **Step 6: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Manually diff every string literal against the Layout spec's captured copy.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/admin/dashboard app/src/main/java/com/maduka/rentmanager/ui/common/StatusBadgeView.java app/src/main/res/layout/fragment_admin_dashboard.xml app/src/main/res/layout/item_payment_row.xml app/src/main/res/layout/sheet_confirm_reject.xml app/src/main/res/layout/sheet_record_payment.xml
git commit -m "feat: build Admin/Super Admin dashboard with record/confirm/reject payment flow"
```

---

### Task 10: Properties screen

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/properties/PropertiesFragment.java`, `ShopAdapter.java`
- Create: `app/src/main/res/layout/fragment_properties.xml`, `item_shop_card.xml`

**Layout spec:** Header "Properties" + "N units · Kariakoo". Stat row: Units (10, "A1 – A10"), Occupied (N, "N0% occupancy"), Empty (N, list of empty shop ids), Overdue (N, list of overdue shop ids). Filter chips: All / Occupied / Empty / Overdue. `RecyclerView` of shop cards, each: "Shop {id}" + `StatusBadgeView` (Yupo/Inangoja/Hayupo tone, or Empty tone with no tenant); when occupied: tenant name · phone, "TZS {rent} / per month", Last payment / Due date / Move-in / Days-left-or-overdue rows; when the shop's tenant is overdue (`DateCalculator.isOverdue`) and `presenceStatus == YUPO`: inline prompt "Paid months have run out. Is this tenant still here?" with **Yupo** / **Hayupo** buttons wired to `TenantRepository.setPresence`; when empty: "No tenant assigned" / "— no rent set".

**Interfaces:**
- Consumes: `ShopRepository.observeShops`, `TenantRepository.observeTenants`/`.setPresence`, `StatusBadgeView`, `DateCalculator`.

- [ ] **Step 1–3:** Implement per spec above; join `Shop` + `Tenant` streams client-side by `shop.getTenantUid()`/`tenant.getShopId()`.
- [ ] **Step 4: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/admin/properties app/src/main/res/layout/fragment_properties.xml app/src/main/res/layout/item_shop_card.xml
git commit -m "feat: build Properties screen with Yupo/Hayupo presence prompt"
```

---

### Task 11: Tenants screen (Admin read-only, Super Admin + registration)

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/tenants/TenantsFragment.java`, `TenantAdapter.java`, `RegisterTenantSheet.java`
- Create: `app/src/main/res/layout/fragment_tenants.xml`, `item_tenant_card.xml`, `sheet_register_tenant.xml`

**Layout spec:** Header "Tenants" + "N · M overdue". `RecyclerView` of tenant cards: name + `StatusBadgeView` (Yupo/Inangoja/Hayupo), "{shopId} · {phone}" subtitle, "TZS {rent} / per month", Last payment / Due date / Days rows, "Shop / Unit: {id}"; overdue+Yupo cards show the same Yupo/Hayupo presence prompt as Task 10 (identical copy and wiring — factor the prompt view into a small reusable `include` layout if convenient, but do not duplicate the `TenantRepository.setPresence` call site logic). Admin role: list ends with plain text "The super admin registers new tenants." (no button). Super Admin role: list ends with **"+ Register new tenant"** button opening `RegisterTenantSheet`.

`RegisterTenantSheet` fields, in order: "Register new tenant" + "Super admin only" subtitle; Full name*; Phone number*; Email; Shop/Unit* dropdown (populated from `ShopRepository.observeShops` filtered to `!occupied`, showing e.g. "Shop A7 · Empty"); Monthly rent (TZS)*; Move-in date; Emergency contact name; Emergency contact phone; Notes; "Tenants cannot register themselves. The super admin issues the first password." notice; **Register tenant** / **Cancel**. On submit: create a Firebase Auth user (via `AuthRepository`'s underlying `FirebaseAuth` — add `AuthRepository.createTenantAccount(String authEmail, String tempPassword, Callback<String uid>)` in this task, since Task 5 didn't need account *creation*, only sign-in), write the `Tenant` record, write `shops/{id}.occupied=true` + `.tenantUid`, and write the three `login_index/tenants/{sanitizedUsername|email|phone}` keys pointing at `authEmail`.

**Interfaces:**
- Consumes: `TenantRepository`, `ShopRepository`, `StatusBadgeView`.
- Produces: `AuthRepository.createTenantAccount(...)` (new method added in this task, mirrored by `createAdminAccount` in Task 14) — both are the only two places outside `cli/` allowed to call Firebase Auth's account-creation API.

- [ ] **Step 1–4:** Implement per spec. Extend `AuthRepository` with:

```java
public void createAccount(String authEmail, String tempPassword, FirebaseManager.Callback<String> cb) {
    FirebaseAuth.getInstance().createUserWithEmailAndPassword(authEmail, tempPassword)
            .addOnSuccessListener(result -> cb.onSuccess(result.getUser().getUid()))
            .addOnFailureListener(e -> cb.onError(e.getMessage()));
}
```
- [ ] **Step 5: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/admin/tenants app/src/main/res/layout/fragment_tenants.xml app/src/main/res/layout/item_tenant_card.xml app/src/main/res/layout/sheet_register_tenant.xml app/src/main/java/com/maduka/rentmanager/data/AuthRepository.java
git commit -m "feat: build Tenants screen and Super Admin tenant registration"
```

---

### Task 12: Reports screen (with custom bar/donut views)

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/reports/ReportsFragment.java`, `BarChartView.java`, `DonutView.java`
- Create: `app/src/main/res/layout/fragment_reports.xml`

**Layout spec:** Header "Reports" + "View only · no download". Report-type chip row: Payment summary / Tenant report / Overdue / Collection / Custom — Task ships the **Payment summary** view fully (matches the captured walkthrough); the other four chips switch `reportType` state but Custom/Collection/Tenant-report/Overdue bodies may render a simple "Coming soon" placeholder text rather than fabricated content — do not invent numbers for report types never seen in the canvas walkthrough. Payment summary body: Revenue cards "This month" / "This year" (TZS); "Confirmed" count; "Average payment" (TZS); `BarChartView` fed 6 months of `{TZS millions}` bars labeled Apr–Sep; `DonutView` showing "68% PAID" with legend rows Paid/Pending/Overdue counts.

**Interfaces:**
- Consumes: `PaymentRepository.observePayments` (derive month buckets/averages client-side with plain `Calendar` grouping — no new repository method needed).
- Produces: `BarChartView(Context, AttributeSet)` public API `setBars(List<Pair<String label, float value>>)`; `DonutView` public API `setSegments(List<Pair<String label, float value>> segments, float centerPercent)`. Both draw with `Canvas`/`Paint` only, no chart library dependency.

- [ ] **Step 1–4:** Implement per spec.
- [ ] **Step 5: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/admin/reports app/src/main/res/layout/fragment_reports.xml
git commit -m "feat: build Reports screen with custom bar/donut views"
```

---

### Task 13: Admin/Super Admin Notifications screen

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/admin/notifications/AdminNotificationsFragment.java`, `NotificationAdapter.java`
- Create: `app/src/main/res/layout/fragment_admin_notifications.xml`, `item_notification.xml`

**Layout spec:** Header "Notifications" + "N unread". Filter chips: All / Overdue / Due today / Due soon / System. List rows: title ("{Tenant} — {Reason}" for overdue/due-today/due-soon/payment rows, plain title for system rows), body message, relative time ("2 hours ago" / "Just now" / "Yesterday 09:00"), unread rows visually distinct (accent left-border or bold title), overdue/due-today rows carry a "Contact tenant" text action. "Mark all as read" button. Footer note: "Overdue reminders are sent three times a day — 08:00, 14:00 and 18:00. The super admin sets the times." (static copy; the actual times are not yet super-admin-configurable in v1 — this is documented as a known gap in Task 18, not silently hardcoded-and-hidden).

**Interfaces:**
- Consumes: `NotificationRepository.observeFor(currentUid, ...)`, `.markRead`, `.markAllRead`.

- [ ] **Step 1–3:** Implement per spec; relative-time formatting is a small local helper (`"N hours ago"` etc. via simple millis-diff buckets — no new dependency needed).
- [ ] **Step 4: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/admin/notifications app/src/main/res/layout/fragment_admin_notifications.xml app/src/main/res/layout/item_notification.xml
git commit -m "feat: build Admin/Super Admin notifications screen"
```

---

### Task 14: Super Admin Users screen (list + register admin)

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/superadmin/users/UsersFragment.java`, `UserAdapter.java`, `RegisterAdminSheet.java`
- Create: `app/src/main/res/layout/fragment_users.xml`, `item_user_card.xml`, `sheet_register_admin.xml`

**Layout spec:** Header "Users" + "N · admins". Filter chips: All / Super Admin / Admin. List rows: name + `StatusBadgeView` (Yupo=active/Hayupo=disabled, reusing the presence tone mapping), "@{username} · {phone}" subtitle, role label, "{shopRangeOrAllShops}" (e.g. "A1 – A5" or "All shops" for super admin), Last login, Registered date. "+ Register new admin" button → `RegisterAdminSheet`.

`RegisterAdminSheet` fields, in order: "Register new admin" + "Super admin only"; Full name*; Email*; Phone number*; Username*; "Temporary password — Auto-generated — hand it to the new admin." (generate an 8-char random alphanumeric string client-side, display it read-only, do not let the super admin type it); Properties assigned* — multi-select checkboxes A1..A10; Status Yupo/Hayupo radio (default Yupo); "The user must change this password on first login." notice; **Register admin** / **Cancel**. On submit: `AuthRepository.createAccount`, write `AdminUser`, write the three `login_index/admins/...` keys, and set a `mustChangePassword=true` flag on the admin record (surfaced later, if ever, by prompting `ChangePasswordDialog` on first `MainActivity` load — out of scope to force in v1, note as a follow-up rather than half-building it).

**Interfaces:**
- Consumes: `UserRepository.observeAdmins`, `AuthRepository.createAccount` (from Task 11).

- [ ] **Step 1–4:** Implement per spec.
- [ ] **Step 5: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/superadmin app/src/main/res/layout/fragment_users.xml app/src/main/res/layout/item_user_card.xml app/src/main/res/layout/sheet_register_admin.xml
git commit -m "feat: build Super Admin Users screen and admin registration"
```

---

### Task 15: Tenant Dashboard + Details

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/tenant/dashboard/TenantDashboardFragment.java`
- Create: `app/src/main/java/com/maduka/rentmanager/ui/tenant/details/TenantDetailsFragment.java`
- Create: `app/src/main/res/layout/fragment_tenant_dashboard.xml`, `fragment_tenant_details.xml`

**Layout spec — Dashboard:** Header "Dashboard" + "{shopId} · Kariakoo". "Welcome, {firstName}". Stat cards: Total paid (TZS, all-time), Months covered, Last payment (date + TZS), Due date (date + "N days"). `StatusBadgeView` (Yupo) + "N days until {dueDate}" line. Three plain-text action rows: "View payment history" / "View shop details" / "Contact admin" — no verse block (Tenant side never shows KJV).

**Layout spec — Details:** Header "Details" + "Shop {id}". SHOP section: Shop/Unit, Location ("Kariakoo, Dar es Salaam" — static, no per-shop address field exists in the schema, do not invent one), Admin (name), Phone number, Email. LEASE section: Monthly rent, Move-in date, Status badge. PAYMENT SCHEDULE section: Due day of month, Last payment, Due date, Months covered. "Contact admin" action.

**Interfaces:**
- Consumes: `TenantRepository.observeTenant(currentUid, ...)`, `UserRepository` (to resolve the tenant's assigned admin by matching `shopId` against an admin's `shopsAssigned` — add `UserRepository.observeAdminForShop(String shopId, Callback)` in this task).

- [ ] **Step 1–4:** Implement per spec; add:
```java
public void observeAdminForShop(String shopId, FirebaseManager.Callback<AdminUser> cb) {
    observeAdmins(new AdminsListener() {
        @Override public void onAdmins(List<AdminUser> admins) {
            for (AdminUser a : admins) {
                if (a.getShopsAssigned().contains(shopId)) { cb.onSuccess(a); return; }
            }
            cb.onError("No admin assigned to " + shopId);
        }
        @Override public void onError(String message) { cb.onError(message); }
    });
}
```
- [ ] **Step 5: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/tenant/dashboard app/src/main/java/com/maduka/rentmanager/ui/tenant/details app/src/main/res/layout/fragment_tenant_dashboard.xml app/src/main/res/layout/fragment_tenant_details.xml app/src/main/java/com/maduka/rentmanager/data/UserRepository.java
git commit -m "feat: build Tenant Dashboard and Details screens"
```

---

### Task 16: Tenant Payments + History

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/tenant/payments/TenantPaymentsFragment.java`
- Create: `app/src/main/java/com/maduka/rentmanager/ui/tenant/history/TenantHistoryFragment.java`
- Create: `app/src/main/res/layout/fragment_tenant_payments.xml`, `fragment_tenant_history.xml`

**Layout spec — Payments:** Header "My payments" + "{shopId} · TZS {rent} per month". Status filter: All / Confirmed / Rejected. Table rows: date, "{recordedByName} · {months} month(s) · {paymentMethod}", amount, `StatusBadgeView`. Summary block: Total paid, Months covered, Average payment.

**Layout spec — History:** Header "History" + "All transactions". Stat cards: Transactions (count, "all time"), Total paid (TZS), Average (TZS), Months covered ("N / N paid vs expected"). Transaction list: "{paymentMethod} · {months} month(s)" title, amount, "{date} · {receiptRef} · recorded by {recordedByName} · {status}" detail line.

**Interfaces:**
- Consumes: `PaymentRepository.observePayments` filtered client-side to `payment.getTenantUid() == currentUid`.

- [ ] **Step 1–4:** Implement per spec.
- [ ] **Step 5: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/tenant/payments app/src/main/java/com/maduka/rentmanager/ui/tenant/history app/src/main/res/layout/fragment_tenant_payments.xml app/src/main/res/layout/fragment_tenant_history.xml
git commit -m "feat: build Tenant Payments and History screens"
```

---

### Task 17: Tenant Notifications

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/ui/tenant/notifications/TenantNotificationsFragment.java`
- Create: `app/src/main/res/layout/fragment_tenant_notifications.xml` (reuse `item_notification.xml` from Task 13)

**Layout spec:** Header "Notifications" + "N unread". Filter chips: All / Overdue / Due soon / Confirmed / System. Same row shape as Task 13's `item_notification.xml` (reuse the adapter's view holder, just a different data source). "Mark all as read". Same footer note about 08:00/14:00/18:00 reminders.

**Interfaces:**
- Consumes: `NotificationRepository.observeFor(currentUid, ...)` (identical call shape to Task 13, different fragment).

- [ ] **Step 1–3:** Implement, reusing `NotificationAdapter` from Task 13 (move it to `ui/common/` if both packages need it — do this refactor now rather than duplicating the adapter class).
- [ ] **Step 4: Verify** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/ui/tenant/notifications app/src/main/res/layout/fragment_tenant_notifications.xml
git commit -m "feat: build Tenant notifications screen, share NotificationAdapter"
```

---

### Task 18: Notification delivery — channels, 3×/day overdue check, in-app listener

**Files:**
- Create: `app/src/main/java/com/maduka/rentmanager/notifications/NotificationHelper.java`
- Create: `app/src/main/java/com/maduka/rentmanager/notifications/OverdueCheckReceiver.java`
- Create: `app/src/main/java/com/maduka/rentmanager/notifications/NotificationScheduler.java`
- Modify: `app/src/main/java/com/maduka/rentmanager/MadukaApp.java` (call `NotificationScheduler.ensureScheduled(this)` in `onCreate`)

**Interfaces:**
- Consumes: `PaymentRepository`, `TenantRepository`, `NotificationRepository`, `DateCalculator`.
- Produces: local notification channel `"overdue_reminders"` used by both the scheduled check and any in-app Firebase-listener-triggered notification.

**Known limitation to document, not silently skip:** there is no Cloud Functions backend in this project's scope, so a device that is fully killed (not just backgrounded) will not receive a push the moment data changes elsewhere — delivery here relies on (a) `AlarmManager` waking the app 3×/day to run the overdue scan locally, and (b) a `FirebaseDatabase` listener posting a local notification when the app is running/backgrounded and the `notifications/{uid}` node gets a new child. True always-on push (e.g. a payment confirmed while the tenant's phone is off) needs a Cloud Function + FCM, which is out of scope here — call this out in the final PR/summary rather than claiming full push coverage.

- [ ] **Step 1: `NotificationHelper.java`**

```java
package com.maduka.rentmanager.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.maduka.rentmanager.R;

public final class NotificationHelper {
    public static final String CHANNEL_ID = "overdue_reminders";
    private NotificationHelper() {}

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Rent reminders", NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public static void show(Context context, int notificationId, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_maduka_mark)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true);
        androidx.core.app.NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }
}
```

- [ ] **Step 2: `OverdueCheckReceiver.java`** — on each of the 3 daily alarms, reads `tenants` once, finds `presenceStatus == YUPO && isOverdue(dueDate, now)`, writes an `AppNotification` (type `TYPE_OVERDUE`) to that tenant's assigned admin(s) (via `UserRepository.observeAdminForShop`, Task 15) and to the tenant themself, then calls `NotificationHelper.show` for whichever of those two users is the current signed-in device (so a phone that's open sees the banner immediately; the DB write is what the *other* party's next app-open/listener picks up).

```java
package com.maduka.rentmanager.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.maduka.rentmanager.data.NotificationRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.AppNotification;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.DateCalculator;
import java.util.List;

public class OverdueCheckReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long now = System.currentTimeMillis();
        NotificationRepository notifications = new NotificationRepository();
        new TenantRepository().observeTenants(new TenantRepository.TenantsListener() {
            @Override
            public void onTenants(List<Tenant> tenants) {
                for (Tenant t : tenants) {
                    if (t.getPresenceStatus() == PresenceStatus.YUPO && DateCalculator.isOverdue(t.getDueDate(), now)) {
                        AppNotification n = new AppNotification();
                        n.setUserUid(t.getUid());
                        n.setType(AppNotification.TYPE_OVERDUE);
                        n.setTitle("Kodi ni Hayupo (Overdue)");
                        n.setRelatedTenantUid(t.getUid());
                        n.setMessage("TZS " + t.getMonthlyRent() + " was due "
                                + DateCalculator.formatDdMmYyyy(t.getDueDate()) + ".");
                        notifications.push(n);
                        NotificationHelper.show(context, t.getUid().hashCode(), n.getTitle(), n.getMessage());
                    }
                }
            }
            @Override public void onError(String message) { /* silent retry at next scheduled fire */ }
        });
        NotificationScheduler.scheduleNext(context);
    }
}
```

- [ ] **Step 3: `NotificationScheduler.java`** — schedules the next of the three daily times (08:00/14:00/18:00) via `AlarmManager.setExactAndAllowWhileIdle`, re-arming itself from inside `OverdueCheckReceiver` (Step 2's last line) so the chain continues indefinitely without needing `BOOT_COMPLETED` handling beyond a call from `MadukaApp.onCreate`.

```java
package com.maduka.rentmanager.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

public final class NotificationScheduler {
    private static final int[] HOURS = {8, 14, 18};
    private NotificationScheduler() {}

    public static void ensureScheduled(Context context) {
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (int hour : HOURS) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
            best = Math.min(best, c.getTimeInMillis());
        }

        Intent intent = new Intent(context, OverdueCheckReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pendingIntent);
    }
}
```

- [ ] **Step 4: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maduka/rentmanager/notifications app/src/main/java/com/maduka/rentmanager/MadukaApp.java
git commit -m "feat: schedule 3x-daily overdue notification checks via AlarmManager"
```

---

### Task 19: Kiswahili localization + language toggle wiring

**Files:**
- Create: `app/src/main/res/values-sw/strings.xml`

**Interfaces:**
- Consumes: every `strings.xml` key introduced in Tasks 7–17 (this task must run last among the UI tasks, once every string key exists).

- [ ] **Step 1:** Enumerate every string resource added across Tasks 7–17 (`grep -o 'name="[a-z_0-9]*"' app/src/main/res/values/strings.xml`) and provide a `values-sw/strings.xml` translation for each key, reusing the canvas's own fixed vocabulary from Global Constraints verbatim (Yupo, Hayupo, Inangoja, Imethibitishwa, Imekataliwa, Dashibodi for Dashboard, Mali for Properties, Wakodishaji for Tenants, Ripoti for Reports, Arifa for Notifications, Watumiaji for Users, Malipo for Payments, Maelezo for Details, Historia for History) plus natural Swahili for every other captured string (do not machine-translate the fixed vocabulary words differently than the canvas already fixed them).
- [ ] **Step 2: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, and `aapt2`/lint raises no "missing translation" warnings — every key in `values/strings.xml` must have a `values-sw` counterpart.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values-sw/strings.xml
git commit -m "feat: add Kiswahili translations for every app string"
```

---

### Task 20: Firebase Realtime Database security rules

**Files:**
- Create: `rules/database.rules.json`

**Interfaces:**
- Produces: the exact JSON the user pastes into Firebase Console → Realtime Database → Rules (this repo cannot push rules itself without a Firebase CLI login token, which is not available in this environment — hand the file to the user with that instruction rather than silently skipping deployment).

- [ ] **Step 1: Write `rules/database.rules.json`**

```json
{
  "rules": {
    "login_index": {
      ".read": true,
      ".write": "auth != null && (root.child('super_admins').child(auth.uid).exists())"
    },
    "super_admins": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('super_admins').child(auth.uid).exists()"
    },
    "admins": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('super_admins').child(auth.uid).exists()",
      "$adminUid": {
        ".write": "auth != null && (auth.uid == $adminUid || root.child('super_admins').child(auth.uid).exists())"
      }
    },
    "shops": {
      ".read": "auth != null",
      ".write": "auth != null && (root.child('admins').child(auth.uid).exists() || root.child('super_admins').child(auth.uid).exists())"
    },
    "tenants": {
      ".read": "auth != null",
      ".write": "auth != null && (root.child('admins').child(auth.uid).exists() || root.child('super_admins').child(auth.uid).exists())",
      "$tenantUid": {
        ".write": "auth != null && (auth.uid == $tenantUid || root.child('admins').child(auth.uid).exists() || root.child('super_admins').child(auth.uid).exists())"
      }
    },
    "payments": {
      ".read": "auth != null",
      "$paymentId": {
        ".write": "auth != null && (root.child('admins').child(auth.uid).exists() || root.child('super_admins').child(auth.uid).exists()) && (!data.exists() || newData.child('recordedByUid').val() != auth.uid || data.child('status').val() == newData.child('status').val())"
      }
    },
    "notifications": {
      "$userUid": {
        ".read": "auth != null && auth.uid == $userUid",
        ".write": "auth != null && (auth.uid == $userUid || root.child('admins').child(auth.uid).exists() || root.child('super_admins').child(auth.uid).exists())"
      }
    }
  }
}
```

The `payments/$paymentId` rule is the server-side half of the two-person rule: a write that changes `status` while `recordedByUid` on the existing record equals the writer's own `auth.uid` is rejected (the boolean short-circuits true for brand-new records and for writes that don't touch `status`, e.g. the initial optimistic tenant-field write bundled into the same `updateChildren` call in `PaymentRepository.recordPayment`).

- [ ] **Step 2: No automated verify possible from this repo** — hand `rules/database.rules.json` to the user with the instruction: "Paste this into Firebase Console → your `rent-manager-116d8` project → Realtime Database → Rules → Publish." Do not mark this task done until the user confirms they've published it, since the app's writes will be rejected by Firebase's default locked-down rules until then.
- [ ] **Step 3: Commit**

```bash
git add rules/database.rules.json
git commit -m "docs: add Firebase Realtime Database security rules for the two-person payment rule"
```

---

### Task 21: `cli` module — Super Admin / Admin / Tenant registration tool

**Files:**
- Create: `cli/build.gradle.kts`
- Modify: `settings.gradle.kts` (`include(":cli")`)
- Create: `cli/src/main/java/com/maduka/rentmanager/cli/RegisterCli.java`
- Create: `cli/.gitignore` (ignore `service-account.json`)

**Interfaces:**
- Consumes: a Firebase service-account JSON key the **user** must download themselves from Firebase Console → Project Settings → Service Accounts → "Generate new private key" (this repo/assistant cannot generate that credential) and place at `cli/service-account.json`.
- Produces: a runnable `java -jar` (or `./gradlew :cli:run --args=...`) tool with subcommands `register-superadmin`, `register-admin`, `register-tenant`, `reset-password`, `list-users` — the only place in the whole system, besides the in-app Super Admin forms (Tasks 11/14), allowed to create accounts.

- [ ] **Step 1: `cli/build.gradle.kts`**

```kotlin
plugins {
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("com.google.firebase:firebase-admin:9.4.3")
}

application {
    mainClass.set("com.maduka.rentmanager.cli.RegisterCli")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
```

- [ ] **Step 2: `settings.gradle.kts`** — add `include(":cli")` alongside the existing `:app` include.

- [ ] **Step 3: `RegisterCli.java`** — a plain `main(String[] args)` that: loads `service-account.json` via `GoogleCredentials.fromStream`, initializes `FirebaseApp`/`FirebaseAuth`/`FirebaseDatabase` (Admin SDK, which bypasses the rules from Task 20 by design — this is the intended bootstrap path for the very first super admin, since `rules/database.rules.json` requires an existing super admin to write new `admins`/`login_index` entries), dispatches on `args[0]`:
  - `register-superadmin --username= --email= --phone= --password=` → `FirebaseAuth.createUser`, write `super_admins/{uid}`, write the three `login_index/super_admins/*` keys.
  - `register-admin --name= --email= --phone= --username= --shops=A1,A2 [--password=]` (auto-generates an 8-char temp password if omitted, printed to stdout) → same pattern under `admins/`.
  - `register-tenant --name= --phone= --shop=A7 --rent= [--email=] [--password=]` → validates the shop is currently `occupied=false` before writing, same pattern under `tenants/`, also sets `shops/{id}.occupied=true`/`.tenantUid`.
  - `reset-password --uid= --password=` → `FirebaseAuth.updateUser(uid, new UserRecord.UpdateRequest().setPassword(...))`.
  - `list-users --role=admin|tenant|super_admin` → prints a table from the corresponding RTDB node.

  Print `Usage: ...` and exit 1 for an unrecognized/missing subcommand rather than silently no-op-ing.

- [ ] **Step 4: Verify**

Run: `./gradlew :cli:build`
Expected: BUILD SUCCESSFUL (this only checks compilation — running any subcommand for real requires the user's own `service-account.json`, which this plan cannot supply; do not attempt to fabricate one).

- [ ] **Step 5: Commit**

```bash
git add cli settings.gradle.kts
git commit -m "feat: add cli module for Firebase Admin SDK user registration"
```

---

### Task 22: Final polish and release build

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (`app_name` = "Maduka")
- Modify: `app/build.gradle.kts` (confirm `versionName`/`versionCode` before shipping)

- [ ] **Step 1:** Full-project build check.

Run: `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :cli:build`
Expected: BUILD SUCCESSFUL and all of `DateCalculatorTest`, `LoginKeyUtilTest`, `VerseProviderTest`, `PaymentRecordTest` pass.

- [ ] **Step 2:** Lint pass.

Run: `./gradlew :app:lintDebug`
Expected: no new errors introduced beyond the stock template's baseline (review `app/build/reports/lint-results-debug.html` if it fails).

- [ ] **Step 3:** Manual spec-conformance pass — re-open the live canvas (`http://localhost:4173/maduka.html`, already running from this planning session's `maduka-design-preview` launch config) side-by-side with a debug APK install and confirm, per role, that every string/flow in Tasks 7–17's Layout specs is present. Record any deltas as follow-up notes rather than silently shipping a mismatch.
- [ ] **Step 4:** `./gradlew :app:assembleRelease` once the user confirms a signing config (this plan does not create a keystore — ask the user for one or to generate one via `keytool`, do not invent release-signing credentials).
- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: final polish pass before release build"
```

---

## Sequencing note

Tasks 1–6 are strictly ordered (foundation → tokens → models → utils → repositories → prefs). Task 7 (Login) can start once Task 6 is done. Task 8 (shell) needs *stub* versions of every fragment in Tasks 9/10/11/12/13/14/15/16/17 to compile its `fragmentFor()` switch — either build Task 8 last among the "screen" tasks, or create one-line placeholder Fragments up front and let Tasks 9–17 fill them in (recommended if using subagent-driven-development, since it lets Tasks 9–17 run in parallel once Task 8's stubs exist). Tasks 18–22 depend on the full screen set being in place. Task 21 (`cli`) has no dependency on Tasks 7–19 and can run any time after Task 3 (needs the model field names for parity, though it uses its own Admin-SDK-side POJOs rather than importing `:app`'s classes, since `:cli` is a separate `application` module — do not add an inter-module dependency just to share the four model classes, duplication here is cheaper than coupling a server tool to an Android module).
