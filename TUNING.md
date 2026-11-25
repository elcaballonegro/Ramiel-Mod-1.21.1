# Tuning

## Live settings (screen or `ramiel-common.toml`)
| Setting | Default | Range | Notes |
|---|---|---|---|
| Destruction | 1.0 | 0-3 | scales every crater/terrain cut; 0 = harmless |
| Block physics | 1.0 | 0-3 | debris thrown by blasts |
| Impact radius | 96 | 24-250 | blocks |
| Impact charge | 25 s | 5-120 | warning time of the impact strike |
| Damage | 1.0 | 0-5 | everything Ramiel hurts |
| Move speed | 1.0 | 0.25-4 | |
| Aggression | 1.0 | 0.25-4 | higher = shorter pauses between attacks |
| Health | 1.0 | 0.25-5 | multiplies the base pool (3000) |
| Notice range | 192 | 32-512 | blocks |
| Star beam delay | 45 s | 10-180 | final phase |
| Cutscenes | see config | 0-2 | 0 off, 1 key moments, 2 every transformation |
| Ignore players | off | | |
| Mobs flee / attack | on / on | | |
| Damage interrupts | on | | enough damage during the impact charge cancels it |
| Horizon render / Keep chunks | | | |

## Numbers in code (first guesses, untested in game)
Health pool 3000 (`RamielEntity`), body hit x0.25, exposed core x5; railcannon 20% / 50% of max health (2 core shots kill); phase gate at 2/3 HP -> 60 s stun
(floor 30%); shield durability 1500, 600 ticks vulnerable when broken; attack pause 10-20 s / aggression; hover 84 blocks; R = 24 blocks.
Morph feel: `RamielModel` (`SPLIT_OPEN 0.38`, `SPLIT_CLOSE 0.60`, `STAGGER`, `BURST`). Stage lengths: each attack's `duration()`.
Camera: `Films` (distances are in R units; `baseDist()` clamps 2.4-5.5 R).
Item hold transforms: the `display` block in `models/item/*.json`. Tune these first in game (F3+T reloads resources).
Sunburst wheel: `FormPoses.sunburst` (ring radius 0.80 R, bead radius 0.11, central octahedron 0.26, 20 beads; spike lengths 0.9-1.8 R / 0.35-0.55 R). Keep the ring + shields inside 1.0 R or the collision slabs and `rayWheel` will not cover them.
