package io.github.raghavsatyadev.tvfreedom.shell

/**
 * How to write a design for this TV, in words an AI follows. The phone asks for it (content call catalog)
 * before writing or fixing a design, so it always matches the shell that is on the TV.
 */
object Guide {
    val TEXT = """
A design is a JSON array of A2UI v0.9 messages:
1. {"version":"v0.9","createSurface":{"surfaceId":"home","catalogId":"${TvCatalog.ID}"}}
2. {"version":"v0.9","updateComponents":{"surfaceId":"home","components":[ ...parts... ]}}
Each part is {"id":"...","component":"<Block>", ...properties}. The screen starts at the part with id "root".
Parts name their children by id in "children" (a list) or "child" (one). Each id is used once.
The TV's apps are in the data at /apps as [{"pkg":"...","label":"..."}]. Bind them with {"path":"/apps"},
or list package names, e.g. ["com.netflix.ninja","com.google.android.youtube.tv"]; apps the TV lacks are skipped.
The remote moves between app tiles, so the design needs at least one AppRow or AppGrid with apps the TV has.

Look: Material 3 Expressive, dark theme. The root's "seed" (a colour like "#6750A4") and "scheme"
(${Tone.SCHEMES.joinToString("|")}; default tonalSpot) make every colour. A colour property is a role name or
"#RRGGBB"/"#AARRGGBB"; prefer roles so the design stays in tune. Roles: ${Tone.ROLES.keys.joinToString()}.
Pair a container with its "on" role (primaryContainer with onPrimaryContainer). Sizes are in dp.
Shapes: ${Shapes.NAMED.keys.joinToString()}; tiles also take "auto" (each app its own shape) and "square".
Type is Google Sans Flex; weight is 100-1000.

Blocks:
- Column: children one under another. children, gap (16), align (start|center|end), background (role or colour).
- Row: children side by side. children, gap (16), align, background.
  The root may also set seed and scheme; its background (surfaceDim) fills the whole screen.
- Panel: a card around child or children. color (surfaceContainer), padding (28), radius (32),
  shape (rounded, or a shape name: the card turns into that shape and stays square; good around a Clock).
- Spacer: empty space. size (24).
- Text: text (string, or {"path":...}), style (${Renderer.TYPE.keys.joinToString("|")}; body), weight, color.
- Clock: the time; its weight follows the time of day. style (display), color.
- Date: today's date. format (Java date pattern, "EEEE d MMMM"), style (title), weight, color.
- Greeting: "Good morning/afternoon/evening", with name if given. name, style (headline), weight, color.
- AppRow: app tiles in one scrolling row. apps, size (small|medium|large), shape (auto), labels (true|false),
  icons (mono: one-colour icons, the M3E look | color: the apps' own icons), gap (24),
  tileColor (primaryContainer), iconColor (onPrimaryContainer), focusColor (primary), focusIconColor (onPrimary),
  labelColor (onSurfaceVariant), focusLabelColor (onSurface).
  A focused tile rounds into a circle, grows and gets a ring in focusColor.
- AppGrid: app tiles in rows. Same as AppRow, plus columns (6).
Nothing else is supported: no images, buttons or actions; OK on a tile opens that app.
""".trimIndent()
}
