# Flop Visuals

Fabric 1.21.11 client mod: smooth rounded HUD, keystrokes, cinematic trails and petal ambience, all in your accent colour.

Press **V** in game to open the menu. Change it in Options › Controls › Key Binds › Flop Visuals.

## What's in it

- **Theme**: accent colour (or rainbow), background, HUD opacity (0% = clear), rounding, outline, accent strip, text colours, shadow, padding, presets (Sky, Amber, Sakura, Mint, Violet, Mono, Clear). Menus follow the theme.
- **HUD**: FPS, coordinates (with nether/overworld conversion), direction, speed, ping, CPS, clock, biome, armor durability, day. Turn each on/off, then **Edit HUD layout** to drag, scroll-resize, right-click to hide. Snaps to edges, centre and other widgets (hold Shift to place freely).
- **Keystrokes**: W A S D, mouse buttons with CPS, space bar. Pressed keys fade into your accent colour. Size, gap, fade speed, backgrounds.
- **Trails**: Stardust, Accent Dust, Feathers, Sparks, Flames, Soul Flames, Hearts, Enchant, Notes, Snow, Cherry, Glow, Totem, Clouds. Density, spread, only when moving, while flying.
- **Petals**: Cherry, Pale Oak, Spore Blossom, Fireflies, Snowflakes, Ash, Crimson, Warped, End Rods, in a bubble around you only. Radius, density, outdoors-only.

Trails and petals use the mod's own hi-res, anti-aliased particle textures (no 8x8 pixel art) and are client-side: only you see them.
- **Sky**: animated glowing topographic contour lines in your accent colour, drawn per pixel by a shader so the lines stay sharp at any resolution. Sky cover, line count, speed, brightness, matching fog, optional Nether/End.

All rounded shapes are drawn at your screen's real resolution (e.g. 1080p) with smoothed edges.

- **Glass ground**: the ground around you mirrors the animated sky like polished glass (reflection %, radius, optional water).
- **Wallpaper**: animated topographic background (or your own PNGs from `config/flop-visuals/wallpapers`) on the title screen and every menu outside a world.
- **Spotify**: now-playing card with album art and progress. One-time setup: create an app at developer.spotify.com with redirect URI `http://127.0.0.1:43821/callback`, paste the Client ID in the menu, click Connect.
- **Menus**: Apple-style settings window with sidebar, grouped cards, iOS switches and sliders, dark/light appearance, background blur, smooth Inter font (SIL Open Font License).

## Building

Push to GitHub and the Actions workflow builds the jar and attaches it to a release.
