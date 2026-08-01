# Tire & Wheel Visualizer — Implementation Plan

**Status:** Planning. No implementation started.
**Last updated:** 2026-08-01

Goal: a mobile app where a user picks their vehicle, selects wheels and tires
(catalog or custom size), and sees the result rendered on their vehicle — with
correct diameter, sidewall, and offset — in a profile view and (later) a
rotatable 3D view.

---

## 1. Locked decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Platform / stack | React Native + Expo, iOS + Android (+ web, inherited) |
| 2 | Visualization for v1 | 2D profile only; 3D as a fast-follow |
| 3 | Fitment data budget | TBD — options priced in §6 |
| 4 | Vehicle coverage for v1 | Only vehicles already in Maintenance Tracker |
| 5 | v1 scope | Visualizer + accounts + affiliate buy links |
| 6 | Wheels vs. tires | Wheel *models* + tire *sizes*. Tire brand/sidewall detail deferred. |

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

**(b) Decision 5 requires building a backend from scratch.**
Maintenance Tracker has **no auth, no user accounts, and no server-side data
store**. Its only server is a Cloudflare Worker for web push
(`push-worker/`, `maintenance-push.enachealex1.workers.dev`). Accounts, saved
builds, and affiliate attribution all need a backend that does not exist yet.
Recommendation: stay on Cloudflare (Workers + D1 + R2) since that's already the
deployment target and the account is set up.

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

**4. 3D vehicle models don't scale.**
There are 40,000+ year/make/model/trim combos and nobody licenses 40,000 accurate
models. Every competitor ships high-quality models for the top ~100–300 vehicles
and falls back to body-style archetypes. Decision 4 (only vehicles already in the
tracker) actually *defuses* this — the initial model set is however many cars the
user base already tracks. Deferred to Phase 6 regardless.

---

## 4. Architecture

```
apps/
  maintenance-tracker/     existing Expo app
  tire-visualizer/         new Expo app
packages/
  vehicle-core/            Vehicle types, vPIC/EPA clients, canonical resolver
  fitment-engine/          pure TS: size math, offset math, clearance warnings
  ui-kit/                  shared theme + primitives (from src/theme.ts)
services/
  api/                     Cloudflare Worker: auth, garage, catalog, affiliate
  push-worker/             existing
```

**Two design calls worth flagging:**

- **`fitment-engine` is a pure TypeScript package** — no network, no rendering,
  no React. Every correctness-critical behavior in the product lives here and is
  testable without a device or an API key. This is the single most important
  structural decision in the plan.

- **Separate app vs. a tab inside Maintenance Tracker.** Given decision 4, a tab
  inside the existing app is the tighter product and roughly half the work — no
  second auth story, no second store listing, vehicles already present. The
  monorepo above supports either; the shared packages are what matter.
  **Open question O1 in §8.**

**2D renderer:** start with layered `<Image>` + transforms, which works
identically on iOS, Android, and web with zero new dependencies. Escalate to
React Native Skia only if masking and sidewall generation demand it (Skia's web
build is a heavy CanvasKit/WASM payload — not free for the PWA).

### 4a. Camera angle — why 25–30°

The v1 view is a front corner shot, not a profile. Two forces set the angle:

- **Rotating further breaks the 2D illusion.** At an angle a wheel projects as an
  ellipse, and the flat face-on catalog photo must be perspective-warped onto it.
  Past roughly 35–40° the render needs the wheel's *barrel and spoke sides* —
  geometry that does not exist in a face-on photograph. Deep-dish and concave
  spokes fail first, looking pasted on rather than mounted.
- **Rotating less hides the offset.** A 20mm offset change projects to ~7mm of
  apparent shift at 20°, ~14mm at 45°. Poke/tuck is read against the fender edge,
  which the eye judges well as a relative-edge comparison — but shallower always
  shows less.

**25–30° is the compromise:** poke/tuck reads clearly, and the face stays
foreshortened enough that a warped catalog image holds up.

**This stays dependency-free.** React Native supports
`transform: [{ perspective }, { rotateY }]` natively, and it maps to CSS
transforms under `react-native-web` — so the corner warp runs on iOS, Android,
and the PWA with nothing new added. Skia is likely avoidable.

**What does get harder:** the tire becomes an elliptical annulus with a visible
**tread band** on the leading edge, rather than a flat ring. New procedural work,
but also a visual upgrade — visible tread is what sells the render.

---

## 5. Phases

Estimates assume 1–2 developers.

### Phase 0 — De-risking (1–2 weeks)
Not optional. Everything downstream is priced by what this finds.
- Trial the fitment API; validate coverage against 20 real vehicles.
- Get written pricing *and image licensing terms* from 2–3 catalog sources.
- **Spike:** one hardcoded car + one hardcoded wheel composited at a 25–30°
  corner angle (§4a), on a real device and on web. **Test a deep-dish and a
  concave-spoke wheel specifically** — those fail first, and they decide whether
  the warped-flat-photo approach holds or the angle has to come down.
