# CLAUDE.md: working on LemonCosmetics

LemonCosmetics is a client-only Fabric mod (Minecraft 26.1.x, loader-only, Java 25) that draws
LemonCloud's cosmetics on the player's own items, armour and head, on their screen only. Read
[PLAN.md](PLAN.md) for the design and what has (and has not) been verified.

It is a separate mod from Nethermine (`../prisonPlayer`) and Nylah (`../Nylah`) and must stay that way:
no references to them, no reading their files or state, no shared code.

## Build, test, deliver

`JAVA_HOME` must be Temurin 25 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot`).

```bash
./gradlew build                 # build/libs/lemoncosmetics-<version>.jar; runs the tests
powershell -File deliver.ps1    # both CurseForge instances (waits while one is running, one jar, hash-checked)
./gradlew runClient             # dev client; run/client/resourcepacks/lemoncloud.zip + options.txt enable the pack
```

Bump `mod_version` in `gradle.properties` for every delivered change.

## Where things live

| Concern | Files |
|---|---|
| Reading the pack into looks | `catalogue/CatalogueParser` (pure), `catalogue/Catalogue` (live, rebuilt when the loaded packs change) |
| Deciding what to draw | `Dresser` (`dress` for items, `dressHumanoid` for armour + head), `Rules` (pure) |
| Hooks | `mixin/ItemModelResolverMixin` (every item drawn), `mixin/HumanoidMobRendererMixin` (player armour/head) |
| Menu, keys, config | `ui/CosmeticsScreen` (K), `Keybinds`, `Config` (`config/lemoncosmetics.json`) |
| Logo, icon, banner | `tools/logo/make_logo.py` (Pillow) |

## Rules that keep it safe

1. **Nothing may talk to the server.** No packets, chat, commands, container clicks or inventory writes.
   `SafetyTest.nothingInTheModCanTalkToTheServer` audits the bytecode.
2. **Never change a real item.** Only copies are dressed; `SafetyTest.dressingOnlyChangesCopies` pins every
   `ItemStack.set` call site. Add a new one only on a copy, and update the test deliberately.
3. **Only the local player's things.** Item owner / entity must be `Minecraft.getInstance().player`.
4. **A look applies only when the loaded packs offer it** (`Catalogue.find` / `hasArmour`), so rules go
   inert off LemonCloud.
5. **Verify every Minecraft API against the 26.1 jar** (`javap` on
   `~/.gradle/caches/unimined/net/minecraft/minecraft/26.1/minecraft-26.1-client.jar`) before writing code.
6. **The server's assets never go in this repo.**
