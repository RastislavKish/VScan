# The command prompt

The field on the Scan screen is the command prompt. A line is one command, a config to start, or an assignment. Command output is announced as a toast.

## Starting configs

A config is started by its `command`, or by its id:

```
fd red keys                 the rest of the line goes to fd's object parameter
blr --image=$door           a named argument
find -i=$lastImage keys     a short one
```

Arguments are separated by spaces. Quote them with `"` or `'` to include spaces, a backslash escaping the next character inside quotes. Named arguments are `--name value`, `--name=value`, `-s value` or `-s=value`; a Bool one may leave out its value to mean true.

An argument like `$door` takes the value of a variable. Strings, numbers and bools are used as if typed, and an image serves as an image source always giving that image. To pass text starting with a dollar, write `\$`.

## Variables

```
door = $lastImage           an image, kept as a snapshot
object = red keys           a string, the rest of the line
count = 3                   a whole number (Int)
ratio = 0.5                 a number (Double)
loud = true                 a bool
code = '3'                  quoted text is always a string
```

Names consist of letters, digits and underscores, starting with a letter, and are case-sensitive. Names differing only in case can't be used together, since some storage doesn't tell them apart in file names.

Variables are global and persistent: they're kept in the VScan folder's `memory/` folder (see [files.md](files.md)) until removed. Temporary values can simply use short names that get reused.

The built-in variables can't be assigned:

| Variable | Value |
|---|---|
| `$defaultImageSource` | the current camera, the default of every config's image parameter |
| `$lastImage` | the image the foreground process took last, or the one shared into the app |

Image sources can't be stored in variables yet.

## Built-in commands

| Command | Does |
|---|---|
| `ps` | lists the running processes |
| `kill pid...` | stops processes |
| `killall` | stops all processes |
| `fg pid` | brings a background process to the foreground |
| `fg command...` | loads a config into the foreground |
| `bg` | moves the foreground process to the background |
| `reload` | brings in the changes of the linked folder |
| `vars` | lists the variables |
| `unset name...` | removes variables |

A line starting with a space is a message to the foreground process' conversation.
