# Tire & Wheel Visualizer — Implementation Plan

**Status:** Phases 1, 3 and 5 complete. Phase 6 next. **Nothing has been run on a real device yet** — that is the outstanding gap.
**Last updated:** 2026-08-01

Goal: a mobile app where a user picks their vehicle, enters or picks a tire
size, and sees it composited onto **a photo of their own car** at the correct
diameter and sidewall — so they can work out what size to shop for before
buying. Offset, wheel catalogs, and multi-angle views are deliberately out of
v1 (decisions 10 and 11).

---

## 1. Locked decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Platform / stack | React Native + Expo, iOS + Android (+ web, inherited) |
| 2 | Visualization for v1 | 2D compositing onto **user-submitted photos**. No licensed vehicle imagery, no 3D models. |
| 3 | Fitment data budget | TBD — options priced in §6 |
| 4 | Vehicle coverage for v1 | Only vehicles already in Maintenance Tracker |
| 5 | v1 scope | Visualizer + affiliate buy links. **No accounts, no sign-in, no user data on any server.** |
| 6 | Wheels vs. tires | Wheel *models* + tire *sizes*. Tire brand/sidewall detail deferred. |
| 7 | "Rotate" view | **Multi-angle photo capture** + swipe, not a 3D model (§4b) |
| 8 | App structure | **One app, tabbed shell.** Wheels is a peer tab to Maintenance over a shared garage, reached from a new Home hub (§4c). |
| 9 | User data | **Fully local.** Photos, builds, calibration, and garage all live in device storage. Nothing about the user is uploaded, ever (§4d). |
| 10 | v1 view | **Profile (side-on), tire size only — offset not shown.** The goal is helping people work out what size to shop for (§4a). |
| 11 | Fitment data source | **User-supplied tire size**, from the federally mandated door placard or the tire sidewall. No licensed feed in v1 (§6). |

---

## 2. What the Maintenance Tracker actually gives us

Audited at commit `6c7c9a4`. ~3,200 LOC, clean and current.

