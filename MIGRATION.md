# Aetheria Forge 1.8.9 -> Fabric 1.21.0 migration checklist

Status key: [x] scaffolded  [ ] not started  (nothing here is verified in-game yet)

## Forge -> Fabric API mapping
| Forge 1.8.9 | Fabric 1.21 |
|---|---|
| @Mod / FMLInitializationEvent | ClientModInitializer |
| @SubscribeEvent TickEvent.ClientTickEvent | ClientTickEvents.END_CLIENT_TICK |
| RenderGameOverlayEvent | HudRenderCallback (DrawContext) |
| ClientChatReceivedEvent | ClientReceiveMessageEvents.MODIFY_GAME / ALLOW_GAME |
| RenderWorldLastEvent | WorldRenderEvents.LAST / AFTER_TRANSLUCENT |
| GuiScreenEvent / GuiScreen | ScreenEvents, Screen |
| ClientCommandHandler | ClientCommandRegistrationCallback (Brigadier) |
| KeyBinding + ClientRegistry | KeyBindingHelper.registerKeyBinding |
| ItemStack NBT lore/ExtraAttributes | DataComponentTypes (CUSTOM_DATA, LORE) |
| IChatComponent | Text |
| GL11 / Tessellator | DrawContext, MatrixStack, BufferBuilder |
| Config (Forge) / custom JSON | Gson (done) |

## Features
Core: [x] config+profiles+autosave  [x] command registration  [ ] settings GUI  [ ] API/repo manager
Items: [ ] item DB  [ ] item list/recipes  [ ] search bar  [ ] tooltips  [ ] enchant parser  [ ] slot binds  [ ] inventory buttons  [ ] storage overlay  [ ] protections
HUD/Misc: [x] performance HUD  [ ] pet overlay  [ ] cooldowns  [ ] pickup log  [ ] damage formatter  [ ] timers  [ ] notifications  [ ] sign calc  [ ] scoreboard  [ ] incompat warnings
Chat: [ ] filters  [ ] compact  [x] timestamps  [ ] heads  [ ] copy  [ ] animated  [ ] ping  [ ] emoji  [ ] global chat
Dungeons / Mining / Farming / Fishing / Diana / Waypoints / PV / Capes / Party / Network / Privacy: [ ] all not started
