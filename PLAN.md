# LemonCosmetics: the plan (1.0.0)

The ask (2026-10-08): "a mod that will allow us to use the server's cosmetics, but locally only. So I can
change my pickaxe on the server to one of the ones I like, but it will just change client side."

## Decisions

1. **A separate mod.** Its own repo (`Y:\Github\LemonCosmetics`), its own id (`lemoncosmetics`), no code
   shared with Nethermine (`../prisonPlayer`) or Nylah (`../Nylah`). Fabric, loader-only (no Fabric API),
   client-only, Minecraft 26.1.x, Java 25. Build setup copied from Nylah (unimined, mojmap).
2. **The cosmetics come from the server's own pack, at runtime.** Never bundled: the mod parses the
   loaded `items/*.json` (each `range_dispatch` on `custom_model_data` entry is one look) and
   `equipment/*.json` (each is a worn armour set). It follows pack updates, and on any other server the
   list is empty, so no rule can apply there.
3. **Draw a dressed copy; never touch the real item.** One hook, `ItemModelResolver.appendItemLayers`, sees
   every item the game draws (hand, hotbar, inventory, third person, worn on the head). For an item whose
   owner is the local player and that a rule matches, it resolves a copy with `item_model` = the look's
   item definition and `custom_model_data` = its number. Any item can wear any look.
4. **Armour and head after the vanilla extract.** `HumanoidMobRenderer.extractHumanoidRenderState` (TAIL)
   is the last word on the player's render state. Armour: the equipment stack copy gets an `Equippable`
   with the chosen asset (e.g. `custom:bee`). Head: `headItem` is re-resolved with the chosen look,
   placed by vanilla's head-slot transform (so wings sit on the back, as the pack intends).
5. **Only yours.** `ItemOwner.asLivingEntity() == mc.player` for items; `entity == mc.player` for armour.
6. **Safety is tested, not trusted.** `SafetyTest` audits the bytecode: no `Connection`,
   `ClientPacketListener`, `MultiPlayerGameMode` or serverbound packet, no inventory writes, and
   `ItemStack.set` only inside `Dresser.wear` / `Dresser.armour` (both on copies).

## Verified (1.0.0)

- Every Minecraft API checked against the 26.1 client jar with `javap` before use.
- Unit tests: the parser on hand-made JSON and on the real LemonCloud pack (thousands of looks, unique
  keys, all 49 armour sets with four piece icons), the rule picker, the safety audit.
- A dev client with the LemonCloud pack enabled: all mixins applied, and the live catalogue built
  2,685 looks and 49 armour sets.

## Not yet verified

- **Seeing it in a world.** Nobody has yet looked at a dressed pickaxe, armour or hat in game. The
  first in-game run is the real test: hold your pickaxe, press K, click **+ Item in my hand**, pick a look.

## Later, if wanted

- A second "back" slot so wings and a hat can be worn together (needs its own layer).
- The 26.2 line (Fabulously Optimized 14), the same way Nylah carries two lines.
- Looks on other players' items (off by design for now).
