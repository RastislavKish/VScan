/*
* Copyright (C) 2026 Rastislav Kish
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, version 3.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/
package com.rastislavkish.vscan.ui.form

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.config.PlayOperation
import com.rastislavkish.vscan.core.config.SayOperation
import com.rastislavkish.vscan.core.config.SetOperation
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.config.VibrateOperation
import com.rastislavkish.vscan.core.imagesource.Crop
import com.rastislavkish.vscan.core.imagesource.Downscale
import com.rastislavkish.vscan.core.imagesource.RelativeRect
import com.rastislavkish.vscan.core.imagesource.Zoom
import com.rastislavkish.vscan.core.speech.SpeechPriority

class DescriptionsTest {

    @Test
    fun describesParameters() {
        assertEquals("image (-i), ImageSource, default \$defaultImageSource", Descriptions.parameter(Parameter.defaultImageParameter))
        assertEquals("object, String, positional, the rest of the line, required", Descriptions.parameter(Parameter("object", ParameterType.STRING, positional=true, rest=true)))
        }

    @Test
    fun describesOperations() {
        assertEquals("Toast {{ lastOutput }}", Descriptions.operation(ToastOperation()))
        assertEquals("Say Bus {{ lastOutput }}, High priority, if changed", Descriptions.operation(SayOperation(condition="changed", text="Bus\n{{ lastOutput }}", priority=SpeechPriority.HIGH)))
        assertEquals("Play beep, looping, stopping the other sounds", Descriptions.operation(PlayOperation(sound="beep", repeat=0, stopOthers=true)))
        assertEquals("Vibrate 0, 100, 3 times", Descriptions.operation(VibrateOperation(repeat=3)))
        assertEquals("Set announced to true", Descriptions.operation(SetOperation(name="announced", value="true")))
        assertEquals("Toast ${"a".repeat(100)}...", Descriptions.operation(ToastOperation(text="a".repeat(150))))
        }

    @Test
    fun describesLensOperations() {
        assertEquals("Crop from 0.1, 0 to 0.9, 1", Descriptions.lensOperation(Crop(RelativeRect(0.1f, 0f, 0.9f, 1f))))
        assertEquals("Zoom 2 times around 0.5, 0.4", Descriptions.lensOperation(Zoom(2f, centerY=0.4f)))
        assertEquals("Downscale to 1024 pixels", Descriptions.lensOperation(Downscale(1024)))
        }
    }
