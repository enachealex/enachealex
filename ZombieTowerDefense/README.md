# 🧟 Zombie Defense

A zombie-themed tower defense game for Android, written in pure Kotlin with no game
engine and no image assets — everything is rendered with the Android Canvas API on a
`SurfaceView` game loop.

## Game modes

- **Campaign** — 8 missions that unlock one at a time as you finish them; each mission
  fields a bigger horde than the last (progress is saved on the device)
- **Survival** — endless waves from the start; how long can you last?
- **Castle vs Nest** — the nest spawns zombies nonstop; send melee troops up the
  path to destroy it while your towers defend the castle

## Gameplay

Pick a mode and a mission or map, then hold the line: the horde marches along the
dirt trail toward your gate. Towers can only be built on the prepared emplacements dotted along the
trail — tap an empty stone pad to choose which class to station there. The map is
a smooth landscape — trees, rocks and buildings block building — and the game fills
the whole screen on any aspect ratio. Every person and creature is a jointed stick
figure: soldiers aim their weapon arm at targets, troops stab, zombies lurch.

- **Waves arrive on their own** — there is no "start wave" button. Wave 1 lands after
  a short build-up and the rest follow on a timer. Once the last zombie of the current
  wave has spawned, a SKIP button appears that pulls the next wave in early for a bonus
- **Deploy troops** ($120) — melee soldiers march from your gate back up the path,
  blocking zombies and fighting hand-to-hand (they carry the charge that damages
  the nest in Castle vs Nest)
- **Pause menu** — Resume / Settings (blood effects, screen shake) / Main Menu

- **Money** — earn cash for every kill plus a bonus for each cleared wave
- **Lives** — you start with 20; each zombie that escapes costs you lives (big ones cost more)
- **Waves** — zombies never get tougher, there are just *more of them*: every zombie
  keeps the same health all game while wave sizes and spawn speed ramp up, and each
  campaign mission multiplies the horde further (x1.0 on mission 1 up to x2.3)
- Every 5th wave ends with a boss

### Maps

| Map            | Difficulty | Twist                                              |
|----------------|------------|----------------------------------------------------|
| The Long Road  | Normal     | The classic snaking path                           |
| River Crossing | Normal     | A river splits the board — the path crosses a bridge, and you can't build on water |
| The Fork       | Hard       | The path splits into two lanes; zombies pick one at random |
| Death Spiral   | Easy       | A very long spiral — lots of time to whittle them down |

### Soldier classes

Towers are soldiers, drawn top-down with class-specific gear. See `TOWERS.md`
for the full roster of implemented and planned classes.

| Class    | Cost | Specialty                                        |
|----------|------|--------------------------------------------------|
| Assault  | $100 | Fast, reliable carbine fire                      |
| Support  | $180 | LMG stream that suppresses (slows) zombies       |
| Engineer | $220 | Long-range launcher with splash damage           |
| Recon    | $250 | Ghillie sniper: huge damage at very long range   |
| Barracks | $200 | Trains a 3-soldier melee squad that holds the nearest trail |

### Upgrades

Each class has **three upgradeable stats**, and a level is only complete once all
three have been bought — then the same three are offered again, one level higher and
more expensive. Five levels per tower.

- **Assault** — Damage +5%, Range +20 (starts at 120), Fire Rate +10%
- **Support** — Fire Rate +8%, Suppression +5%, Damage +5%
- **Engineer** — Damage +7%, Blast Radius +2%, Fire Rate +5%
- **Recon** — Range +20% (starts at 250), Damage +10%, Accuracy +10% (starts at 70/100)
- **Barracks** — Soldier HP +8%, Damage +5%, Respawn Rate -0.5s

Range values are design units scaled by 1.5x on the board: an Assault opens at 180px
(roughly 1.7 tiles) and reaches 330px fully upgraded, while a Recon spans 375px to 750px.

Reaching level 2 and level 4 gives Recon a piercing shot; reaching level 3 and level 5
gives the Barracks a fourth soldier (it starts with three). Recon's accuracy is its hit chance — below 100 some
shots miss, above 100 the surplus becomes critical hits.

Selling a tower refunds 70% of everything invested, upgrades included.

### Zombies

| Zombie      | Trait                                  |
|-------------|----------------------------------------|
| Walker      | Your standard shambler                 |
| Runner      | Fast but fragile (from wave 3)         |
| Brute       | Slow and very tough (from wave 5)      |
| Abomination | Boss — appears every 5th wave          |

## Controls

- **Pick a mode, then a map** on the home screen
- **Tap an empty stone pad** → choose which soldier class to station there
- **Tap a tower** → buy upgrade ranks or sell it (shows its range)
- **SKIP** → pull the next wave in early (only once the current wave has fully spawned)
- **TROOP** → deploy a melee soldier
- **1x/2x** → toggle game speed; pause opens the menu

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
