# Lingua Franca · Tutorial

> Minecraft 26.3 · Fabric · Public Beta 0.0.1

## 1. Requirements

| Item | Notes |
|---|---|
| Minecraft 26.3 | exact version |
| Fabric Loader | 0.20.1+ |
| Fabric API | matching 26.3 build |
| Java | 25+ |
| Translation API key | DeepSeek by default; any OpenAI-compatible endpoint works |

## 2. Install

1. Close the game.
2. Put `lingua-franca-0.0.1.jar` into `mods/`.
3. Make sure only one `lingua-franca-*.jar` is present.
4. Launch the game.

## 3. Configure

Press **J** in game, enter your API key, save.

Or edit `config/lingua-franca.json`:

```json
{
  "enabled": true,
  "apiKey": "your-key",
  "baseUrl": "https://api.deepseek.com",
  "model": "deepseek-flash",
  "translateChat": true,
  "translateTooltip": true,
  "translateItemName": true,
  "translateItemLore": true,
  "translateTextDisplay": true,
  "translateAdvancements": true,
  "translateSigns": true,
  "translateTabList": true,
  "translateBossBar": true,
  "tabBossServers": "wynncraft"
}
```

## 4. Modules

| Option | Default | Description |
|---|---|---|
| translateChat | on | server chat |
| translateTooltip | on | hover tooltips |
| translateItemName / translateItemLore | on | item names / lore |
| translateTextDisplay | on | holograms |
| translateAdvancements | on | advancements |
| translateSigns | on | signs |
| translateTabList / translateBossBar | on | tab list / boss bars (servers matching `tabBossServers`) |
| protectedNames | empty | names never translated |

## 5. Cache

Translations are cached per server in `config/cache/translations-server-<scope>.tsv`. Every phrase is translated once and reused. Delete a scope's cache file (with the game closed) to force fresh translations.

## 6. Troubleshooting

- Nothing translated → check `mods`, API key, network; the log should show `[lingua] ready: ...`.
- Some text stays English → it may be drawn by another mod (e.g. Wynntils) that needs raw English; those are skipped on purpose.
- Temporary off switch → press J or set `"enabled": false`.

## 7. Update / Uninstall

Update: replace the jar. Config and cache are kept.
Uninstall: delete the jar (optionally remove `config/lingua-franca*`).
