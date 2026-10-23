# Config v2

A config is a program. Calling it, from the command prompt or the UI, spawns a process. The process runs one or more iterations. In each iteration it analyzes its input and runs operations on the result.

Each config is a file, `configs/<id>/config.toml` in the VScan folder, described in [files.md](files.md). Field names are snake_case in TOML (`max_completion_tokens`) and camelCase in Kotlin (`maxCompletionTokens`). The Kotlin classes live in `core/Config.kt` and `core/config/`. The id is the folder's name, so the file has no `id` field.

```toml
name = "Bus line reader"
command = "blr"                       # optional, the name to call it by from the command prompt
description = "Reports the line number of approaching buses"   # optional

# --- Parameters -------------------------------------------------------------

# Every config has the implicit parameter  image / -i  (ImageSource, default $default).
# Declare it only to change its default or description.
[[parameters]]
name = "object"
type = "String"                       # String | Int | Double | Bool | Image | ImageSource
positional = true                     # false = named only (--object / -o)
short = "o"                           # optional
rest = true                           # positional only, the last one: takes the rest of the line
default = "keys"                      # optional, command-language text; no default = required
description = "What to look for"

# --- Lens -------------------------------------------------------------------

[lens]
operations = [ { type = "zoom", zoom = 2.0, center_y = 0.4 } ]   # crop | zoom | downscale
applies_to = "image"                  # image: the image parameter only | all: every ImageSource parameter

# --- Analyzer ---------------------------------------------------------------

[analyzer]
type = "llm"                          # the only type so far
model = "vscan-gpt-4o"
system_prompt = "..."                 # template
user_prompt = """
Read the line number of the bus in the image.
{% if lastOutput %}Last time you reported {{ lastOutput }}.{% endif %}
Answer with the number only, or None if there's no bus."""
max_completion_tokens = 300
reasoning_effort = "low"              # optional: none | minimal | low | medium | high | xhigh
append_image = true                   # append the image parameter unless the prompt places an image itself

# Each analyzer type detects changes its own way; this is the LLM one.
[analyzer.change_detection]
mode = "app_driven"                   # app_driven: changed when the output differs from lastOutput
                                      # llm_driven: the model signals it with the change marker
change_marker = "Unchanged"
change_marker_meaning = "no_change"   # no_change | change
change_marker_match = "exact"         # exact | wildcard (* ?) | regex

# --- Operations -------------------------------------------------------------

# Run in order after every iteration; each is skipped when its condition is false.
[[operations]]
type = "say"
condition = "changed && lastOutput!='None'"   # optional JavaScript expression
text = "Bus {{ lastOutput }}"                 # template
priority = "high"                             # critical | high | normal | low

[[operations]]
type = "play"
sound = "beep"                        # optional; without a sound, still useful with stop_others
repeat = 1                            # 0 = loop
volume = { value = 0.8 }              # 0 to 1, full when not set
pan = { extract = "json", path = "x" }    # -1 to 1, centered when not set
instance = "process"                  # process: one instance per sound and process; a looping one keeps
                                      #   playing when played again, taking the new volume and pan | new
stop_others = false                   # stop this process' other sounds first

[[operations]]
type = "vibrate"                      # replaces the current vibration, the device has only one
pattern = [0, 100, 50, 100]           # alternating off / on milliseconds
repeat = 1                            # 0 = loop

[[operations]]
type = "toast"
text = "{{ lastOutput }}"

# --- Lifetime ---------------------------------------------------------------

[lifetime]
interval = 2.0                        # seconds, 0 = single-shot
capture = "auto"                      # auto: photo when single-shot, stream frame when looping | photo | frame
flashlight = "default"                # default | on | off (the torch while looping, the flash for photos)
iteration_condition = "..."           # optional JavaScript expression
max_iterations = 0                    # 0 = unlimited
max_duration = 0                      # seconds
max_tokens = 0
```

Default values may be omitted. The defaults make up the built-in Base config.

## Sounds and vibrations

A sound is looked up in the config's `assets` folder, then in the global `assets` folder, then among the sounds built into the app: `beep`, `low-beep`, `high-beep` and `tick`. Names may omit the extension and may lead through subfolders. Sounds play at the accessibility volume or the media volume, following the speech setting.

When a process finishes, its last sounds and vibrations play out; only looping ones are stopped. A killed process stops all of them.

## Number values

Operation settings such as `volume` and `pan` take one of these forms:

| Form | Value |
|---|---|
| `{ value = 0.8 }` | a constant |
| `{ extract = "number" }` | the first number in `lastOutput` |
| `{ extract = "json", path = "objects.0.x" }` | the number at a dot-separated path in a JSON `lastOutput` |
| `{ extract = "regex", pattern = "x=(\\d+)" }` | the first group of the first match, or the whole match without groups |
| `{ expression = "..." }` | a JavaScript expression |

## LLM change detection

| Mode | The output is a change when… |
|---|---|
| `app_driven` | it differs from `lastOutput` (outputs are trimmed first). The prompt must make the model answer consistently. |
| `llm_driven`, meaning `no_change` | it does **not** match the marker. |
| `llm_driven`, meaning `change` | it matches the marker. The whole output, any marker prefix included, becomes `lastOutput`. |

When an iteration is not a change, `lastOutput` keeps its previous value.

## Process variables

These are visible to templates and expressions. Templates see only the process scope. Globals reach a process through parameter defaults, such as `--image=$glasses`. Until the command language arrives, the globals are the image sources `$default` (the current camera) and `$lastImage` (the image the foreground process took last, or the one shared into the app, as it was when the config was called).

Scans of the foreground process count as changes even when the output stays the same, unless the setting "Report every scan" is off. `lastOutput` and `history` still change only with the output. Looping always uses the change detection.

| Variable | Meaning |
|---|---|
| parameters | by name, e.g. `object`, `image` |
| `lastOutput` | the last *reported* output, `null` before the first report. In the prompt it's the previous one; in operations it includes this iteration's |
| `rawOutput` | what the analyzer literally returned this iteration (operations only) |
| `changed` | whether this iteration has something new (operations only) |
| `history` | past reported outputs, oldest first, capped at 20 |
| `iteration` | 1, 2, … |
| `elapsed` | seconds since the process started |

## Migration from v1

The flat configs of versions up to 0.2.x, stored under the key `configurations`, are converted on the first load, and written as files. The old key is kept as a backup, as is the JSON of the configs stored under `configs` before the files.

| v1 field | v2 |
|---|---|
| name | `name` |
| prompts, model, maxCompletionTokens, reasoningEffort | `[analyzer]` of type `llm`, in `app_driven` mode |
| flashlightMode | `lifetime.flashlight` |

The prompts are escaped with `Template.escape`, so `{{`, `{%` and `{#` stay literal. Migrated configs get no command, no extra parameters, `interval = 0` and a single `toast` operation of `{{ lastOutput }}`. This matches the behavior of v1. The built-in File description config has no operations, because its output is used as a filename.
