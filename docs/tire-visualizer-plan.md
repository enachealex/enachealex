# Tire & Wheel Visualizer — Implementation Plan

**Status:** Phase 1 complete (app shell). Phases 0, 2–7 not started.
**Last updated:** 2026-08-01

Goal: a mobile app where a user picks their vehicle, selects wheels and tires
(catalog or custom size), and sees the result composited onto **a photo of their
own car** — with correct diameter, sidewall, and offset — viewable from multiple
captured angles.

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

**1. Fitment data is licensed, not free.**
vPIC gives the vehicle tree but no wheel/tire specs. Bolt pattern, hub bore,
OE tire sizes, and factory offset ranges all require a commercial feed. There is
no legitimate free source. Priced in §6.

**2. Wheel catalogs and images have no universal API.**
Every brand publishes its own catalog. Aggregation happens through distributor
feeds or SEMA Data Co-op, or per-brand agreements. Product photography is
copyrighted — scraping it is a legal problem, not a shortcut. Upside: brands
generally *want* their wheels in a visualizer, so partnership is a real path.

**3. A true profile view cannot show offset — so v1 uses a corner angle instead.**
Offset is lateral. In a 90° side elevation, +35 → +15 moves the wheel ~20mm
toward the camera — invisible. **Resolved:** the primary (and only) v1 view is a
**front corner shot at 25–30° off profile**. See §4a for why that angle.
Profile view is the degenerate no-warp case of the same compositor and can be
added later — but it costs a second base image per vehicle, which is the
expensive part.

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

### 4a. Camera angle

The target capture is a **front corner shot, 25–30° off profile**. Two forces set
that range:

- **Rotating further breaks the 2D illusion.** At an angle a wheel projects as an
  ellipse, and the flat face-on catalog photo must be perspective-warped onto it.
  Past roughly 35–40° the render needs the wheel's *barrel and spoke sides* —
  geometry that does not exist in a face-on photograph. Deep-dish and concave
  spokes fail first, looking pasted on rather than mounted.
- **Rotating less hides the offset.** A 20mm offset change projects to ~7mm of
  apparent shift at 20°, ~14mm at 45°. Poke/tuck is read against the fender edge,
  which the eye judges well as a relative-edge comparison — but shallower always
  shows less.

Since photos are user-supplied, this is **capture guidance, not an asset spec** —
a ghost overlay in the camera showing where to stand. The compositor handles
whatever angle it actually receives, measured per §4b, and warns when a photo is
too close to profile for offset to be legible.

**The warp stays dependency-free.** React Native supports
`transform: [{ perspective }, { rotateY }]` natively, and it maps to CSS
transforms under `react-native-web` — so it runs on iOS, Android, and the PWA
with nothing new added. Skia is likely avoidable.

**What does get harder:** the tire becomes an elliptical annulus with a visible
**tread band** on the leading edge, rather than a flat ring. New procedural work,
but also a visual upgrade — visible tread is what sells the render.

### 4b. Photo capture & calibration — no ML required for v1

The user drags an ellipse over their wheel. **That single gesture yields every
parameter the compositor needs**, deterministically:

| Needed | Derived from |
|---|---|
| Camera angle θ | `cos θ = minor axis ÷ major axis` — the ellipse's squash *is* the angle |
| Scale (px per inch) | `major axis px ÷ reference wheel diameter` (see below) |
| Anchor point | ellipse center |

**The reference diameter cannot be assumed to be OE.** Scale calibration divides
by the diameter of the wheel *actually in the photo*. If the car is already on
aftermarket wheels — the enthusiast core of this audience — assuming the factory
diameter silently miscalibrates every render, with no error surfaced and no way
for the user to notice. Calibration must therefore ask **"are these the factory
wheels?"** and accept a current diameter when the answer is no. This applies to
user-taken photos, not just sourced ones.

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

### Phase 0 — De-risking (1–2 weeks)
Not optional. Everything downstream is priced by what this finds.
- Trial the fitment API; validate coverage against 20 real vehicles.
- Get written pricing *and image licensing terms* from 2–3 catalog sources.
- **Spike:** composite a catalog wheel onto a **real phone photo of a real car**,
  calibrated by the ellipse gesture (§4b), on device and on web. Three things
  this must answer, in priority order:
  1. **Does the lighting match hold up?** Shoot the same car in harsh noon sun,
     overcast, and golden hour. This is the largest quality risk in the plan.
  2. **Does old-wheel removal work under a real OD change?** Not just the
     constant-OD plus-size case that occlusion handles for free.
  3. **Do deep-dish and concave-spoke wheels survive the warp?** They fail first,
     and they decide whether 30° holds or the guidance angle comes down.
