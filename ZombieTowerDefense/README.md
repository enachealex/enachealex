# 🧟 Zombie Defense

A zombie-themed tower defense game for Android, written in pure Kotlin with no game
engine and no image assets — everything is rendered with the Android Canvas API on a
`SurfaceView` game loop.

## Gameplay

The horde marches along a winding dirt path toward the exit (☠). Build towers on the
grass tiles to stop them before they get through. Survive **20 waves** to save the
city, then keep going in endless mode if you dare.

- **Money** — earn cash for every kill plus a bonus for each cleared wave
- **Lives** — you start with 20; each zombie that escapes costs you lives (big ones cost more)
- **Waves** — start each wave when you're ready; every 5th wave ends with a boss

### Towers

| Tower  | Cost | Specialty                         |
|--------|------|-----------------------------------|
| Rifle  | $100 | Fast, reliable all-rounder        |
| Frost  | $150 | Slows zombies in their tracks     |
| Flame  | $200 | Sets zombies on fire (damage over time) |
| Sniper | $250 | Huge damage at very long range    |

Each tower can be upgraded twice (more damage, range and fire rate) or sold for 70%
of what you invested.

### Zombies

| Zombie      | Trait                                  |
|-------------|----------------------------------------|
| Walker      | Your standard shambler                 |
| Runner      | Fast but fragile (from wave 3)         |
| Brute       | Slow and very tough (from wave 5)      |
| Abomination | Boss — appears every 5th wave          |

Zombies get tougher every wave, so keep building and upgrading.

## Controls

- **Tap a grass tile** → choose a tower to build
- **Tap a tower** → upgrade or sell it (shows its range)
- **START WAVE** → send in the next wave
- **1x/2x** → toggle game speed, **‖** → pause

## Building

Open the `ZombieTowerDefense` folder in Android Studio and press Run, or from the
command line:

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+ and the Android SDK (compileSdk 35). Minimum supported device:
Android 8.0 (API 26).