**Stack (this is a gift — it matches decision 1 exactly):**
- Expo `~57.0.7`, React Native `0.86.0`, React `19.2.3`, TypeScript `~6.0.3`
- Jest + `jest-expo`, `testMatch: **/__tests__/**/*.test.ts`
- Also runs on web via `react-native-web` (it's a working PWA)
- Navigation is a hand-rolled `Nav` union in `App.tsx` — no `expo-router`

**Vehicle data pipeline (`src/api/vehicles.ts`):**
- NHTSA vPIC → makes and models by year (free, government, always current)
- EPA fueleconomy.gov → trim/engine variants (free, 1984+, has coverage gaps)
- Both free. **Neither returns wheel or tire fitment data.**

**Vehicle shape (`src/types.ts`):**
```ts
interface Vehicle {
  year: number; make: string; model: string;
  trim: string;   // "GT Premium"
  engine: string; // "5.0L V8 (Gas)"
}
```

**Persistence (`src/storage.ts`):** AsyncStorage only, key `maintenance-tracker/v1`,
`SCHEMA_VERSION = 2`, with a working v1→v2 migration. All state is one JSON blob;
backup/restore is that blob shared or downloaded.

### Three consequences that reshape the plan

**(a) Decision 4 is harder than it sounds — there is no stable vehicle ID.**
Maintenance Tracker stores make/model/trim as **free-text strings** sourced from
two APIs that disagree on naming. The existing code already fights this: `getTrims()`
fuzzy-matches vPIC's `"F-150"` against EPA's `"F150 Pickup 4WD"` by stripping
non-alphanumerics. Fitment lookup needs a *canonical* vehicle identity, so
"just use the vehicles we already have" requires a **resolution layer** —
`(year, make, model, trim)` strings → canonical fitment vehicle ID — with a
manual-disambiguation UI when the match is ambiguous. This is real Phase 1 work,
not a free import.

**(b) There is no backend, and decision 9 keeps it that way.**
Maintenance Tracker has **no auth, no user accounts, and no server-side data
store**. Its only server is a Cloudflare Worker for web push
(`push-worker/`, `maintenance-push.enachealex1.workers.dev`). Rather than build
one, the visualizer inherits the local-only model: builds, photos, and
calibration all live in device storage (§4d).

The one addition is a **stateless fitment proxy** — a Worker that holds vendor
API keys and caches responses, storing nothing about the user. Same Cloudflare
account, same deployment story as the existing push worker.

**Its local-only nature also constrains device migration.** `backup.ts` exports
one JSON blob, and `BACKUP_IMPORT_SUPPORTED` is **web-only** — native has no file
picker, so a phone user currently cannot restore a backup at all. With no cloud
sync in the plan, that gap becomes the *entire* migration story and has to be
closed (Phase 6).

**(c) The app runs on web, so renderer choice matters.**
Anything picked for the 2D compositor should degrade gracefully to
`react-native-web`, or web support gets dropped for that screen. See §4.

---

## 3. The four hard problems

**1. Fitment data is licensed — *sidestepped by decisions 10 and 11*.**
vPIC gives the vehicle tree but no wheel or tire specs, and every commercial
feed is quote-gated. **A profile view of tire size needs neither.** It needs the
size on the car now and the size being considered, both of which the user can
read off a federally mandated door placard or the tire sidewall. Full detail and
the vendor survey are in §6.

**2. Wheel catalogs and images have no universal API.**
Every brand publishes its own catalog. Aggregation happens through distributor
feeds or SEMA Data Co-op, or per-brand agreements. Product photography is
copyrighted — scraping it is a legal problem, not a shortcut. Upside: brands
generally *want* their wheels in a visualizer, so partnership is a real path.

**3. A profile view cannot show offset — accepted, and v1 does not try.**
Offset is lateral: in a 90° side elevation, +35 → +15 moves the wheel ~20mm
toward the camera and is invisible. Decision 10 accepts that and scopes v1 to
**tire size in profile**, which profile shows perfectly well — diameter,
sidewall height, and how the tire fills the arch are exactly the questions
someone has when working out what to buy.

This is a simplification, not a limitation to work around. §4a covers what it
buys: no perspective warp, an easier photo to take, and no wheel catalog.

**4. Vehicle imagery doesn't scale — *solved by decision 2*.**
Originally this plan needed either licensed vehicle photography (per angle, per
vehicle) or 3D models (nobody licenses 40,000 accurate ones). **User-submitted
photos remove the problem entirely:** no per-vehicle asset pipeline, no top-300
tier, no body-style fallbacks, and vehicle coverage becomes infinite on day one.
Remaining licensing exposure is the wheel catalog alone, which was required
regardless.

The approach is also a better product, not merely a cheaper one — it's the user's
actual car, in their color, at their ride height, with their existing mods. That
matters for share rate, which is the growth loop (§5, Phase 5).

---

## 4. Architecture

**One app, two feature tabs** (O1 resolved). Not a second Expo app, not a second
store listing — the visualizer ships inside the existing app as a peer tab to
maintenance, over a shared garage.

As built in Phase 1 (`+` marks what later phases add):

```
src/
  shell/          nav.ts, TabBar, HomeHub, VehicleList
  core/           types.ts, storage.ts, backup.ts, vehicles.ts (vPIC/EPA)
                + resolver.ts   canonical vehicle identity      (Phase 2)
                + fitment/      pure TS size, offset, clearance (Phase 3)
  features/
    maintenance/  logic, cadence, schedule, Dashboard, VehicleSetup,
                  MileageSetup, MaintenanceTab
    wheels/       WheelsTab (shell only)
                + capture, calibrate, catalog, compositor, builds
  components/     shared ui primitives
  theme.ts, notifications.ts, webNotifications.ts, pushConfig.ts, webViewport.ts
services/
  push-worker/    existing
                + fitment-proxy/  stateless key-holder + cache (§4d). No user data.
```

`core/` is kept flat rather than split into `vehicle/`, `garage/`, and `fitment/`
subdirectories — at four files, nesting would be structure without benefit.
Split it when `fitment/` lands, since that one is genuinely a module.

No auth service, no database, no object storage — decision 9.

**Two design calls worth flagging:**

- **`core/fitment` is pure TypeScript** — no network, no rendering, no React.
  Every correctness-critical behavior in the product lives here and is testable
  without a device or an API key. This is the single most important structural
  decision in the plan.

- **The garage is the spine, not a maintenance feature.** `VehicleRecord` moves
  to `core/garage` because both tabs read it. This is why one app beats two: the
  vehicle, its photos, and its calibration are added once and used everywhere.

### 4c. App shell & Home page

Today `Home.tsx` *is* the garage — "🚗 My Garage", a vehicle list routing into
the maintenance Dashboard, carrying app chrome (notification prompt, backup
export/import). It gets promoted to a genuine hub.

**Bottom tabs: Home · Maintenance · Wheels**

- **Home** — cross-feature hub. Vehicle cards showing *both* a maintenance-due
  badge and a saved-builds count, so each vehicle is one entry point to either
  feature. Add-vehicle CTA. App chrome moves here or to Settings.
- **Maintenance** — vehicle picker → existing Dashboard flow, unchanged.
- **Wheels** — vehicle picker → capture → calibrate → catalog → preview → save.

Extract a shared `VehicleList` / `VehiclePicker` from today's `Home.tsx`; all
three tabs use it. Behavior of the maintenance flow must not change.

**Navigation: keep the hand-rolled `Nav` union.** *(Reversed during Phase 1 —
see below.)*

This plan originally called for migrating to React Navigation. The justification
was that **Phase 6 needed share links**, and deep linking effectively requires
real linking config. **Decision 9 replaced share links with the OS share sheet**,
which removed that requirement entirely. What remained was screen count alone —
not enough to justify replacing working navigation.

Decisive factor against migrating: `App.tsx` hand-builds PWA history
(`ensureHistoryEntry`, a `popstate` listener, `parentOf`) so the phone back
gesture navigates in-app rather than closing the PWA. React Navigation's linking
layer would replace all of it, and that was already flagged as the most likely
way this phase breaks something users rely on. With the deep-linking driver gone,
that risk buys nothing.

**What shipped instead:** `Nav` gained a tab dimension. Pushed screens record the
tab they came from (`from: TabKey`), so backing out of a vehicle returns to that
tab rather than always landing on home. `parentOf`, `tabOf`, and `isTabRoot` are
pure functions in `src/shell/nav.ts` with unit tests, including that repeatedly
walking parents always terminates at home. Zero new dependencies.

*Considered and rejected:* `expo-router`. Better file-based ergonomics, but
migrating existing screens into file-based routes is a larger restructure than
React Navigation, which it's built on top of anyway.

### 4d. Fully local, no accounts

Maintenance Tracker is local-only today — AsyncStorage, with the push worker as
its only server. The visualizer inherits that unchanged. Decision 9 makes it
explicit and permanent:

| Data | Where it lives |
|---|---|
| Photos, calibration, builds, garage, history | **Device storage only** |
| Sharing | OS share sheet — the composite goes straight to Messages/Instagram/wherever |
| Fitment + catalog lookups | Stateless proxy (below), which stores nothing about the user |

**No sign-in, no sync, no user records anywhere.** This deletes auth, D1, object
storage, share-link hosting, moderation, and the entire GDPR/CCPA surface. It
also removes a signup wall from in front of the product's core value.

**Sharing without a backend.** The composite renders on-device and goes to the
native share sheet as an image. This is arguably a *better* growth loop than
share links — the image lands directly in the feed or thread where people
actually react to it. What's lost is the click-back funnel and per-share
analytics; a small in-image watermark recovers attribution if wanted.

**Affiliate links still work.** They're outbound URLs carrying your affiliate ID;
attribution happens on the retailer's side. You lose per-user analytics, not
revenue.

**Privacy becomes a feature, not a disclaimer.** "Your car, your photos, never
leaves your phone" is a real differentiator in this category, and it extends copy
`Home.tsx` already ships: *"Your data lives only on this device."*

#### No accounts still means one small server

The fitment API and wheel catalog are metered third-party services. **Embedding
their API key in the app means it gets extracted and your quota gets burned** —
a financial risk, not a theoretical one. A thin **stateless Cloudflare Worker**
solves it:

- holds the vendor API keys
- caches aggressively — fitment data is effectively static, and this is the
  difference between one API call per vehicle and one per user per session
- lets you swap vendors without shipping an app release

It stores **nothing about the user** — no accounts, no photos, no builds, no
identifiers. Same infrastructure as the existing `push-worker`.

#### The cost — decide deliberately

`backup.ts` is a pretty-printed JSON blob shared via the OS share sheet or
downloaded on web. It covers builds and calibration fine, since those are small.
Base64-encoding **photos** into it takes it from kilobytes to tens of megabytes,
so photos are excluded — a user switching phones re-shoots and re-calibrates.
Recalibration is one gesture per vehicle, so this is the recommended v1 answer
rather than a container format. This is O9.

With no cloud sync, **backup/restore is now the *only* device-migration path** —
so it matters more than it did, and Phase 6 should treat it as a real feature
rather than the afterthought it can be when sync exists.

**2D renderer:** start with layered `<Image>` + transforms, which works
identically on iOS, Android, and web with zero new dependencies. Escalate to
React Native Skia only if masking and sidewall generation demand it (Skia's web
build is a heavy CanvasKit/WASM payload — not free for the PWA).

### 4a. Profile view — what decision 10 buys

v1 renders a **square-on side view**. Earlier revisions of this plan specified a
25–30° corner shot so that offset would be visible; decision 10 drops offset from
v1, and with it the reason for the angle. Four things fall out:

- **No perspective warp.** Square-on, a wheel is a circle, not an ellipse. The
  compositor scales rather than projects, which removes the hardest part of the
  render and the one most likely to look pasted-on (deep-dish and concave spokes
  were the risk).
- **A far easier photo to ask for.** "Stand square to the side of the car" is an
  instruction people follow correctly; "stand at 25–30° off the centreline" is
  not. Capture quality was a named risk and this substantially defuses it.
- **Calibration simplifies to a circle.** The ellipse gesture in §4b collapses to
  a diameter — the user drags a circle to match their wheel. Scale and anchor
  still come free; the angle term is simply not needed.
- **No wheel catalog required.** See below.

**Rendering a size change without a catalog.** The user is asking "what size
should I shop for", not "what does this specific rim look like". So v1
**re-scales the user's own wheel** — cropped from their photo — to the new rim
diameter, and regenerates the tire sidewall at the new height around it. Their
actual wheel, shown at the proportions of the size they are considering.

Be straight about what that is and isn't: it shows **size and proportion
faithfully, not a different wheel design**. Seeing an alternative rim is what
the catalog (Phase 4) is for, and it is deferred. For the stated goal — working
out what to buy — proportion is the answer.

**Offset is not silently dropped.** The engine already computes poke and inner
clearance (Phase 3), so where a size or wheel change moves the wheel laterally
is still reported **numerically**. It is simply not drawn, because a profile
view would be lying if it tried.

### 4b. Photo capture & calibration — no ML required for v1

The user drags an ellipse over their wheel. **That single gesture yields every
parameter the compositor needs**, deterministically:

| Needed | Derived from |
|---|---|
| Scale (px per inch) | `circle diameter px ÷ reference wheel diameter` (see below) |
| Anchor point | circle centre |

*(Camera angle was the third output when v1 used a corner shot. Square-on, it is
fixed at zero — see §4a. The gesture is a circle, not an ellipse.)*

**The reference diameter cannot be assumed to be OE.** Scale calibration divides
by the diameter of the wheel *actually in the photo*. If the car is already on
aftermarket wheels — the enthusiast core of this audience — assuming the factory
diameter silently miscalibrates every render, with no error surfaced and no way
for the user to notice.

This is why decision 11 reads the size **off the tire sidewall** rather than
looking it up: the sidewall states what is fitted, an API states what shipped.
Calibration asks for the current size, offering the placard value (§6a) as the
default and letting the user correct it. A vendor lookup could not do this
correctly even if we paid for one.

No model to train, no training data, no inference cost, and it works at any angle
on any photo. **Auto-detection is a later convenience** that pre-positions the
ellipse — a nice-to-have, never a prerequisite. This keeps an entire ML
workstream off the critical path.

**Multi-angle capture replaces 3D rotation (decision 7).** The user shoots 3–4
photos walking around the car; each is calibrated independently; the UI swipes
between them. This delivers the *intent* of the original rotatable-3D
requirement — see the wheels from several angles — with zero models and zero
licensing. Not continuous rotation, but every frame is their actual car, which is
the better trade.

**Photos live in the garage.** Calibrate once when the vehicle is added, reuse
forever. The user does not need to be standing next to their car to shop, and it
mirrors the Maintenance Tracker garage model.

**Two hard parts, both to be validated in Phase 0:**

1. **Removing the old wheel.** Pasting over is not enough — a smaller new setup
   leaves the original peeking out. What rescues this is that **plus sizing keeps
   overall diameter roughly constant** by design (bigger wheel, shorter sidewall,
   same OD), so most swaps cover nearly the same pixels and plain occlusion
   works. The failure case is a genuine OD change, needing fill — forgiving
   against a mostly-dark tire, but it must be tested, not assumed.

2. **Lighting and color match.** The catalog wheel is studio-lit on white; the
   photo is a driveway at golden hour. Without correction the result reads as a
   sticker. Mitigation: sample ambient color and exposure from the photo, apply a
   tint, synthesize a contact shadow. **This is the single largest quality risk
   in the plan** — it decides whether output looks real.

---

## 5. Phases

Estimates assume 1–2 developers.

### Phase 0 — De-risking — **mostly obsolete**

Originally: trial a fitment API, get written pricing and image licensing from
catalog vendors, and spike the corner-angle composite. Decisions 10 and 11
removed the first two, and §6b found that vendor pricing is quote-gated
everywhere, so waiting on procurement would block the product for no benefit.

**What survives, folded into Phase 5:** the composite spike. It still has to be
proved on a real phone photo of a real car, and the risk order is unchanged:

1. **Does the lighting match hold up?** Shoot the same car in harsh noon sun,
   overcast, and golden hour. Still the largest quality risk in the plan.
2. **Does old-wheel removal work under a real diameter change?** Not just the
   constant-OD plus-size case that occlusion handles for free.

*(The third spike — deep-dish and concave spokes surviving a perspective warp —
is moot: square-on, there is no warp.)*

### Phase 1 — App shell & Home page (2 weeks) — **DONE**

**The one phase not blocked by Phase 0** — no licensing, no fitment feed, no
catalog. Landed in two commits on `claude/vehicle-tire-review-app-h5lse2`:

1. **`Reorganize src into core, features, and shell`** — pure moves plus the
   import updates they force. Zero behavior change; git records every move as a
   rename. Verified independently: tsc clean, original 24 tests pass.
2. **`Add tabbed shell with a Home hub and a Wheels tab`** — the new shell.

Delivered:
- `src/` regrouped into `core/` (types, storage, backup, vPIC/EPA client),
  `features/maintenance/`, `features/wheels/`, and `shell/`. The garage moved to
  `core/` because it stops being maintenance-owned once a second feature reads it.
- Bottom tabs — Home, Maintenance, Wheels — in normal flow below the screen, so
  scrolling content is never hidden behind the bar and no inset is needed.
- **Home hub**: each vehicle card carries maintenance status and offers both
  features directly. App chrome (reminders, backup) stays here.
- `VehicleList` / `VehicleCard` extracted so all three tabs render the garage
  identically instead of diverging.
- `Nav` extended with a tab dimension; `nav.ts` pure and unit-tested.
- Wheels tab stubbed, stating plainly that it isn't built rather than faking it.

**Verified:** tsc clean, 34 tests pass (24 existing + 10 new nav tests), web
bundle builds (332 modules).

**Not yet verified — needs a device.** The PWA back gesture and Android hardware
back were left structurally intact (that machinery was deliberately not
replaced), but they have not been exercised on real hardware. Do this before
building on top of the shell.

**Deferred to Phase 6:** saved-builds count on Home cards, which needs builds to
exist first. Showing "0 builds" on every card would be noise.

### Phase 2 — Vehicle identity & selection — **DROPPED from v1**

This phase existed to resolve Maintenance Tracker's free-text
`(year, make, model, trim)` into a canonical vehicle ID that a **vendor fitment
API** could be keyed on. Decision 11 removes the vendor lookup, so there is
nothing to key: the tire size comes from the user, who does not need their car
disambiguated against someone else's taxonomy to type it in.

It returns if and when a vendor prefill is added (§6c) — the naming mismatch
described in §2(a) is real and has not gone away, it is simply no longer on the
critical path.

**VIN scanning** was folded in here and is likewise deferred. Worth revisiting
as a convenience later, not needed to ship.

### Phase 3 — Fitment engine (2 weeks) — **DONE**

Pure TypeScript in `src/core/fitment/`. No network, no storage, no React, so it
was buildable with zero external dependencies — like Phase 1, unblocked by
Phase 0.

Delivered — `size.ts`, `geometry.ts`, `compare.ts`, `ratings.ts`:
- **Parsing:** metric (`275/40R20`, `P225/45R17 91V`, `LT275/70R18`,
  `275/40ZR20`) and flotation (`33x12.50R17`). Flotation states overall diameter
  instead of aspect ratio, so the ratio is recovered on parse and both formats
  share one geometry path.
- **Geometry:** sidewall, overall diameter, circumference, revs/mile (unloaded —
  documented, since published figures run a few percent higher).
- **Offset:** ET ↔ backspacing both directions, and where each rim edge sits
  relative to the hub face. That last one is what turns an offset number into
  the lateral shift the corner-angle render displays.
- **Comparison:** diameter/width/sidewall deltas, speedometer error, stance
  delta, and warnings — diameter past 3% and 5%, load index or speed rating
  below factory, poke and inner clearance, stretched/bulged rim width, and a
  wheel that cannot physically mount the tire.

**Parsing is separate from judgment**, which is what makes custom sizes work:
`parseTireSize` returns null only for unreadable input, `validateTireSize`
decides whether a size looks sensible, and `compareFitment` never blocks. An
aggressive setup returns full geometry plus a list of warnings, because looking
at one is a legitimate thing to want.

**116 tests.** Hand-worked values are pinned rather than snapshotted (a
275/40R20 is asserted at 110mm sidewall, 728mm overall), plus round-trip
identities for offset/backspacing and parse/format.

**One test worth calling out:** going up a wheel size holds overall diameter
within one percent. That is the invariant §4b leans on — it is *why* a swapped
wheel covers nearly the same pixels and plain occlusion hides the original.

**Verified:** tsc clean, 116 tests pass, web bundle builds.

### Phase 4 — Wheel catalog — **DEFERRED past v1**

Aggregating vendor feeds, normalising every brand's product photography to a
common face-on square at a known pixel-per-inch, and licensing the imagery was
the single largest workstream in this plan and the only remaining licensing
exposure.

Decision 10 removes it from v1: the compositor re-scales the user's own wheel
(§4a), so nothing needs to be licensed to answer "what size should I shop for".

This is the phase to revive when the product moves from *what size* to *which
wheel* — at which point the image-normalisation work described previously still
applies in full, and should still be treated as its own workstream with manual
QA rather than an afternoon of scripting.

### Phase 5 — Photo capture, calibration & profile compositor — **DONE**

Pick a vehicle → calibrate one square-on photo → try sizes and see them on your
own car.

**Calibration is two taps, not a drag.** Centre of the wheel, then the outer
edge of the tire; radius is the distance between them. No gesture library,
identical on native and `react-native-web`, and more precise than dragging a
handle on a phone-sized image. The tire's outer diameter follows exactly from
the fitted size, so the scale carries no modelling error — which is why that
reference was chosen over the rim lip.

**It asks for the size fitted right now, not the factory size.** The placard is
collected separately as the factory reference. On a modified car these differ,
and calibration must use what is actually in the photo (§4b).

**The composite needs no catalog and no native image manipulation.** The user's
own wheel is clipped out of their photo by a rounded `overflow: hidden`
container and rescaled to the new rim diameter, with a sidewall drawn around it
as a border whose thickness *is* the sidewall height at that scale. Square-on
means a wheel is a circle, so it is scale and position throughout. A disc
covering whichever tire is larger prevents a smaller proposed size leaving a
ring of the original visible.

**Numbers accompany the picture**, straight from the Phase 3 engine: diameter
delta, sidewall delta, speedometer error, and warnings. Offset is computed but
not drawn.

**Data safety.** `VehicleRecord.wheels` is optional with **no default backfilled
in migrate()**, so a record that has never opened the tab stays byte-identical
to one saved before the feature existed. Five upgrade-safety tests assert this,
including that migration never invents the field.

**Verified:** tsc clean, 165 tests (49 new), web bundle builds.

**Not verified — and this is now the main risk.** Nothing here has run on a
device or against a real photograph. Three things need a real phone:

1. **Does the composite look right?** The lighting-match risk (§4b) is untested,
   and it is still the largest quality risk in the plan.
2. **Is the two-tap calibration accurate enough in practice?** A few pixels of
   error at the tire edge scales into the whole render.
3. **Does the clipped-and-rescaled wheel hold up**, or does re-scaling the user's
   own wheel read as obviously enlarged?

**Addendum — size adjustment & calibration precision (post-review):**
- **Steppers** on the size field: Width/Sidewall/Wheel in the increments the
  industry sells (10mm / 5 / 1"), clamped to plausible ranges. Empty field +
  one tap steps from the fitted size. Stepped sizes drop the base's load/speed
  markings rather than claiming unchecked ratings.
- **Calibration loupe + nudge**: a 3× magnifier on the active point with 2px
  arrow nudges and a centre/edge toggle — fingertip accuracy was the cap on
  calibration quality.
- **Web tap fix**: react-native-web leaves `locationX` undefined for mouse
  events, so every web tap was silently NaN. Taps now use pageX/pageY minus the
  frame's measured window offset (both platforms).
- **Vehicle-switch fix**: the working size resets on vehicle change instead of
  leaking to the next car.
- **Verified end-to-end in a real browser** (Playwright over the exported
  bundle): file chooser → two taps → loupe/nudge → save → 'Calibrated against'
  → stepper → preview. The web-tap and vehicle-switch bugs were both found by
  this pass, not by unit tests — the E2E walkthrough is now part of the
  verification story. Live demo published as an artifact.

**Addendum 2 — the tire is real now too.** The proposed tire is no longer a
synthetic dark ring: both wheel AND tire are clipped from the user's photo and
rescaled independently. The tire scales so its outer edge lands at the new OD;
the wheel draws over the middle, so a thinner sidewall emerges from the bigger
wheel covering more rubber — which is what plus-sizing physically does. The one
case that trick can't cover (taller sidewall exposing scaled rim-lip pixels) is
patched with flat rubber tone at the rim, where real sidewalls are darkest; on
a plus-one the patch is provably unnecessary. Layer recipe is pure tested math
in `compositeLayersFor`. This also materially de-risks the lighting-match
question: the rubber now carries the photo's own lighting.

**Addendum 3 — durable photo storage (production-readiness, user-reported).**
The web picker returns a blob: URL that dies on reload (mobile browsers reload
constantly → "the image disappears"); native returns an OS-purgeable cache URI —
same bug, different coat. Photos now go through `core/photoStore`: IndexedDB on
web (idb: key resolved to an object URL per session), document-directory copy on
native (stable file:// path). Old data:/file:/https: URIs pass through, so
existing records keep working.

This also protects the data guarantee: photo bytes never enter AppData, because
one multi-MB record would breach localStorage's ~5MB cap and make **every**
subsequent save fail — silently taking maintenance edits with it. Records carry
a short reference only, pinned by an upgrade-safety test.

Web photos are downscaled at ingest (1600px long edge) — full-resolution phone
photos rendered 4× over at up to 3× scale blank out on mobile browsers. Touch
taps fixed alongside (pageX via changedTouches). A second E2E walkthrough now
runs under iPhone touch emulation, including tab-away and full-reload
persistence checks.

*Deferred:* placard OCR (needs a dev build for ML Kit — manual entry ships
first), automatic wheel detection, multi-angle capture, offset rendering.

### Phase 6 — Builds, sharing & commerce (1–2 weeks)

Roughly halved by decision 9 — no auth, no D1, no object storage, no share-link
hosting, no moderation.

- Save/name/revisit builds **in local storage**, alongside the existing garage.
- Side-by-side comparison of two builds.
- **Share via the OS share sheet** — composite rendered on-device, handed to the
  system as an image. EXIF strip and optional plate blur on the way out.
- **Affiliate buy links** (Tire Rack, Discount Tire, Fitment Industries).
  Outbound URLs with your affiliate ID; attribution is retailer-side.
- **Extend `backup.ts` to cover builds and calibration.** With no cloud sync this
  is the only device-migration path, so it is a real feature now — including
  making import work on native, which today is web-only for lack of a file picker.
- **Write-back to the Maintenance tab:** a purchased wheel/tire set becomes a
  tracked item with mileage — the payoff of the shared garage (decision 8).

### Phase 7 — Hardening & launch (2–3 weeks)
Accessibility, offline behavior, analytics on the fitment funnel, crash
reporting, store assets and review, and legal — fitment guidance is advisory,
**not a safety guarantee**, and must say so.

### Fast-follows (post-v1, in likely priority order)
1. **Automatic wheel detection** — pre-positions the calibration ellipse. Pure
   convenience; the manual gesture already works.
2. **Stylized body-style illustrations** — the no-photo fallback, pending O6.
3. **Tire brand/sidewall detail** — per decision 6.
4. **3D viewer** — *no longer on the roadmap by default.* Multi-angle photo
   capture (decision 7) covers the original rotate requirement, and 3D would show
   a generic model rather than the user's own car, which is a product step
   backward. Revisit only if continuous rotation proves necessary; it would mean
   `three.js` via `react-three-fiber` + `expo-gl`, per-vehicle model licensing,
   and a GLB pipeline under ~5MB at 60fps on mid-range Android.

**Rough v1 total: 1.5–2.5 months from here.** Phases 1 and 3 are done; 2 is
dropped, 4 deferred, and 0 largely obsolete. What remains is Phase 5 (3–4
weeks), Phase 6 (1–2 weeks) and Phase 7 (2–3 weeks), none of them blocked.

*Original estimate, for reference: 3–4.5 months.* Phase 5 grew ~1 week for capture and
calibration, offset by deleting the vehicle-imagery acquisition workstream
entirely. Decision 8 (one app) removes a second store listing and a duplicate
garage. Decision 9 (no accounts) roughly halves Phase 6 — no auth, no database,
no object storage, no share-link hosting, no moderation, and no privacy-compliance
surface.

**Critical path note:** Phase 1 is the only phase with no external dependency —
no licensing, no feed, no catalog. It can run in parallel with Phase 0 and is the
correct place to start.

---

## 6. Fitment data — research findings

**Headline: decision 10 removes the licensed-data dependency from v1.**

A profile view showing tire size, with no offset, needs exactly two numbers —
the tire size currently on the car, and the size the user wants to try. It does
not need bolt pattern, hub bore, centre bore, factory offset ranges, or a wheel
catalog. Those were the expensive parts.

### 6a. Two free sources, both already in the user's possession

**The door placard is federally mandated.** Under FMVSS 110 (49 CFR 571.110),
every vehicle with a GVWR of 4,536kg (10,000lb) or less must carry a permanently
affixed placard on the driver's side B-pillar showing the manufacturer's
recommended tire size, along with cold inflation pressure and capacity weight.
This is not a data licensing question at all — it is a legal requirement on a
sticker the user can photograph, and it gives the **factory** size.

**The tire sidewall gives the current size.** Moulded into every tire, and this
is the one the compositor actually needs: §4b calibrates scale against the wheel
*in the photo*, which on a modified car is not the factory size. A vendor API
returns what the car shipped with and would silently miscalibrate exactly those
users — the enthusiast core. **On this specific point the free source is better
than the paid one, not merely cheaper.**

Both are around ten characters. Typed, that is a few seconds; the app already
asks for a photo, so OCR of the placard is a natural extension rather than a new
kind of request.

### 6b. Commercial options, if lookup convenience is wanted later

| Source | What it gives | Pricing |
|---|---|---|
| [Wheel-Size.com](https://developer.wheel-size.com/) | OE + aftermarket sizes, rim dimensions, offset, bolt patterns. Headline coverage 60,000+ configurations, 250+ makes, 14 regions | Free sandbox, no card, ~2–4h manual review. Paid billed yearly; **+$800/yr per additional app**. Tier prices not published |
| [vehicledatabases.com](https://vehicledatabases.com/api/tire-wheel-fitment-specifications) | VIN or Y/M/M → tire and wheel specs | Not published |
| [tire.vdim.app](https://tire.vdim.app/) | Search by vehicle or by tire size | Free plan advertised; detail not published |
| [DriveRightData](https://www.infopro-digital-automotive.com/us/driverightdata/tire-fitments-database/) | OE, OE-optional and aftermarket fitments by trim | Enterprise, quote only |
| [Fitment Group](https://fitmentgroup.com/tire-and-wheel-fitment-data/) | Tire and wheel fitment data | Enterprise, quote only |

**Finding worth acting on: none of them publish tier pricing.** Every one is
quote-gated or behind an application. Phase 0's "get written pricing" step
therefore cannot be shortcut by research — it needs an actual application, and
several of these sites block automated access outright. That is a reason to ship
v1 without them rather than wait on procurement.

*(NHTSA vPIC, already used for the make/model tree, does not return tire or
wheel fitment. It remains the vehicle-identity source only.)*

### 6c. Recommendation

Ship v1 on user-supplied sizes: manual entry, with placard/sidewall OCR as the
fast path. Zero cost, zero licensing, works on any vehicle in any market
including grey imports and anything already modified, and it is the *correct*
input for calibration rather than a compromise.

Take Wheel-Size.com's free sandbox afterwards as a **prefill convenience** —
behind the stateless proxy (§4d), so the key never ships in the app — once the
product itself is validated. It saves the user typing; it is not load-bearing.

**Consequence: nothing external blocks the roadmap any more.** Phase 2 (canonical
vehicle resolution) existed to key a vendor lookup and is no longer needed for
v1. Phase 4 (wheel catalog) is deferred. The path to a shippable product is
Phase 5 → 6 → 7, all buildable now.

---

## 7. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Wheel image rights unclear | **Retired for v1** | No catalog imagery used — the compositor rescales the user's own wheel (§4a) |
| Vehicle name matching is noisy | **Retired for v1** | No vendor lookup to key, so nothing to match (Phase 2 dropped) |
| Image normalization underestimated | Deferred with Phase 4 | Returns when a wheel catalog does; still its own workstream then |
| **Lighting mismatch makes composites look fake** | Core feature feels cheap | Largest quality risk. Ambient sampling + contact shadow; validated first in Phase 5 |
| Old wheel not fully removed on OD change | Visible artifact | Plus sizing keeps OD ~constant (pinned by test in Phase 3), so most swaps occlude cleanly; fill path tested in Phase 5 |
| Warped wheel looks pasted on | **Retired** | Square-on means no perspective warp at all (§4a) |
| Poor user photos (dark, blurry) | Bad output blamed on the app | Capture guardrails. Angle risk is much reduced — "stand square to the car" is an instruction people follow (§4a) |
| Users supply copyrighted images they found online | Neutralized by decision 9 (§7a) | Nothing is uploaded, so there is no hosting exposure. One constraint only: no in-app image search |
| **Native has no backup import today** | With no sync, native users have **no** device-migration path at all | `BACKUP_IMPORT_SUPPORTED` is web-only for lack of a file picker. Add `expo-document-picker` in Phase 6 |
| Photos excluded from backup | Device migration loses calibration | Accepted for v1 — recalibration is one gesture per vehicle (§4d, O9) |
| Vendor API key extracted from the app binary | Not applicable in v1 | No vendor API is called. If prefill is added later, the stateless proxy holds the key (§4d, §6c) |
| Photo shows non-factory wheels | **Silent** miscalibration of every render | Calibrate against the size on the sidewall, not the placard or a lookup (§4b, §6a) |
| Fitment advice taken as authoritative | Liability | Prominent advisory disclaimer; never present as a guarantee |
| Users expect to browse actual wheels | v1 shows size, not wheel designs | Say so plainly in the UI; the catalog is the honest answer, and it is Phase 4 |

---

### 7a. User-sourced photos — scoped by the local-first architecture

Users will supply images they found online, of their own model or of a car
they're considering. The library picker cannot distinguish those from photos they
shot, so this is not a feature to build or block.

**It is also not a service-liability question.** Compositing and storage are
local and nothing is ever uploaded (decision 9), so the service never copies,
hosts, or distributes the image. No hosting, no hosting exposure. This is how any
photo editor operates.

**What that leaves — one UI constraint:**
- **Never build in-app image search.** A "find photos of your car" browser is
  active encouragement rather than neutral tooling, and is the version that draws
  both a takedown and a store rejection.
- **Keep library upload with neutral copy.** No further gating needed.

Note the technical consequence, which is unrelated to any of the above: sourced
photos frequently show aftermarket wheels, which breaks scale calibration unless
§4b's factory-wheel question is implemented.

---

## 8. Open questions

- ~~**O1 — Separate app, or a new tab?**~~ **Resolved:** one app, tabbed shell,
  Wheels as a peer tab to Maintenance from a new Home hub (decision 8, §4c).
- **O8 — Does the app get renamed?** "Maintenance Tracker" no longer describes a
  product that also visualizes wheels. Affects `app.json`, store listing, and
  icon. Cheap now, expensive after launch — worth deciding before Phase 1 ships.
- **O2 — Keep web/PWA support for the visualizer,** or native-only for that screen?
  Lower stakes now: with no perspective warp, plain `<Image>` + transforms cover
  it on all three platforms, so Skia's heavy web payload is likely avoidable (§4).
- ~~**O3 — Existing wheel brand relationships?**~~ **Not needed for v1** — the
  wheel catalog is deferred (Phase 4). Revisit when moving from *what size* to
  *which wheel*.
- ~~**O4 — Does the garage move to the cloud?**~~ **Resolved:** no. Everything
  local, no accounts (decision 9).
- ~~**O5 — Where do vehicle base images come from?**~~ **Resolved:** user-submitted
  photos (decision 2). No licensed vehicle imagery in v1.
- **O6 — Is there a no-photo fallback?** *(unchanged, still open)* A user shopping at work, or before buying
  the car, has nothing to composite onto. **Partially resolved** — see §7a: allow
  library upload with neutral copy, never build in-app image search. The clean
  fallback for the genuine no-photo case remains commissioned *stylized
  illustrations* per body style: one-time cost, no ongoing exposure, and being
  stylized they sidestep the photo-realism problem rather than competing with it.
- ~~**O7 — Is the share surface public or private?**~~ **Resolved:** there is no
  share surface. Sharing goes through the OS share sheet; nothing is hosted, so
  there is nothing to moderate (decision 9).
- **O9 — Photos in backup?** Recommended v1 answer is no: exclude them, accept
  re-shoot on device change (§4d). Revisit if users push back.
- **O12 — Does v1 need a tire *brand* picker?** Decision 6 deferred sidewall
  detail, and profile-only makes brand almost invisible — a size and a sidewall
  height look the same whoever made the tire. Affiliate links still need a
  brand+size to hand off to a retailer, so Phase 6 needs an answer even if the
  render does not.
- **O10 — Is losing cross-device continuity acceptable?** Decision 9's one real
  cost. A user replacing their phone re-shoots and re-calibrates, and there is no
  "log in and it's all there." Local-only privacy is a genuine differentiator, so
  this is likely the right trade — but it should be a chosen trade, and it makes
  fixing native backup import (Phase 6) non-optional.
