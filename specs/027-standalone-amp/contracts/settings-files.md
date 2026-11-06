# Contract: GuitarFX's settings files

**Folder** (research R6):
- macOS: `~/Library/Application Support/GuitarFX`;
- Windows: `%APPDATA%\GuitarFX`;
- Linux: `$XDG_CONFIG_HOME/guitarfx`, or `~/.config/guitarfx`.

Both files are UTF-8 `key=value` properties. They are written atomically (a temporary file, then a rename), and only from the application's UI thread. The plugin only reads `settings.properties`.

## `settings.properties`

```properties
settings.format=1
devices.input=Rocksmith Guitar Adapter
devices.channel=1
devices.output=MacBook Pro Speakers
volume=0.5
sound.current=Crunch
chain.current=gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on
guitarfx.output.ceiling=-1
guitarfx.output.fade-in=500
…
```

- **Device keys**: missing until the guitarist chooses. A named device that is not present is shown as missing, never replaced by another device.
- **`volume`**: 0 to 1, clamped.
- **`chain.current`**: the chain as it is, which may differ from `sound.current`'s (shown as changed).
- **`guitarfx.<category>.<name>`**: every external parameter.
  - A key that is missing takes its default.
  - A value out of bounds is clamped, and the clamped value is written back at the next save.
  - An unknown key is kept as is and ignored.
- **The sound's on/off state is never stored** (FR-009).

## `sounds.properties`

```properties
sounds.format=1
ready.Clean=                      # empty: as shipped
ready.Crunch=gate:on,-60,150|amp:crunch,6,5,5,5,6|cab:on    # changed by the guitarist
own.1.name=My lead
own.1.chain=…
own.2.name=…
```

The scheme follows `AmpSoundStore`'s keys (`ready…`, `own.<n>.name`, `own.<n>.chain`) so the two stores read alike. They are not shared files (owner, Q2 B).

## Damaged or newer files

| Case | Result |
|---|---|
| `*.format` greater than 1 | the file is left untouched, the defaults are used for this session, a message says the file comes from a newer GuitarFX, and nothing is written over it until the guitarist confirms |
| unreadable, or a malformed value | the file is copied to `<name>.damaged-<yyyymmdd-hhmmss>`, the readable values are kept, the others take their defaults, and a message names the copy |

## External parameter keys

- The registry in `guitarfx-dsp/src/params.rs` is the single list: key, category, unit, default, min, max, and the text keys of its name and tooltip.
- `docs/guitarfx/parameters.md` is generated from it and checked by a test, so it is never edited by hand.
- The *Advanced* window, the settings reader and the documentation all read the same registry.