- Prototype the canonical vehicle resolver against real Maintenance Tracker
  records; measure the ambiguous-match rate. That number sizes Phase 2.

**Exit:** known monthly data cost, and a wheel visibly on a car on a phone.

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

### Phase 2 — Vehicle identity & selection (2 weeks)
- **Canonical resolver:** MT's `(year, make, model, trim)` → fitment vehicle ID.
  Fuzzy match, confidence score, cached. Reuse the normalization already proven
  in `getTrims()`.
- Disambiguation UI for low-confidence matches ("which of these is your Camry?").
- **VIN scan** (camera → barcode → vPIC decode) as the high-accuracy fast path.
  Recommended: VIN sidesteps the whole string-matching problem for new vehicles.
- Import flow from the Maintenance Tracker garage.

**Exit:** every vehicle in a real tracker garage resolves to a fitment ID or
prompts sensibly.

### Phase 3 — Fitment engine (2 weeks, parallel with Phase 2)
Pure TypeScript, zero UI. ~90% tests by line count.

- **Size parsing:** `275/40R20`, `33x12.50R17`, metric and flotation.
- **Derived geometry:**
  - sidewall height (mm) = section width × aspect ÷ 100
  - overall diameter = rim (in × 25.4) + 2 × sidewall
  - revs/mile = 63360 ÷ (π × diameter in inches)
- **Comparison vs. OE:** speedometer error %, diameter delta, width delta.
- **Offset math:** ET ↔ backspacing; poke/tuck delta = OE offset − new offset
  (lower offset = more poke).
- **Clearance warnings:** fender rub, strut clearance, caliper clearance, load
  index or speed rating below OE.
- **Custom sizes:** free-form entry, validated and *warned* against — never
  blocked. Someone running a deliberately aggressive setup must be able to see it.

**Exit:** fixture-based test suite green against known-good fitment data.

### Phase 4 — Wheel catalog (2–3 weeks)
- Ingestion: normalize vendor feeds to one schema (brand, model, finish,
  diameter, width, offset, bolt pattern, hub bore, load rating, weight, price).
- **Image normalization — the underrated part.** Every brand shoots wheels
  differently. The compositor needs every image center-cropped, square,
  transparent-background, true face-on, at a known pixel-per-inch. Without this
  the renderer produces garbage. Budget real time and manual QA here.
- Browse UI with faceted filters, and a **"Fits my vehicle"** filter driven by
  Phase 3 — the core value proposition.

**Exit:** catalog browsable and filterable against a real vehicle.

### Phase 5 — Photo capture, calibration & compositor (4–5 weeks) — *the v1 payoff*

**5a. Capture & calibration (§4b)**
- Camera flow with a ghost overlay guiding a 25–30° corner shot; support picking
  from the library too.
- Ellipse-fit gesture per wheel → derives angle, scale, and anchor.
- **"Are these the factory wheels?"** step; if no, take the current wheel
  diameter as the scale reference (§4b). Skipping this miscalibrates silently.
- Multi-angle capture: 3–4 shots per vehicle, each calibrated, swipeable.
- Photos and calibration stored on the vehicle record; captured once, reused.
- Quality guardrails: reject too-dark/too-blurry, warn when the angle is too near
  profile for offset to read.
- Photos persist to local storage only (decision 9, §4d). **Strip EXIF and offer
  license-plate blur at the share boundary**, not at ingest — the photo never
  leaves the device otherwise, and stripping on the way out is where it matters.

**5b. Compositor**
- Mask out the original wheel; fill where the new OD is smaller (§4b, hard part 1).
- Scale the catalog wheel to true diameter → perspective-warp onto the measured
  ellipse via `perspective` + `rotateY` → generate the tire as an elliptical
  annulus with a leading-edge tread band from Phase 3 math → translate laterally
  by offset.
- **Offset renders as a real lateral translation**, not an annotation.
- Ambient color/exposure match + synthesized contact shadow (§4b, hard part 2).
- Controls: ride height, finish swap, staggered front/rear setups.
- Numeric poke/tuck readout alongside the render.
- **Export/share as an image.** The growth loop, and far stronger now that the
  shared image is the user's own car — do not defer it.

*Deferred:* automatic wheel detection (pre-positions the ellipse; pure
convenience), and any licensed or generic stock vehicle imagery.

**Exit:** a user's real tracked vehicle, with a real catalog wheel, shareable.

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