- Prototype the canonical vehicle resolver against real Maintenance Tracker
  records; measure the ambiguous-match rate. That number sizes Phase 2.

**Exit:** known monthly data cost, and a wheel visibly on a car on a phone.

### Phase 1 — Monorepo & shared core (1–2 weeks)
- Stand up the workspace; move Maintenance Tracker in without breaking it.
- Extract `vehicle-core` (types, vPIC/EPA clients) and `ui-kit` (theme).
- Extract `fitment-engine` skeleton.
- CI: typecheck + Jest across all packages. Note `testMatch` currently excludes
  `.tsx` — widen it if component tests are wanted.

**Exit:** Maintenance Tracker builds and passes tests from inside the monorepo,
with zero behavior change.

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

### Phase 5 — 2D corner visualizer (3–4 weeks) — *the v1 payoff*
- Vehicle base assets: **one front corner shot per vehicle at 25–30° off
  profile** (§4a), each with authored metadata — wheel-well anchor points (x, y),
  the camera angle it was shot at, pixels-per-inch scale, and a fender mask layer
  for correct occlusion. Consistent angle across the fleet matters more than the
  exact value; the compositor reads the angle per asset.
- Compositor: scale wheel image to true diameter → perspective-warp the face onto
  the projected ellipse via `perspective` + `rotateY` → generate the tire as an
  elliptical annulus with a leading-edge tread band from Phase 3 math → translate
  laterally by offset → draw behind fender mask.
- **Offset renders as a real lateral translation**, not an annotation — this is
  the whole reason for the corner angle.
- Controls: ride height, finish swap, staggered front/rear setups.
- Numeric poke/tuck readout alongside the render.
- **Export/share as an image.** This is the organic growth loop — do not defer it.
- *Not in v1:* profile view. It's the no-warp case of this same compositor, so
  the code is nearly free — but it needs a second base image per vehicle.

**Exit:** a user's real tracked vehicle, with a real catalog wheel, shareable.

### Phase 6 — Accounts, garage & commerce (2–3 weeks)
- Cloudflare Worker API + D1: auth, saved builds, sync.
- Save/name/revisit builds; side-by-side comparison of two builds.
- Share links with server-rendered preview images.
- **Affiliate buy links** (Tire Rack, Discount Tire, Fitment Industries) with
  click attribution. The visualizer is the funnel; the click-out is the revenue.
- **Write-back to Maintenance Tracker:** a purchased wheel/tire set becomes a
  tracked item with mileage. This is the integration that makes two apps feel
  like one product.

### Phase 7 — Hardening & launch (2–3 weeks)
Accessibility, offline behavior, analytics on the fitment funnel, crash
reporting, store assets and review, and legal — fitment guidance is advisory,
**not a safety guarantee**, and must say so.

### Fast-follow — 3D viewer (4–6 weeks)
Deferred per decision 2. `three.js` via `react-three-fiber` + `expo-gl`.
Model pipeline: source GLB → decimate → bake → Draco/KTX2 → under ~5MB.
Rig wheel mount transforms so offset is a real translation along the axle.
Parametric tire mesh from the Phase 3 math — never one model per size.
Hard budget: 60fps on a mid-range Android or the feature isn't real.

**Rough v1 total: 3.5–5 months** (2D-only scope).

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
| Vehicle base imagery is licensed and per-angle | Cost scales with views × vehicles | One angle only in v1 (§4a); revisit 3D if more angles are wanted |
| Warped catalog photo looks pasted on | Core feature feels cheap | Hold the angle ≤30°; test deep-dish and concave spokes in Phase 0 |
| Fitment advice taken as authoritative | Liability | Prominent advisory disclaimer; never present as a guarantee |
| Backend is greenfield | Phase 6 slips | Start the Worker + D1 skeleton during Phase 4 |

---

## 8. Open questions

- **O1 — Separate app, or a new tab inside Maintenance Tracker?** Given decision 4,
  a tab is tighter and roughly half the work. Leaning tab; needs a call.
- **O2 — Keep web/PWA support for the visualizer,** or native-only for that screen?
  Affects the 2D renderer choice (§4).
- **O3 — Existing wheel brand relationships,** or starting cold on catalog data?
- **O4 — Does the Maintenance Tracker garage move to the cloud** as part of Phase 6
  accounts, or stay local-only with the visualizer owning the only backend?
- **O5 — Where do vehicle base images come from?** Licensed manufacturer press
  photos, commissioned shoots, or rendered from 3D models. Now the largest
  per-vehicle cost line (§4a), and the answer that most affects whether 3D moves
  earlier than the fast-follow slot.
