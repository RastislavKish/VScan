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
package com.rastislavkish.vscan.ui.itemeditoractivity

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

import com.rastislavkish.vscan.core.config.NumberValue
import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.config.PlayOperation
import com.rastislavkish.vscan.core.config.SayOperation
import com.rastislavkish.vscan.core.config.SetOperation
import com.rastislavkish.vscan.core.config.SoundInstance
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.config.VibrateOperation
import com.rastislavkish.vscan.core.imagesource.Crop
import com.rastislavkish.vscan.core.imagesource.Downscale
import com.rastislavkish.vscan.core.imagesource.LensOperation
import com.rastislavkish.vscan.core.imagesource.RelativeRect
import com.rastislavkish.vscan.core.imagesource.Zoom
import com.rastislavkish.vscan.core.process.GlobalMemory
import com.rastislavkish.vscan.core.speech.SpeechPriority
import com.rastislavkish.vscan.ui.form.Descriptions
import com.rastislavkish.vscan.ui.form.Form
import com.rastislavkish.vscan.ui.form.NumberValueKind
import com.rastislavkish.vscan.ui.form.Values

// A variable of the process memory, the config keeping them as a map
@Serializable
data class MemoryVariable(
    val name: String,
    val value: JsonPrimitive,
    )

// The form of an item kind, editing the item decoded from its JSON
abstract class ItemForm<T>(
    private val serializer: KSerializer<T>,
    json: String,
    ) {

    var item: T=Json.decodeFromString(serializer, json)

    val json: String
    get() = Json.encodeToString(serializer, item)

    abstract fun build(form: Form)

    // What keeps the item from being taken, e.g. a missing name, or null when nothing does
    open fun problem(): String? = null

    companion object {

        fun create(kind: ItemKind, json: String): ItemForm<*> {
            return when (kind) {
                ItemKind.PARAMETER -> ParameterForm(json)
                ItemKind.MEMORY_VARIABLE -> MemoryVariableForm(json)
                ItemKind.OPERATION -> OperationForm(json)
                ItemKind.LENS_OPERATION -> LensOperationForm(json)
                }
            }

        fun encode(parameter: Parameter): String = Json.encodeToString(parameter)
        fun encode(variable: MemoryVariable): String = Json.encodeToString(variable)
        fun encode(operation: Operation): String = Json.encodeToString(operation)
        fun encode(operation: LensOperation): String = Json.encodeToString(operation)

        fun decodeParameter(json: String): Parameter = Json.decodeFromString(json)
        fun decodeMemoryVariable(json: String): MemoryVariable = Json.decodeFromString(json)
        fun decodeOperation(json: String): Operation = Json.decodeFromString(json)
        fun decodeLensOperation(json: String): LensOperation = Json.decodeFromString(json)
        }
    }

class ParameterForm(json: String): ItemForm<Parameter>(Parameter.serializer(), json) {

    override fun build(form: Form) {
        form.text("Name", { item.name }, { item=item.copy(name=it) })
        form.choice("Type", ParameterType.values().map { Descriptions.typeName(it) to it }, { item.type }, { item=item.copy(type=it) })
        form.switch("Positional", { item.positional }, { item=item.copy(positional=it, rest=it && item.rest) })
        val rest=form.switch("Takes the rest of the line", { item.rest }, { item=item.copy(rest=it) })
        form.visibleWhen({ item.positional }, rest)
        form.optionalText("Short name", { item.short }, { item=item.copy(short=it) })
        form.optionalText("Default, empty if required", { item.default }, { item=item.copy(default=it) })
        form.text("Description", { item.description }, { item=item.copy(description=it) }, multiLine=true)
        }

    override fun problem(): String? = if (item.name.isBlank()) "The parameter needs a name" else null
    }

class MemoryVariableForm(json: String): ItemForm<MemoryVariable>(MemoryVariable.serializer(), json) {

    override fun build(form: Form) {
        form.text("Name", { item.name }, { item=item.copy(name=it) })
        form.field("Initial value", Values::formatMemoryValue, Values::parseMemoryValue, { item.value }, { item=item.copy(value=it) })
        }

    override fun problem(): String? {
        if (!GlobalMemory.isValidName(item.name))
        return "Use letters, digits and underscores for the name, starting with a letter"
        return null
        }
    }

class OperationForm(json: String): ItemForm<Operation>(Operation.serializer(), json) {

