# VScan

GPT 4 Vision on Android, optimized for recognition as well as scanning use-cases

## Usage

VScan is optimized for easy customization. In other words, you can use it for simple scene recognition, which is the default configuration, however, you can as easily turn it into an efficient OCR, color detector or anything else you may wish to use GPT 4v for.

When you open the app, you're presented with a system prompt and user prompt text entries. System prompt is used to setup a role for the GPT and is optional. The user prompt represents a text that will be later on used whenever a picture is taken, "What's in the image?" is used by default if nothing is entered.

When you click start, a screen with a big shooter button appears. Pressing this button will start a new conversation with GPT 4V, using the prompts you configured before.

If You didn't use any prompt, you get a description of the scene you have captured. You could say, be triggering this repeatedly pointing your smartphone to various directions, getting to know your surrounding. However, you could have used a user prompt saying: "Tell me the color of the object my index finger is touching." Now you have turned GPT into a color detector that reports you the color of anything you touch. Or, you could setup a prompt saying "Tell me if you can see an electric outlet in the image." Now you can just walk around a hotel room and continuously take pictures of the walls, your "electric outlet detector" should save you the need to touch all the walls from bottom to top.

### Setting up OpenAI API key

Before starting a GPT session in the app, you need to setup the OpenAI API key to be used. See the API key section of the [Vision project](https://github.com/RastislavKish/vision) for detailed instructions on optaining the key and an approximate information on pricing. When you do have the key, all you need to do is to insert it into the respective text entry in the app and press the Apply button, your key will be saved and erased from the field for security reasons.

## Attributions

Asset | Author | Source
--- | --- | ---
Icon | <a href="https://www.flaticon.com/free-icons/qr-scan" title="qr scan icons">Qr scan icons created by Bharat Icons - Flaticon</a> | [Flaticon](https://www.flaticon.com/free-icon/scanning_7698853?term=scanner&page=1&position=3&origin=search&related_id=7698853)
Shutter sound | [InspectorJ](https://freesound.org/people/InspectorJ/) | [FreeSound](https://freesound.org/people/InspectorJ/sounds/360329/)

## License

Copyright (C) 2023 Rastislav Kish

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.

