# Soldier Class Roster

The master list of tower classes, in the spirit of the Bloons TD tower catalog:
a broad roster where each class has a clear combat identity and an ordered
upgrade path (with deeper tiers and choice-based branches planned). Classes are
pure data (`TowerType` entries), so introducing one is stats + a path + a sprite.

## Upgrade system

Every class has exactly **three upgradeable stats**. Each level offers one purchase
of each; buying all three promotes the tower to the next level, where the same three
stats are offered again at a higher price (`upgrade cost x level`). Five levels.

| Class    | Cost | Upg. base | Stat 1 | Stat 2 | Stat 3 |
|----------|------|-----------|--------|--------|--------|
| Assault  | $100 | $60  | Damage +5% | Range +20 (from 120) | Fire Rate +10% |
| Support  | $180 | $90  | Fire Rate +8% | Suppression +5% | Damage +5% |
| Engineer | $220 | $110 | Damage +7% | Blast Radius +2% | Fire Rate +5% |
| Recon    | $250 | $120 | Range +20% (from 250) | Damage +10% | Accuracy +10% (from 70) |
| Flame.   | $200 | $100 | Burn Damage +8% | Incendiary Fuel +6% | Range +15 (from 140) |
| Barracks | $200 | $100 | Soldier HP +8% | Damage +5% | Respawn Rate -0.5s |

### Promotions

A class can have a successor it turns into once fully upgraded. The promotion is
offered on the tower panel in place of the maxed-out upgrade rows; taking it swaps
the tower in place, restarts it at level 1 with the successor's own three stats, and
rolls everything already spent into the new tower's sell value.

| From | To | Price | Unlocks |
|------|----|-------|---------|
| Support (maxed) | **Frost Trooper** | $600 | A far harder slow, an area burst, and freezing |

| Class | Upg. base | Stat 1 | Stat 2 | Stat 3 |
|-------|-----------|--------|--------|--------|
| Frost Trooper | $160 | Freeze Power +6% | Damage +8% | Chill Duration +10% |

Frost base stats: range 260 design units, 18 damage at 1.6/s, a 90px burst, a 45%
slow for 2.5s. **Freeze** stops a zombie dead: it lands on the target from level 3,
and from level 5 it catches everything in the burst.

All range figures are design units; the board applies a global `RANGE_SCALE` of
1.5x, so Assault opens at 180px (about 1.7 tiles) and a maxed Recon reaches 750px.

Level milestones: **Recon** gains +1 piercing shot on reaching level 2 and level 4.
**Barracks** fields 3 soldiers from the moment it is built and a 4th on reaching the
final level; its respawn timer starts at 6s and floors at 1.5s.

**Incendiary Fuel** (Flametrooper only) alternates by the level it is bought at:
odd levels (1, 3, 5) add +6% burn *time*, even levels (2, 4) add +6% burn *damage*.

**Flametrooper milestones:** at level 3 the jet widens into a cone that washes every
zombie in front of it; at level 5 it leaves a burning patch of ground behind that
keeps damaging anything walking over it for 3 seconds.

**Accuracy** (Recon only) is hit chance out of 100: at 70 roughly three shots in ten
miss outright (a MISS pops up). Once accuracy passes 100 every shot connects and the
excess becomes critical-hit chance at 2.5x damage.

## Planned

| Class          | Est. cost | Role                        | Concept |
|----------------|-----------|-----------------------------|---------|
| Tesla Trooper  | $300      | Chain damage                | Lightning arcs between clustered zombies; upgrades add chain count and stun |
| Minigunner     | $350      | Sustained shred             | Slow spin-up to extreme fire rate; loses target = spins down |
| Demolitionist  | $400      | Heavy artillery             | Slow, huge splash; can crack Brute armor; top tier = MOAB-class boss damage |
| Medic          | $180      | Troop healer                | Heals nearby deployed troops; top tier revives one fallen troop per wave |
| Commando       | $450      | Aura buffer                 | Boosts damage/fire rate of adjacent towers (BTD village-style) |
| Marksman       | $320      | Camo/priority sniper        | Targets highest-HP zombie on the whole map regardless of range |
| Grenadier      | $150      | Cheap lobbed splash         | Budget area damage with bouncing grenades |
| K9 Handler     | $220      | Melee summoner              | Periodically releases an attack dog that fights on the path like a troop |

## Deployables (not towers)

| Unit  | Cost | Role |
|-------|------|------|
| Troop | $120 | Melee blocker — marches up the path from your gate, blocks and fights zombies; in Castle vs Nest, detonates a charge on the nest |

Planned: Riot Shield trooper (pure blocker, high HP, no damage), Sapper
(runs the path and drops mines), Squad drop (3 troops at once, discounted).

## Back burner

- **Clearing scenery** — let towers/troops shoot down trees and rocks to open up
  more ground. Parked until the pad layout has been played in; if pads stay fixed,
  clearing would instead reveal extra pads rather than free-form space.

## Tier system roadmap

- Deeper paths (7+ tiers) with escalating visuals on the soldier sprite
- Branch choices: at certain tiers pick one of two mutually exclusive upgrades
  (e.g. Recon tier 4: Deadeye crits *or* Shredder pierce)
- More promotions, one per base class (Assault -> Minigunner, Engineer ->
  Demolitionist, Recon -> Marksman, Flametrooper -> Napalm Specialist)
- Tier-gated unlocks per mode (Campaign progression unlocks classes)
