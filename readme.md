# VScan

Perceiving space's geometry lets me *find* interesting things. Perceiving space's vibes lets me *discover* them.

Travelling the world as a blind individual, I get to see many interesting and beautiful places. However, while I can navigate around quite well using human echolocation and my long cane, I still felt something was missing compared to my sighted times. I can use echolocation to perceive the geometry of the environment, but not so much its vibes, which often lie in subtle details. A silent, empty square feels completely different to a square full of people, music and life. In a similar fashion, a square in the middle of the day is something else to a night square, lit by colorful neons, full of flowers, statues, ancient or modern buildings.

I can discover a monument using echolocation, but there's little I can do to tell what it represents, what color does it have, whether it's old, new, in what condition. I can walk on the street and notice a restaurant with a terrace, but I don't get to see its visuals, decoration or style on my radar. I can observe buildings on a square, but don't get to admire the magnificence of their architecture.

Indeed, visuals are not the only medium for vibes, I can still get a lot from sounds, smells, activity. But it's a significant-one and I was thinking, whether I could make use of the large language models to fill this sensory gap. Before, my travel routine was to search for cool places on Google, and later find them in the environment. But with a good enough visual perception layer, perhaps it may be possible to do it the other way around. To visit a place, and discover interesting things on the walk. And get an even deeper connection with the space.

Thus I designed VScan 0.2 to research this approach, as well as address some other camera-related needs I experience on my adventures.

## How does it work

The camera of your smartphone is your visual perception device. The core of VScan lies in configs. These are imaginary tools / purposes / cognitive tasks you may want to use your camera for. Each config consists of:

* The camera used - front / back, as well as camera parameters - resolution, flashlight etc.
* The prompts used for LLM processing. LLM is the bridge between pixel data and your interpretation of it, and in the user/system prompt, you can specify what are you interested in for the particular purpose, as well as the LLM model that should be used.

For the general purpose scene description, you may want to use the base config, with "What's in the image?" prompt using the back camera, which will give you a feeling of whatever area are you pointing to. For creating good looking selfies, you may want to use a specialized purpose, where you ask how good self-image have you captured, and can even ask for details such as the camera orientation, visibility of the capturing person, focus on the background, and the overall impression of the photography.

The interface of the app is designed for maximum efficiency. Right after the start, you're presented with the scanning tab, embracing a large easily-focusable Scan button, which will capture an image, process it using the active config, and read out the result. You can swipe right to display the list of your saved configurations and select the one you want to use, swipe left to see the parameters in detail or swipe up to display the conversation, were you can read/copy individual messages.

The scan tab also contains buttons for easy adjustment of the LLM prompts or asking questions. All of these are done by voice, since they're supposed to be used on the fly. You may, for example, want to focus scene descriptions on a particular building, so you dictate a new, more specific user prompt for the model and then take pictures of the building from various angles / places. When you're done, you can use the Reset button to return to the original config, all changes you make by these adjustments are temporary.

There is also a Save button, which will automatically generate a few word description of the last captured image and save it to your gallery together with its timestamp. Very useful for photo-documenting your travel, no more piles of photos with long, arbitrary numbers in their names and unknown content.

Sometimes, you may actively use one config, but want to hear the opinion of another without taking a new photo or changing your parameters. You can do this by opening the config list and long-pressing any configuration.

### An example usage routine

I love examples, they can often explain things much clearer than thousand definitions. This is one of the ways you can use VScan.

You're located on a historical square of a well-known city. You move around using echolocation, first near its edges, 5 - 10 meters from the walls. In your left hand, you're holding your smartphone with opened VScan, active Base config and the Scan button focused. As you're passing individual buildings, you take an image of every-one with your left thumb and can embrace all its visual details. You may even ask about the content of plaques. When you are done with the edges, you move across the open space and discover a sculpture in the middle of the square. First you ask details about it and explore it. Then you decide it's a good place to take selfie. First you circle around and discover what's visible from various sides, and select what buildings would you like to have in the background. When you find the ideal view, you walk to the opposite side of the sculpture, so it will be located behind your back together with your selected vieww. You switch the active config to Selfie, and try to capture the ideal travel photography, receiving feedback of your job. When you're satisfied, you can do one last check and consult the image with the Base model, to decide if there are no general error. If everything goes as you imagined, you press the Save button and store the trophy in your gallery.

## Warning

VScan is NOT supposed to be used for navigation purposes, or at least not for those where safety matters. Remember, the primary, geometric discovery of the space is the task of your physiological cognitive skills, like echolocation, haptic exploration, perceiving the space through sound etc. VScan is just a tool to provide another layer, another dimension to this perception, which should make it richer, more colorful and which could guide you to new, interesting places. However, actual moving around is still a geometric matter. LLMs are neither accurate nor reliable enough to help in this regard. Make sure to always use your senses first, and VScan to enrich them.

## Installation

