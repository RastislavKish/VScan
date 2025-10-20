# VScan

<a href="https://f-droid.org/packages/com.rastislavkish.vscan">
    <img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">
</a>

Perceiving space's geometry lets me *find* interesting things. Perceiving space's vibes lets me *discover* them.

Travelling the world as a blind individual, I get to see many interesting and beautiful places. However, while I can navigate around quite well using human echolocation and my long cane, I still felt something was missing compared to my sighted times. I can use echolocation to perceive the geometry of the environment, but not so much its vibes, which often lie in subtle details. A silent, empty square feels completely different to a square full of people, music and life. In a similar fashion, a square in the middle of the day is something else to a night square, lit by colorful neons, full of flowers, statues, ancient or modern buildings.

I can discover a monument using echolocation, but there's little I can do to tell what it represents, what color does it have, whether it's old, new, in what condition. I can walk on the street and notice a restaurant with a terrace, but I don't get to see its visuals, decoration or style on my radar. I can observe buildings on a square, but don't get to admire the magnificence of their architecture.

Indeed, visuals are not the only medium for vibes, I can still get a lot from sounds, smells, activity. But it's a significant-one and I was thinking, whether I could make use of the large language models to fill this sensory gap. Before, my travel routine was to search for cool places on Google, and later find them in the environment. But with a good enough visual perception layer, perhaps it may be possible to do it the other way around. To visit a place, and discover interesting things on the walk. And get an even deeper connection with the space.

Thus I designed VScan 0.2 to research this approach, as well as address some other camera-related needs I experience on my adventures.

## How does it work

The camera of your smartphone is your visual perception device. The core of VScan lies in configs. These are imaginary tools / purposes / cognitive tasks you may want to use your camera for. Each config consists of:

* The camera used - front / back, as well as camera parameters - resolution, flashlight etc.
* The prompts used for LLM processing. LLM is the bridge between pixel data and your interpretation of it, and in the user/system prompt, you can specify what are you interested in for the particular purpose, as well as the LLM model that should be used.

For the general purpose scene description, you may want to use the base config, with "What's in the image?" prompt using the back camera, which will give you a feeling of whatever area are you pointing to. For creating good looking selfies, you may want to use a specialized purpose, where you ask how good self-image have you captured, and can even ask for details such as the camera orientation, visibility of the capturing person, focus on the background, and the overall impression of the photography. Using the configs, you can describe various visual cognitive functions, for instance, looking for an object such as an electric outlet, a specific building or state of an object.

### Example usage scenarios

I love examples, they can often explain things much clearer than thousand definitions. These are some of the ways you can use VScan.

#### Travel and feeling the surrounding

You're located on a historical square of a well-known city. You move around using echolocation, first near its edges, 5 - 10 meters from the walls. In your left hand, you're holding your smartphone with opened VScan, active Base config and the Scan button focused.

As you're passing individual buildings, you take an image of every-one with your left thumb and can embrace all its visual details. You may even ask about specific things if you find something of interest, for instance the text of a plaque on a house, its architecture, state etc.

When you are done with the edges, you move across the open space and discover a sculpture in the middle of the square. First you ask details about it and explore it. Then you decide it's a good place to take selfie. First you circle around and discover what's visible from various sides, and you choose what buildings would you like to have in the background.

When you find the ideal view, you walk to the opposite side of the sculpture, so it will be located behind your back together with your selected vieww. You switch the active config to Selfie, and try to capture the ideal travel photography, receiving feedback of your job. When you're satisfied, you can do one last check and consult the image with the Base model, to decide if there are no general errors. If everything goes as you imagined, you press the Save button and store the trophy in your gallery.

#### Looking for electric outlets

Whenever you visit a hotel, searching for an outlet to charge your equipment can get... Time consuming. Walls are large, outlets are tiny, and they can be located almost anywhere. Without sight, there is often no other way to find them than manually search every squared centimeter of your room.

With VScan, you can create a config that specifically looks for electric outlets. Then you simply point at large wall segments and press the Scan button to see if they contain what you're looking for, instead of manually gliding them with your hands from top to bottom.

