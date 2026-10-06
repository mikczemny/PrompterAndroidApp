# Appearance

Version 1.4 adds independent choices for the interface style and colour palette.
Open **Appearance** with the palette icon on the mode chooser or script editor.
On the reading stage, use **Settings → Interface style and colours**. The dialog
shows the current selection, colour swatches and a preview built from the same
components as the actual screens. Changes apply and save immediately; **Done**
closes the dialog.

## Styles

| Style | Window and control treatment | Typography |
| --- | --- | --- |
| Workbench | Flat rectangular frames, strong title bar, inset fields | Monospaced interface |
| Commodore 64 | Double terminal frames and reverse-colour selections | Monospaced interface |
| Windows 3.11 | Raised and sunken bevels, rectangular buttons, title bars | Sans serif |
| Manuscript | Fine frames, restrained panels and spacing | Serif headings; readable body text |
| Modern | Rounded panels and contemporary controls | Sans serif |

These are original application styles inspired by the historical desktops, not
emulators or pixel-exact reproductions. The system font families preserve
legibility and supported language glyphs at Android's text sizes.

## Palettes

| Palette | Behaviour |
| --- | --- |
| Original | Each retro style uses its characteristic colours. Manuscript and Modern follow Android's light/dark setting. |
| Bottle green & gold | Deep green desktop and panels, warm ivory text, gold accents; always dark. |
| Paper | Warm light paper with green ink accents; always light. |
| Graphite | Dark neutral surfaces with cool, light accents. |
| Amber | Dark brown surfaces with amber and cream. |
| Ice | Light blue-grey surfaces with deep blue accents. |
| Plum | Dark plum surfaces with pale rose accents. |

The initial selection is **Manuscript + Bottle green & gold**. Every style remembers
its last palette, including after an application restart. Selecting another style
does not overwrite that choice. Explicit palettes are unaffected by wallpaper
colours or the system light/dark setting.

## Reading and editing

The stage uses the same light-on-black script and sans-serif reading font in
every style. Changing the interface does not reflow the script through a retro
font. The reading highlight, progress and camera frame use a legible version of
the chosen accent. The surrounding controls and settings use the selected
window style and palette.

Appearance lives separately from the per-mode reading settings. Selecting a
style does not reset font size, margins, line spacing, focus band, mirror,
countdown, brightness or remote controls. The camera controller and recognizer
are not recreated by an appearance change.

The editor draft, cursor and language are retained through appearance changes
and when returning from recordings, licences, the reading stage or mode selection.
For a durable script that should remain after closing the app, use **Save** in
the script library as before.

## Code and verification

- `AppearanceSelection` holds the selected style and a palette per style; its
  string encoding tolerates missing, unrecognized and incorrectly typed values.
- `AppearanceStore` persists these values in `appearance_v1` preferences.
- `PaletteSpec` pairs each surface with foreground colours. Pure JVM contrast
  tests cover text, buttons, title bars and the stage accents across the styles
  and palettes in both system modes.
- `LocalAppearance` supplies the current selection without changing the identity
  of the navigation, editor, camera or recording composition.
- `AppWindow`, `AppPanel`, `AppHeader` and the shared buttons provide style-specific
  geometry. Windows controls invert their bevel while pressed.
- Screen tools wrap on narrow displays, lists and settings scroll, and controls
  provide touch targets of at least 48 dp. The mode chooser uses two columns only
  when the available width permits them.

Android compilation and unit tests run in the repository's CI workflow. Final
device checks should include selecting each style, reopening the app to verify
the remembered palette, retaining a draft across navigation, and changing
appearance during a test take without interrupting its audio/video recording.
Those checks require a device or emulator and are separate from JVM tests.
