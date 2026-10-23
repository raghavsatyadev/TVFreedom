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
Colours are "#RRGGBB" or "#AARRGGBB". Sizes are in dp.

Blocks:
- Column: children one under another. children, gap (16), align (start|center|end), background (colour).
- Row: children side by side. children, gap (16), align, background.
- Panel: a rounded card around child or children. color, padding (24), radius (28).
- Spacer: empty space. size (24).
- Text: text (string, or {"path":...}), style (display|headline|title|body|label), color.
- Clock: the time as HH:mm. style (display), color.
- Date: today's date. format (Java date pattern, "EEEE d MMMM"), style (title), color.
- Greeting: "Good morning/afternoon/evening", with name if given. name, style (headline), color.
- AppRow: app tiles in one scrolling row. apps, size (small|medium|large), shape (round|square|circle),
  labels (true|false), gap (20), tileColor, focusColor.
- AppGrid: app tiles in rows. Same as AppRow, plus columns (6).
The root's background fills the whole screen (default #101418). Text is light by default, so keep backgrounds dark
or set text colours. Nothing else is supported: no images, buttons or actions; OK on a tile opens that app.
""".trimIndent()
}