I recommend installing the app through [Obtainium](https://github.com/ImranR98/Obtainium), installable through [F-Droid](https://f-droid.org/). With Obtainium, you can simply enter the GitHub URL of VScan as the source of the app, and have it installed directly from the GitHub releases of the project. You will receive notifications about any updates and have the option to conveniently install them immediately after they're made available.

Alternatively, you can manually download and install the apk file from the [Releases page](https://github.com/RastislavKish/VScan).

## Initial setup

VScan is a universal tool not bound to any specific LLM model or provider. The user can configure an arbitrary number of LLM servers, which can include closed proprietary services like OpenAI or Anthropic, routers such as OpenRouter or nano-gpt, and the user can even self-host their own server, the only requirement is OpenAI protocol compatibility. Subsequently, any model supported by the configured backends can be used in the app.

For non-technical users, it's recommended to simply use the well-known services like OpenAI or Anthropic with their flagship models, as they provide a reasonable balance between simplicity, quality and privacy.

There are 3 configuration steps needed to get things going.

### 1. Configure an APi provider

In the app, switch to the Options tab, click on Settings and navigate into API providers. Here you can define servers which will provide you the models you want to use in the app.

Tap on Add provider button and configure the details. If you just want to use one of the common providers such as OpenAI, Anthropic or OpenRouter, tap on Select a preset button and choose from the list of pre-configured choices. All you need to do with this approach is enter your API key, which you can obtain from the website of your chosen provider.

#### For advanced users

The purpose of Name, Base URL and API key fields may be obvious, the models section less so. VScan has an internal catalogue of common vision LLM models. It includes various SOTA model families such as GPT, Claude, Gemini, Gemma or Qwen. VScan uses its own internal textual identifiers for these models, starting with "vscan-" e.g. vscan-gpt-4o, vscan-claude-4-sonnet etc. The purpose of these identifiers is to allow VScan to easily request a particular model from any known or unknown LLM provider, as far as mapping between the VScan identifiers and the provider's model identifiers exists.

The models section in the provider configuration serves exactly this purpose. Here, the user can configure what models known by VScan does the provider offer, and how does it identify them. Provider presets include these mappings, so if the user goes with one of the supported choices, there should be no need to configure anything. But even if the provider is unknown to VScan, it can still be setup manually.

Note, while it's recommended to use the VScan identifiers for building cognitive tools, the app can still use arbitrary textual identifiers, so if you self-host a server with your own finetuned models or want to use models other than those known by VScan, you should be able to do so without any complications.

### 2. Configure the models you want to use

Before you can use an LLM model with VScan, you need to assign it a provider. In the app settings, Navigate to the Providers for models section. Think about the models you want to use with your cognitive tools, set them up here and assign them a provider or providers you have configured in the previous step. E.G. if you have chosen OpenAI as your provider and want to use GPT 4O (which is default for the base cognitive tool), click on Add, tap on Select model button and choose GPT 4O from the list, then tap on the Provider field and choose your configured OpenAI backend. If you want to use auto-describing of saved images, you may want to configure GPT 4O mini in the same way, as it is used by default by the description model.

### 3. Finetune your cognitive tools

If you've chosen OpenAI as your provider and configured GPT 4O and GPT 4O mini in the previous step, you can skip this part and enjoy taking photos. If you've chosen a different configuration, there is one more thing to do.

The default Base cognitive tool and File description cognitive tool are pre-configured to use GPT 4O and GPT 4O mini respectively, because they're the most popular option. These configs can't be directly edited, since they serve as a basis for deriving new configurations. You need to create your own derivations of these models, for instance, MyBase and MyFileDescription, with the same parameters but a model of your choice. You can do this easily on the Options tab by editing the respective config's Name, choosing the model and hitting Create. Next, you need to setup MyBase and MyFileDescription in the Settings to be used as the default models for the respective actions.

## Usage

VScan is a versatile app, which however is equipped with a highly-streamlined interface which should be easy, convenient and straight-forward to use in any situation.

After opening, you land directly on the scanning screen, where you can press the Scan button to take an image and apply the currently active cognitive tool (config in Vscan terminology). On the upper bar, you have a several quick action buttons, which let you ask a question, set up the system prompt, user prompt or save the last taken image to your gallery.

Setting the system or user prompt is a temporary action, these changes won't be saved to the currently active config and will be reset either upon changing configs, or upon hitting the Reset button on the upper bar, unless you explicitly decide to save them. This is useful either to throw in a random user prompt you may suddenly need to use without spending time on creating a new config, or simply for testing out new prompts.

Under the Scan button, there is a multipurpose edit field, which is used for various text input operations, like entering the system / user prompt, or sending a message to the conversation, this is the default action.

If you switch to the Options tab, you can see the entire configuration of the currently active cognitive tool, including its name, prompts, used camera, image resolution etc. Any changes made to these parameters are temporary, until you either hit the Update button, which saves these changes, or the create button, which saves a new cognitive tool with the configured parameters. Note. It is not possible to update the Base config and File description config, these are intended to be used for deriving custom configurations, and thus are immutable, you can either use them as they are, or derive your own variants and configure them to be used by default in the app settings.

The Config list tab includes a list of your cognitive tools, here you can either click on one to use it on the Scan tab, or long press one to apply it on the currently captured image (this is called consulting the config).

Eventually, the Conversation tab displays the currently active conversation, where you can access the past messages.

## Attributions

Asset | Author | Source
--- | --- | ---
Icon | <a href="https://www.flaticon.com/free-icons/qr-scan" title="qr scan icons">Qr scan icons created by Bharat Icons - Flaticon</a> | [Flaticon](https://www.flaticon.com/free-icon/scanning_7698853?term=scanner&page=1&position=3&origin=search&related_id=7698853)
Shutter sound | [InspectorJ](https://freesound.org/people/InspectorJ/) | [FreeSound](https://freesound.org/people/InspectorJ/sounds/360329/)

## Privacy policy

VScan on its own does not collect any personal information. If the user deliberately chooses to interact through the app with a third-party LLM provider, the user's photos, chats and other data shared for purposes of LLM processing are subject to the privacy policy of the respective external provider. VScan is not affiliated with any external services and it's solely the user's responsibility to review and agree with the conditions of any third-party LLM providers they choose to use.

## License

Copyright (C) 2024 Rastislav Kish

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.