    override fun build(form: Form) {
        when (item) {
            is ToastOperation -> {
                form.text("Text", { toast.text }, { item=toast.copy(text=it) }, multiLine=true)
                }
            is SayOperation -> {
                form.text("Text", { say.text }, { item=say.copy(text=it) }, multiLine=true)
                form.choice("Priority", SpeechPriority.values().map { Descriptions.priorityName(it) to it }, { say.priority }, { item=say.copy(priority=it) })
                }
            is PlayOperation -> {
                form.optionalText("Sound", { play.sound }, { item=play.copy(sound=it) })
                form.int("Repeat, 0 loops", { play.repeat }, { item=play.copy(repeat=it) })
                numberValue(form, "Volume", 1.0, { play.volume }, { item=play.copy(volume=it) })
                numberValue(form, "Pan", 0.0, { play.pan }, { item=play.copy(pan=it) })
                form.choice("Instance", listOf("Per process" to SoundInstance.PROCESS, "New" to SoundInstance.NEW), { play.instance }, { item=play.copy(instance=it) })
                form.switch("Stop the other sounds of the process", { play.stopOthers }, { item=play.copy(stopOthers=it) })
                }
            is VibrateOperation -> {
                form.field("Pattern, off and on milliseconds", Values::formatPattern, Values::parsePattern, { vibrate.pattern }, { item=vibrate.copy(pattern=it) })
                form.int("Repeat, 0 loops", { vibrate.repeat }, { item=vibrate.copy(repeat=it) })
                }
            is SetOperation -> {
                form.text("Variable", { set.name }, { item=set.copy(name=it) })
                form.text("Value expression", { set.value }, { item=set.copy(value=it) })
                }
            }

        form.optionalText("Condition", { item.condition }, { item=withCondition(item, it) })
        }

    override fun problem(): String? {
        val operation=item
        if (operation is SetOperation && operation.name.isBlank())
        return "Name the memory variable to set"
        if (operation is SetOperation && operation.value.isBlank())
        return "Give the value expression"
        return null
        }

    private val toast: ToastOperation get() = item as ToastOperation
    private val say: SayOperation get() = item as SayOperation
    private val play: PlayOperation get() = item as PlayOperation
    private val vibrate: VibrateOperation get() = item as VibrateOperation
    private val set: SetOperation get() = item as SetOperation

    private fun withCondition(operation: Operation, condition: String?): Operation {
        return when (operation) {
            is ToastOperation -> operation.copy(condition=condition)
            is SayOperation -> operation.copy(condition=condition)
            is PlayOperation -> operation.copy(condition=condition)
            is VibrateOperation -> operation.copy(condition=condition)
            is SetOperation -> operation.copy(condition=condition)
            }
        }

    // A kind choice with the fields of the chosen kind
    private fun numberValue(form: Form, label: String, constant: Double, get: () -> NumberValue?, set: (NumberValue?) -> Unit) {
        form.choice(label, NumberValueKind.values().map { it.label to it }, { NumberValueKind.of(get()) }, { set(Values.withKind(get(), it, constant)) })

        val value=form.double("$label value", { get()?.value ?: constant }, { set(get()?.copy(value=it)) })
        form.visibleWhen({ NumberValueKind.of(get())==NumberValueKind.CONSTANT }, value)
        val path=form.text("$label JSON path", { get()?.path ?: "" }, { set(get()?.copy(path=it)) })
        form.visibleWhen({ NumberValueKind.of(get())==NumberValueKind.JSON }, path)
        val pattern=form.text("$label pattern", { get()?.pattern ?: "" }, { set(get()?.copy(pattern=it)) })
        form.visibleWhen({ NumberValueKind.of(get())==NumberValueKind.REGEX }, pattern)
        val expression=form.text("$label expression", { get()?.expression ?: "" }, { set(get()?.copy(expression=it)) })
        form.visibleWhen({ NumberValueKind.of(get())==NumberValueKind.EXPRESSION }, expression)
        }
    }

// Coordinates are relative to the image, 0 being the left / top edge and 1 the right / bottom one
// The constructors check the values, a field taking only a value giving a valid operation
class LensOperationForm(json: String): ItemForm<LensOperation>(LensOperation.serializer(), json) {

    override fun build(form: Form) {
        when (item) {
            is Crop -> {
                form.double("Left", { double(crop.region.left) }, { region { r -> RelativeRect(it.toFloat(), r.top, r.right, r.bottom) } })
                form.double("Top", { double(crop.region.top) }, { region { r -> RelativeRect(r.left, it.toFloat(), r.right, r.bottom) } })
                form.double("Right", { double(crop.region.right) }, { region { r -> RelativeRect(r.left, r.top, it.toFloat(), r.bottom) } })
                form.double("Bottom", { double(crop.region.bottom) }, { region { r -> RelativeRect(r.left, r.top, r.right, it.toFloat()) } })
                }
            is Zoom -> {
                form.double("Zoom", { double(zoom.zoom) }, { item=Zoom(it.toFloat(), zoom.centerX, zoom.centerY) })
                form.double("Center x", { double(zoom.centerX) }, { item=Zoom(zoom.zoom, it.toFloat(), zoom.centerY) })
                form.double("Center y", { double(zoom.centerY) }, { item=Zoom(zoom.zoom, zoom.centerX, it.toFloat()) })
                }
            is Downscale -> {
                form.int("Longer side in pixels", { downscale.maxDimension }, { item=Downscale(it) })
                }
            }
        }

    private val crop: Crop get() = item as Crop
    private val zoom: Zoom get() = item as Zoom
    private val downscale: Downscale get() = item as Downscale

    // The float's shortest form, so 0.4f shows as 0.4
    private fun double(value: Float): Double = value.toString().toDouble()

    private fun region(transform: (RelativeRect) -> RelativeRect) {
        item=Crop(transform(crop.region))
        }
    }