Note: Sometimes the outlets are deliberately located on covered locations, such as behind nightstands, between the bed and the wall, etc. In this case, they're often visible just partially, or can only be spotted from a specific angle. Nevertheless, even in such situation, VScan can help you elliminate large surfaces you'd otherwise need to check manually, just like a sighted person can right away tell the outlets are not present on the open areas and can immediately focus on the covered-ones.

#### Verifying state of objects

Have you ever needed to live-capture your ID for an online identity verification? Yes, I'm looking at you, Ryanair. Then you may know it requires some conditions to be met in order to capture a usable photo. You need good lighting in the room, no shadows, the ID must be in correct orientation, correct distance, facing you with the correct side and the image you capture must be readable.

A sighted person can evaluate all of these things at a single glance. And you may be able to as well. In VScan, you can define a config checking specifically for the conditions you're interested in. For instance: "Is the text on the card fully visible, upright and clearly readable?"

And you can afterwards just keep pressing the Scan button, as you try various positions for capturing the image of your ID and get immediate feedback whether it's usable or not.

Note: For capturing sensitive documents like the ID cards, passports or health insurance cards, make sure to verify the privacy policy of your chosen provider and the conditions under which your data are manipulated. You most likely DO NOT want an image of your ID card ending up in the training data of a public large language model.

## Warning

VScan is NOT supposed to be used for navigation purposes, or at least not for those where safety matters. Remember, the primary, geometric discovery of the space is the task of your physiological cognitive skills, like echolocation, haptic exploration, perceiving the space through sound etc. VScan is just a tool to provide another layer, another dimension to this perception, which should make it richer, more colorful and which could guide you to new, interesting places. However, actual moving around is still a geometric matter. LLMs are neither accurate nor reliable enough to help in this regard. Make sure to always use your senses first, and VScan to enrich them.

Also note the LLMs may be prone to general hallucinations and inaccuracy. This is something VScan fully counts with. If we can get a cognitive function to work in 7 out of 10 times, that's a considerable improvement over 0 out of 10 times. However, there are important situations, when you may need 10 out of 10 accuracy. In such scenarios, VScan is NOT the tool to use.

## Installation

VScan is available on [F-Droid](https://f-droid.org/packages/com.rastislavkish.vscan), through [Obtainium](https://github.com/ImranR98/Obtainium) and also on [Google Play](https://play.google.com/store/apps/details?id=com.rastislavkish.vscan).

I recommend using F-Droid for standard installation and Obtainium for installing directly from the repository. You can use the Google Play version as well, but this version is signed by a Play Store generated key, and as such I can't guarantee the package authenticity. Also note there are several other apps on the Play store with the same or a similar name, make sure to check the source before installing.

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

After opening, you land directly on the scanning screen, where you can press the Scan button to take an image and apply the currently active cognitive tool (config in Vscan terminology). On the upper bar, you have a several quick action buttons, which let you ask a question, set up the system prompt, user prompt or save the last taken image to your gallery. The buttons triggering user input work on a textual basis, long pressing will use voice input (although this is not fully tuned to my liking yet).

Setting the system or user prompt is a temporary action, these changes won't be saved to the currently active config and will be reset either upon changing configs, or upon hitting the Reset button on the upper bar, unless you explicitly decide to save them. This is useful either to throw in a random user prompt you may suddenly need to use without spending time on creating a new config, or simply for testing out new prompts.

Under the Scan button, there is a multipurpose edit field, which is used for various text input operations, like entering the system / user prompt, or sending a message to the conversation, this is the default action.

If you switch to the Options tab or swipe left, you can see the entire configuration of the currently active cognitive tool, including its name, prompts, used camera, image resolution etc. Any changes made to these parameters are temporary, until you either hit the Update button, which saves these changes, or the create button, which saves a new cognitive tool with the configured parameters. Note. It is not possible to update the Base config and File description config, these are intended to be used for deriving custom configurations, and thus are immutable, you can either use them as they are, or derive your own variants and configure them to be used by default in the app settings.

The Config list tab, accessible by a swipe to the right, includes a list of your cognitive tools, here you can either click on one to use it on the Scan tab, or long press one to apply it on the currently captured image (this is called consulting the config).

Eventually, the Conversation tab, which you can access by swiping up, displays the currently active conversation, where you can access the past messages.

## Attributions

Asset | Author | Source
--- | --- | ---
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
