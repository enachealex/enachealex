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
| Barracks | $200 | $100 | Combat Training +8% | Body Armor +8% | Recruiting +5% |

Level milestones: **Recon** gains +1 piercing shot on reaching level 2 and level 4.
**Barracks** gains another soldier on reaching level 3 and level 5.

**Accuracy** (Recon only) is hit chance out of 100: at 70 roughly three shots in ten
miss outright (a MISS pops up). Once accuracy passes 100 every shot connects and the
excess becomes critical-hit chance at 2.5x damage.

## Planned

| Class          | Est. cost | Role                        | Concept |
|----------------|-----------|-----------------------------|---------|
| Flametrooper   | $200      | Short-range burn DoT        | Cone of fire; burn stacks; upgrades into napalm pools on the path |
| Frost Trooper  | $160      | Hard slow / freeze          | Snap-slow bursts; top tier briefly freezes small zombies solid |
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
- Tier-gated unlocks per mode (Campaign progression unlocks classes)
