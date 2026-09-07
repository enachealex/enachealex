# Soldier Class Roster

The master list of tower classes, in the spirit of the Bloons TD tower catalog:
a broad roster where each class has a clear combat identity and an ordered
upgrade path (with deeper tiers and choice-based branches planned). Classes are
pure data (`TowerType` entries), so introducing one is stats + a path + a sprite.

## Implemented

| Class    | Cost | Role                | Path (tiers 1→5) |
|----------|------|---------------------|------------------|
| Assault  | $100 | All-round DPS       | Rapid Fire → Hollow Points → Long Barrel → Rapid Fire II → Hollow Points II |
| Support  | $180 | Crowd slow / DPS    | Ammo Belt → Suppressing Fire → AP Rounds → Suppressing Fire II → Ammo Belt II |
| Engineer | $220 | Splash / area denial| Big Payload → Frag Radius → Auto Loader → Big Payload II → Frag Radius II |
| Recon    | $250 | Single-target burst | Heavy Rounds → Piercing Shot → Heavy Rounds II → Deadeye → Piercing Shot II |
| Barracks | $200 | Melee blocker squad | Recruits → Body Armor → Combat Training → Reinforcements → Veterans |

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