**Rough v1 total: 3–4.5 months.** Phase 5 grew ~1 week for capture and
calibration, offset by deleting the vehicle-imagery acquisition workstream
entirely. Decision 8 (one app) removes a second store listing and a duplicate
garage. Decision 9 (no accounts) roughly halves Phase 6 — no auth, no database,
no object storage, no share-link hosting, no moderation, and no privacy-compliance
surface.

**Critical path note:** Phase 1 is the only phase with no external dependency —
no licensing, no feed, no catalog. It can run in parallel with Phase 0 and is the
correct place to start.

---

## 6. Fitment data options — to price in Phase 0

Figures below are directional and **must be confirmed with vendors**; published
pricing moves and several are quote-only.

| Source | What it gives | Ballpark | Notes |
|---|---|---|---|
| NHTSA vPIC | Vehicle tree, VIN decode | Free | Already in use. No fitment. |
| Wheel-Size.com API | OE + aftermarket fitment, bolt pattern, offset ranges | ~$50–500/mo by tier | Cheapest credible path. Best first call. |
| SEMA Data Co-op | Aftermarket wheel catalog + media | ~$500–2,000/yr | Membership; good for catalog + images. |
| Distributor feeds (Turn 14, Keystone) | Catalog, stock, pricing, images | Free w/ dealer account | Requires reseller status. |
| DataOne / Chrome Data | Full OE fitment | Enterprise, $1,000s/mo | Overkill for v1. |

**Recommended opening move:** Wheel-Size.com for fitment + SEMA Data Co-op for
catalog and imagery. Validate both in Phase 0 before committing to either.

---

## 7. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Wheel image rights unclear | Blocks launch; store rejection | Written licensing in Phase 0, before any renderer work |
| Vehicle name matching is noisy | Wrong fitment shown → safety issue | Confidence scoring + disambiguation UI + VIN path |
| Image normalization underestimated | Renderer output looks broken | Treat as its own workstream in Phase 4, with manual QA |
| **Lighting mismatch makes composites look fake** | Core feature feels cheap | Largest quality risk. Ambient sampling + contact shadow; validated first in Phase 0 |
| Old wheel not fully removed on OD change | Visible artifact | Plus sizing keeps OD ~constant, so most swaps occlude cleanly; fill path tested in Phase 0 |
| Warped catalog photo looks pasted on | Core feature feels cheap | Guide capture to ≤30°; test deep-dish and concave spokes in Phase 0 |
| Poor user photos (dark, blurry, bad angle) | Bad output blamed on the app | Capture guardrails + ghost overlay + explicit angle warning (Phase 5a) |
| Users supply copyrighted images they found online | Neutralized by decision 9 (§7a) | Nothing is uploaded, so there is no hosting exposure. One constraint only: no in-app image search |
| **Native has no backup import today** | With no sync, native users have **no** device-migration path at all | `BACKUP_IMPORT_SUPPORTED` is web-only for lack of a file picker. Add `expo-document-picker` in Phase 6 |
| Photos excluded from backup | Device migration loses calibration | Accepted for v1 — recalibration is one gesture per vehicle (§4d, O9) |
| Vendor API key extracted from the app binary | Quota burned, real financial cost | Stateless proxy holds the key; app never sees it (§4d) |
| Photo shows non-factory wheels | **Silent** miscalibration of every render | Ask "are these factory wheels?" at calibration; take current diameter (§4b) |
| Fitment advice taken as authoritative | Liability | Prominent advisory disclaimer; never present as a guarantee |
| Fitment proxy needed before catalog work | Phase 4 blocked | Stateless Worker, small — stand it up during Phase 3 |

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
  Affects the 2D renderer choice (§4).
- **O3 — Existing wheel brand relationships,** or starting cold on catalog data?
- ~~**O4 — Does the garage move to the cloud?**~~ **Resolved:** no. Everything
  local, no accounts (decision 9).
- ~~**O5 — Where do vehicle base images come from?**~~ **Resolved:** user-submitted
  photos (decision 2). No licensed vehicle imagery in v1.
- **O6 — Is there a no-photo fallback?** A user shopping at work, or before buying
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
- **O10 — Is losing cross-device continuity acceptable?** Decision 9's one real
  cost. A user replacing their phone re-shoots and re-calibrates, and there is no
  "log in and it's all there." Local-only privacy is a genuine differentiator, so
  this is likely the right trade — but it should be a chosen trade, and it makes
  fixing native backup import (Phase 6) non-optional.
