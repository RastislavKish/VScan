# The VScan folder

VScan keeps its configs, providers, settings and global variables in one folder:

```
VScan/
  configs/
    find-keys/            the config's id
      config.toml
      assets/             optional: the config's own sounds and other files
    bus-lines/
      config.toml
  assets/                 optional: files for all configs
  providers.toml          the API providers, without their keys
  secrets.toml            the API keys
  settings.toml           the global settings and button bindings
  memory/                 the global variables
    variables.toml        strings, numbers and bools
    images/door.jpg       images, by variable name
```

The schema of `config.toml` is in [config-v2.md](config-v2.md). Field names are snake_case. Fields left out take their default values, and unknown fields are ignored.

## Where the folder is

Without a linked folder, the files are in the app's private storage, and only VScan can access them.

Linking a folder in the settings (Files, Link a folder) makes a folder of your choice the source of truth, for example one synchronized across devices or kept in git.

- **An empty folder** gets a copy of the files on the device.
- **A folder with VScan files already**, for example one set up on another device, replaces the configs, providers and settings on the device, after a confirmation.

VScan keeps a copy of the linked folder on the device and reads that copy. It brings in the folder's changes when the app comes to the foreground, and on the command `reload`. Changes made in the app are written to both. While the folder is unavailable, the app works with its copy; changes made meanwhile are copied to the folder once it's back.

Unlinking the folder leaves the files on the device, and the folder as it is.

Files and folders starting with a dot, such as `.git`, are ignored.

## Editing the files

- Saving a config, a provider or the settings in VScan rewrites the whole file, leaving out comments. Keep comments in files you edit only by hand.
- A config's id is the name of its folder, so a new folder with a `config.toml` is a new config. Renaming a config in the app keeps its folder.
- Deleting a config in the app deletes its folder, assets included.
- The built-in configs Base, File description and Find are defined in the app. A folder with one of their ids is reported as broken.
- A file that can't be read is reported in a dialog naming the file and the line of the error, which stays until you confirm it with OK, and the app doesn't overwrite the file. A broken config stays in the config list until it's fixed.

## Assets

A config's sounds are looked up in its own `assets` folder, then in the global `assets` folder, then among the sounds built into the app (`beep`, `low-beep`, `high-beep` and `tick`). Names may omit the extension and may lead through subfolders, for example `sound = "alerts/door"`. Changed files are reloaded the next time they're played.

## Memory

The global variables assigned on the command prompt (see [commands.md](commands.md)) are kept in `memory/`: strings, numbers and bools in `variables.toml`, images as their original files in `images/`, named after their variables. Assigning or removing a variable writes the files right away, and images are read only when used. To keep a device's variables to itself, leave `memory/` out of its synchronization.

## Secrets

`secrets.toml` holds the API keys of the providers, by provider id:

```toml
[providers.openai]
api_key = "sk-..."
```

Keep it private: leave it out of git repositories and of anything you share. Without it, a shared `providers.toml` still describes the providers, and each device can use its own keys.

## Settings

`settings.toml` holds all the settings, including the button bindings:

```toml
use_sounds = true
report_every_scan = true
default_config = "base"

[volume_up_press_action]
type = "run_command"      # scan_with_active_config | scan_with_config | consult_config | ask
command = "fd keys"       # | set_system_prompt | set_user_prompt | stop_speech | run_command
```

To keep a device's own settings while synchronizing the rest, leave `settings.toml` out of its synchronization. The linked folder itself is remembered per device.
