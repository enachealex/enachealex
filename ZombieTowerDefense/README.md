# 🧟 Zombie Defense

A zombie-themed tower defense game for Android, written in pure Kotlin with no game
engine and no image assets — everything is rendered with the Android Canvas API on a
`SurfaceView` game loop.

## Gameplay

Pick a map, then hold the line: the horde marches along the path toward the exit (☠).
Build towers on the grass tiles to stop them. Survive **20 waves** to save the city,
then keep going in endless mode if you dare.

- **Money** — earn cash for every kill plus a bonus for each cleared wave
- **Lives** — you start with 20; each zombie that escapes costs you lives (big ones cost more)
- **Waves** — zombies never get tougher, there are just *more of them*: every zombie
  keeps the same health all game while wave sizes and spawn speed ramp up
- Every 5th wave ends with a boss

### Maps

| Map            | Difficulty | Twist                                              |
|----------------|------------|----------------------------------------------------|
| The Long Road  | Normal     | The classic snaking path                           |
| River Crossing | Normal     | A river splits the board — the path crosses a bridge, and you can't build on water |
| The Fork       | Hard       | The path splits into two lanes; zombies pick one at random |
| Death Spiral   | Easy       | A very long spiral — lots of time to whittle them down |

### Soldier classes

Towers are soldiers, drawn top-down with class-specific gear. More classes are
planned.

| Class    | Cost | Specialty                                        |
|----------|------|--------------------------------------------------|
| Assault  | $100 | Fast, reliable carbine fire                      |
| Support  | $180 | LMG stream that suppresses (slows) zombies       |
| Engineer | $220 | Long-range launcher with splash damage           |
| Recon    | $250 | Ghillie sniper: huge damage at very long range   |

### Upgrades

There are no generic tower levels — every class has its **own three upgrade tracks**,
each purchasable rank by rank (costs rise with each rank):

- **Assault** — Rapid Fire (fire rate), Hollow Points (damage), Long Barrel (range)
- **Support** — Ammo Belt (fire rate), AP Rounds (damage), Suppressing Fire (stronger slow)
- **Engineer** — Big Payload (damage), Frag Radius (blast radius), Auto Loader (fire rate)
- **Recon** — Deadeye (critical hits, 2.5x), Heavy Rounds (damage), Piercing Shot (shots pass through enemies)

Selling a tower refunds 70% of everything invested, upgrades included.

### Zombies

| Zombie      | Trait                                  |
|-------------|----------------------------------------|
| Walker      | Your standard shambler                 |
| Runner      | Fast but fragile (from wave 3)         |
| Brute       | Slow and very tough (from wave 5)      |
| Abomination | Boss — appears every 5th wave          |

## Controls

- **Pick a map** on the title screen
- **Tap a grass tile** → choose a tower to build
- **Tap a tower** → buy upgrade ranks or sell it (shows its range)
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
Android 8.0 (API 26). Pushes touching this folder also build the APK in CI — grab
it from the workflow run's `zombie-defense-apk` artifact.
