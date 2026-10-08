<p align="center"><img src="docs/banner.png" alt="LemonCosmetics: a crowned pixel-art lemon beside the LEMON COSMETICS wordmark" width="100%"></p>

<p align="center">
  <img alt="Minecraft 26.1.x" src="https://img.shields.io/badge/Minecraft-26.1.x-5AB4F0">
  <img alt="Fabric" src="https://img.shields.io/badge/loader-Fabric-D9B48A">
  <img alt="Client side" src="https://img.shields.io/badge/side-client%20only-FFD23F">
  <img alt="Version" src="https://img.shields.io/badge/version-1.0.0-28221E">
</p>

**LemonCosmetics** lets you wear any of LemonCloud's cosmetics, on your own screen.

Like the Arcanist Pickaxe but own a plain one? Pick it, and your netherite pickaxe is drawn as the Arcanist Pickaxe: in your hand, on your hotbar, in your inventory, in third person. Same for your armour (any of the 49 sets) and your head (hats, wings, backpacks). It all happens in your game's drawing only. The server, and everyone else, still sees your real items.

## How it works

LemonCloud's resource pack (the one the server makes you download) already holds every cosmetic. Each is selected by a base item plus a `custom_model_data` number: the Arcanist Pickaxe is `netherite_pickaxe` with 29. LemonCosmetics reads that pack while you play, lists every look in it, and when the game is about to draw one of **your** items it hands the renderer a copy carrying the look you chose.

- **Nothing is sent to the server.** No packets, no commands, no clicks. A test audits the compiled mod for any way to talk to the server and fails the build if it finds one.
- **Your real items never change.** Only a throwaway copy is drawn. Tooltips, durability and enchant glint all come from the real item.
- **Only your items.** Other players' items look exactly as normal.
- **Only on LemonCloud.** The list is built from the loaded packs. When the server's pack isn't loaded (another server, singleplayer), the list is empty and every look switches itself off, so your pickaxe never turns into a plain dye somewhere else.

## Using it

Press **K** in game.

- **Left:** your looks. Hold an item and click **+ Item in my hand** to give that kind of item a look. Below that are your four armour slots and your head.
- **Right:** every look the pack offers for the selected entry, best matches first (pickaxe skins for a pickaxe). Search by name or set, flip **Show: all** to pick anything, and click a look to wear it. **No look** removes it.
- **Only if named:** give one specific item its own look, e.g. only the pickaxe called "Ethan's Pick".
- **Armour:** *Even when empty* shows the set without wearing armour; *Icons too* restyles the pieces' inventory icons to match.
- **Head:** a hat, wings or backpack. *Hides my helmet* decides whether your real helmet still shows.
- **Looks: on/off** switches everything off at once. There is also an unbound *Cosmetics on / off* key.

Keys can be changed under **Options → Controls → LemonCosmetics**. Choices are saved in `config/lemoncosmetics.json`.

## Install

Fabric loader 0.18.6 or newer, Minecraft 26.1 to 26.1.2. No Fabric API needed. Drop `lemoncosmetics-<version>.jar` into your `mods` folder.

## Building

Temurin 25 (`JAVA_HOME`), then:

```bash
./gradlew build      # build/libs/lemoncosmetics-<version>.jar, runs the tests
powershell -File deliver.ps1   # installs into both CurseForge instances
```

The tests include `CatalogueParserTest.theRealLemonCloudPack`, which parses the newest LemonCloud pack in the instances' `downloads` folders. It skips loudly when none is present.

## Licence

MIT. The cosmetics are LemonCloud's and are never stored in this repository; the mod reads them from the pack your game already downloaded.
