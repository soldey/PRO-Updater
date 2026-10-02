# PRO-Updater

The installer and updater for the settings of the **Skyblock PRO** modpack. A fresh install gets
the pack's full setup before the main menu shows up. A pack update adds only what is new, so your
own settings, keys and slot bindings stay as they are.

Supports **Minecraft 26.1.x and 26.2**, with a separate jar per version - take the one matching
your game. Needs Fabric Loader 0.19.3+, Fabric API and Fabric Language Kotlin; Mod Menu is
optional. It is made for Skyblock PRO and comes with it; outside the pack it only shows its
screen and does nothing on its own.

## Why it exists

When a pack updates, Modrinth App deletes every file the old version shipped and unpacks the new
ones. If a pack ships mod configs directly, every update resets every mod setting to the pack's,
along with anything a mod keeps in those files. Skyblock PRO therefore ships its settings in
`proupdater/defaults/` instead of `config/`, and PRO-Updater copies them over itself.

## How it behaves

Everything runs **before the game starts**, before Minecraft reads `options.txt` and before the
other mods load their configs. Mods write their configs back when the game closes, so changing
files while you play would not stick.

| Situation | What happens |
| --- | --- |
| Fresh install | All pack settings are copied, then the setup steps below run with their defaults - the author's preset. If Modrinth App already wrote an `options.txt` (game options sync), your values stay and only missing keys come from the pack. Then the welcome screen asks how to go on. |
| Pack update | Only files you do not have yet are added. Nothing you have is overwritten. New setup steps are offered on the screen, not applied. |
| Coming from Skyblock PRO 3.3.x or older | Handled like an update. |
| Same pack version | Nothing, apart from what you queued on the screen. |

### The first start

After a fresh install the game opens a welcome screen instead of the main menu, with three choices:

* **Author's preset** - keep the setup that is already in place and play. No restart.
* **Customize the author's preset** - the setup wizard below, with the author's choices as the
  starting point. On the next start the pack settings are laid out again with only the steps you
  picked, using your keys.
* **Continue without setup** - on the next start the install is undone and every mod keeps its own
  defaults. Later pack updates add no pack settings either; "Apply pack settings" brings them in.

The screen shows up again on every start until a choice is made, and never after.

Before any run that overwrites or deletes a file, the old files are zipped into
`proupdater/backups/` (the newest 15 are kept).

## Setup steps

| Step | What it does |
| --- | --- |
| Loadout keys: Skyblocker | Turns on Skyblocker's loadout helper and binds loadouts 1-12 to `1`-`9`, `0`, `-`, `=` inside the Loadouts window, plus `` ` `` to open Loadouts (`/ld`, a NoFrills command key). The same keys in IQ Addons, SkyHanni, Odin, NoFrills and Firmament are turned off so only one mod reacts. |
| Command keys | NoFrills custom keybinds: `U` - `/eq`, `L` - `/wd`, `-` - `/trades`, outside of menus only. Your other binds stay. |
| Clear NoFrills slot bindings | Empties the slot bindings (the feature and its key stay). Fresh install and button only, **never** after an update. |
| Resource packs | Turns on the pack's resource packs in order, without a resource reload. Packs you turned on yourself stay on, above them. |
| Attack/Destroy: Hold | Sets Attack/Destroy back to Hold. |

## The screen

Open it from Mod Menu or with `/proupdater`. It shows which pack settings are installed and has:

* **Setup...** - a wizard through the steps above, one page each: what the step does, whether
  to apply it, its keys. A summary at the end queues the chosen ones.
* **Apply pack settings** - every settings file replaced with the pack's and the default steps,
  i.e. the setup as the pack author has it. The replaced files are backed up first.
* **Make a backup** - saves `options.txt`, the pack's configs and every file a step changes,
  right away. Not all of `config/`: most of it is caches.
* **Restore a backup...** - one of the newest backups, yours or automatic.

Everything except making a backup is **queued** and happens on the next game start.

## Files

| Path | What |
| --- | --- |
| `proupdater/defaults/` | the pack's settings, replaced by Modrinth App on every update |
| `proupdater/defaults/manifest.json` | `{"version": "3.4.0", "files": {"config/…": "<sha1>"}}` |
| `proupdater/defaults/resourcepacks.json` | resource packs to turn on, bottom first: `["vanilla", "file/FurSky Reborn.cats.zip"]` |
| `proupdater/state.json` | installed pack version, files copied, steps applied |
| `proupdater/pending.json` | what you queued on the screen |
| `proupdater/backups/*.zip` | backups |

## Licence

Copyright (C) 2026 kmsold.

PRO-Updater is free software: you can redistribute it and/or modify it under the terms of the
**GNU Lesser General Public License, version 3 or later**, as published by the Free Software
Foundation. See `LICENSE` for the LGPL text and `COPYING` for the GPL text it builds on. In
short: use it freely, but if you publish a modified version, publish its source too.

It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

## Something broken?

Send `logs/latest.log` from your game folder to **soldey** on Discord - the file itself, not a
screenshot, and right after the problem happens: `latest.log` is overwritten every time the game
starts. Every run is logged under `PRO-Updater`, file by file.
