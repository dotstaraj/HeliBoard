// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.edit
import androidx.core.view.forEach
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.EnumMap
import java.util.Locale

fun createToolbarKey(context: Context, keyName: String): ImageButton {
    val key = try { ToolbarKey.valueOf(keyName) } catch (_: Exception) { null }
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER
    button.tag = keyName
    if (key != null) {
        button.contentDescription = key.name.lowercase().getStringResourceOrName("", context)
        button.setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(key.name, context))
    } else if (keyName.startsWith("__str_")) {
        val text = keyName.substringAfter("__str_").decodeFromHex()
        button.contentDescription = text
        button.setImageDrawable(createToolbarStringDrawable(context, text))
    }
    setToolbarButtonActivatedState(button)
    return button
}

private fun createToolbarStringDrawable(context: Context, text: String): Drawable {
    val size = context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) * 0.6f
    val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Settings.getValues().mColors.get(ColorType.KEY_TEXT)
        textAlign = Paint.Align.CENTER
        textSize = size * 0.7f
        typeface = Typeface.DEFAULT_BOLD
    }
    val textWidth = paint.measureText(text)
    if (textWidth > size * 0.9f) {
        paint.textSize *= (size * 0.9f) / textWidth
    }
    val x = canvas.width / 2f
    val y = canvas.height / 2f - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(text, x, y, paint)
    return BitmapDrawable(context.resources, bitmap)
}

fun setToolbarButtonsActivatedStateOnPrefChange(buttonsGroup: ViewGroup, key: String?) {
    // settings need to be updated when buttons change
    if (key != Settings.PREF_AUTO_CORRECTION
        && key != Settings.PREF_ALWAYS_INCOGNITO_MODE
        && key != GestureDataGatheringSettings.PREF_BACKGROUND_GATHERING_ENABLED
        && key != GestureDataGatheringSettings.PREF_BACKGROUND_DISABLED_BEFORE_TIME_MILLIS
        && key?.startsWith(Settings.PREF_ONE_HANDED_MODE_PREFIX) == false)
        return

    GlobalScope.launch {
        delay(10) // need to wait until SettingsValues are reloaded
        buttonsGroup.forEach { if (it is ImageButton) setToolbarButtonActivatedState(it) }
    }
}

private fun setToolbarButtonActivatedState(button: ImageButton) {
    val tag = button.tag as? String ?: return
    val key = try { ToolbarKey.valueOf(tag) } catch (_: Exception) { null }
    button.isActivated = when (key) {
        INCOGNITO -> button.context.prefs().getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)
        ONE_HANDED -> Settings.getValues().mOneHandedModeEnabled
        SPLIT -> Settings.getValues().mIsSplitKeyboardEnabled
        AUTOCORRECT -> Settings.getValues().mAutoCorrectionEnabledPerUserSettings
        BACKGROUND_GATHERING -> useBackgroundGathering
        else -> true
    }
}

fun getCodeForToolbarKey(keyName: String): Int {
    val key = try { ToolbarKey.valueOf(keyName) } catch (_: Exception) { null }
    if (key == null)
        return if (keyName.startsWith("__str_")) {
            val text = keyName.substringAfter("__str_").decodeFromHex()
            if (text.codePointCount(0, text.length) == 1) text.codePointAt(0)
            else KeyCode.MULTIPLE_CODE_POINTS
        } else KeyCode.UNSPECIFIED
    return Settings.getInstance().getCustomToolbarKeyCode(key) ?: when (key) {
        VOICE -> KeyCode.VOICE_INPUT
        CLIPBOARD -> KeyCode.CLIPBOARD
        NUMPAD -> KeyCode.NUMPAD
        DPAD -> KeyCode.DPAD
        UNDO -> KeyCode.UNDO
        REDO -> KeyCode.REDO
        SETTINGS -> KeyCode.SETTINGS
        SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_ALL
        SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_WORD
        COPY -> KeyCode.CLIPBOARD_COPY
        CUT -> KeyCode.CLIPBOARD_CUT
        PASTE -> KeyCode.CLIPBOARD_PASTE
        ONE_HANDED -> KeyCode.TOGGLE_ONE_HANDED_MODE
        INCOGNITO -> KeyCode.TOGGLE_INCOGNITO_MODE
        AUTOCORRECT -> KeyCode.TOGGLE_AUTOCORRECT
        CLEAR_CLIPBOARD -> KeyCode.CLIPBOARD_CLEAR_HISTORY
        CLOSE_HISTORY -> KeyCode.CLIPBOARD
        EMOJI -> KeyCode.EMOJI
        LEFT -> KeyCode.ARROW_LEFT
        RIGHT -> KeyCode.ARROW_RIGHT
        UP -> KeyCode.ARROW_UP
        DOWN -> KeyCode.ARROW_DOWN
        WORD_LEFT -> KeyCode.WORD_LEFT
        WORD_RIGHT -> KeyCode.WORD_RIGHT
        PAGE_UP -> KeyCode.PAGE_UP
        PAGE_DOWN -> KeyCode.PAGE_DOWN
        FULL_LEFT -> KeyCode.MOVE_START_OF_LINE
        FULL_RIGHT -> KeyCode.MOVE_END_OF_LINE
        PAGE_START -> KeyCode.MOVE_START_OF_PAGE
        PAGE_END -> KeyCode.MOVE_END_OF_PAGE
        SPLIT -> KeyCode.SPLIT_LAYOUT
        FLOATING -> KeyCode.TOGGLE_FLOATING_WINDOW
        BACKGROUND_GATHERING -> KeyCode.BACKGROUND_GATHERING
    }
}

fun getCodeForToolbarKeyLongClick(keyName: String): Int {
    val key = try { ToolbarKey.valueOf(keyName) } catch (_: Exception) { null }
    if (key == null) return KeyCode.UNSPECIFIED
    return Settings.getInstance().getCustomToolbarLongpressCode(key) ?: when (key) {
        CLIPBOARD -> KeyCode.CLIPBOARD_PASTE
        UNDO -> KeyCode.REDO
        REDO -> KeyCode.UNDO
        SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_WORD
        SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_ALL
        COPY -> KeyCode.CLIPBOARD_CUT
        PASTE -> KeyCode.CLIPBOARD
        LEFT -> KeyCode.KEY_REPEAT
        RIGHT -> KeyCode.KEY_REPEAT
        UP -> KeyCode.KEY_REPEAT
        DOWN -> KeyCode.KEY_REPEAT
        WORD_LEFT -> KeyCode.KEY_REPEAT
        WORD_RIGHT -> KeyCode.KEY_REPEAT
        PAGE_UP -> KeyCode.MOVE_START_OF_PAGE
        PAGE_DOWN -> KeyCode.MOVE_END_OF_PAGE
        BACKGROUND_GATHERING -> KeyCode.BACKGROUND_GATHERING_TEMP_OFF
        else -> KeyCode.UNSPECIFIED
    }
}

// names need to be aligned with resources strings (using lowercase of key.name)
enum class ToolbarKey {
    VOICE, CLIPBOARD, NUMPAD, DPAD, UNDO, REDO, SETTINGS, SELECT_ALL, SELECT_WORD, COPY, CUT, PASTE, ONE_HANDED, FLOATING, SPLIT,
    INCOGNITO, AUTOCORRECT, CLEAR_CLIPBOARD, CLOSE_HISTORY, EMOJI, LEFT, RIGHT, UP, DOWN, WORD_LEFT, WORD_RIGHT,
    PAGE_UP, PAGE_DOWN, FULL_LEFT, FULL_RIGHT, PAGE_START, PAGE_END, BACKGROUND_GATHERING
}

enum class ToolbarMode {
    EXPANDABLE, TOOLBAR_KEYS, SUGGESTION_STRIP, HIDDEN,
}

val toolbarKeyStrings = entries.associateWithTo(EnumMap(ToolbarKey::class.java)) { it.toString().lowercase(Locale.US) }

val defaultToolbarPref by lazy {
    val default = listOf(SETTINGS, VOICE, CLIPBOARD, UNDO, REDO, SELECT_WORD, COPY, PASTE, LEFT, RIGHT)
    val others = entries.filterNot { it in default || it == CLOSE_HISTORY }
    default.joinToString(Separators.ENTRY) { it.name + Separators.KV + true } + Separators.ENTRY +
            others.joinToString(Separators.ENTRY) { it.name + Separators.KV + false }
}

val defaultPinnedToolbarPref = entries.filterNot { it == CLOSE_HISTORY }.joinToString(Separators.ENTRY) {
    it.name + Separators.KV + false
}

val defaultClipboardToolbarPref by lazy {
    val default = listOf(CLEAR_CLIPBOARD, UP, DOWN, LEFT, RIGHT, UNDO, CUT, COPY, PASTE, SELECT_WORD, CLOSE_HISTORY)
    val others = entries.filterNot { it in default }
    default.joinToString(Separators.ENTRY) { it.name + Separators.KV + true } + Separators.ENTRY +
            others.joinToString(Separators.ENTRY) { it.name + Separators.KV + false }
}

/** add missing keys, typically because a new key has been added */
fun upgradeToolbarPrefs(prefs: SharedPreferences) {
    upgradeToolbarPref(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref)
}

private fun upgradeToolbarPref(prefs: SharedPreferences, pref: String, default: String) {
    if (!prefs.contains(pref)) return
    val list = prefs.getString(pref, default)!!.split(Separators.ENTRY).toMutableList()
    val splitDefault = defaultToolbarPref.split(Separators.ENTRY)
    splitDefault.forEach { entry ->
        val keyWithSeparator = entry.substringBefore(Separators.KV) + Separators.KV
        if (list.none { it.startsWith(keyWithSeparator) })
            list.add("${keyWithSeparator}false")
    }
    // likely not needed, but better prepare for possibility of key removal
    list.removeAll {
        val keyName = it.substringBefore(Separators.KV)
        if (keyName.startsWith("__str_")) return@removeAll false
        try {
            ToolbarKey.valueOf(keyName)
            false
        } catch (_: IllegalArgumentException) {
            true
        }
    }
    prefs.edit { putString(pref, list.joinToString(Separators.ENTRY)) }
}

fun getEnabledToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)

fun getPinnedToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)

fun getEnabledClipboardToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref)

fun addPinnedKey(prefs: SharedPreferences, keyName: String) {
    // remove the existing version of this key and add the enabled one after the last currently enabled key
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)!!
    val keys = string.split(Separators.ENTRY).toMutableList()
    keys.removeAll { it.startsWith(keyName + Separators.KV) }
    val lastEnabledIndex = keys.indexOfLast { it.endsWith("true") }
    keys.add(lastEnabledIndex + 1, keyName + Separators.KV + "true")
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, keys.joinToString(Separators.ENTRY)) }
}

fun removePinnedKey(prefs: SharedPreferences, keyName: String) {
    // just set it to disabled
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)!!
    val result = string.split(Separators.ENTRY).joinToString(Separators.ENTRY) {
        if (it.startsWith(keyName + Separators.KV))
            keyName + Separators.KV + "false"
        else it
    }
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, result) }
}

private fun getEnabledToolbarKeys(prefs: SharedPreferences, pref: String, default: String): List<String> {
    val string = prefs.getString(pref, default)!!
    return string.split(Separators.ENTRY).mapNotNull {
        val split = it.split(Separators.KV)
        if (split.last() == "true") split.first() else null
    }
}

fun writeCustomKeyCodes(prefs: SharedPreferences, codes: EnumMap<ToolbarKey, ToolbarKeyCustomCodes>) {
    val string = codes.mapNotNull { entry -> entry.value?.let { "${entry.key.name},${it.click},${it.longClick},${it.swipeDown}" } }.joinToString(";")
    prefs.edit { putString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, string) }
}

fun readCustomKeyCodes(prefs: SharedPreferences): EnumMap<ToolbarKey, ToolbarKeyCustomCodes> {
    val map = EnumMap<ToolbarKey, ToolbarKeyCustomCodes>(ToolbarKey::class.java)
    prefs.getString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, Defaults.PREF_TOOLBAR_CUSTOM_KEY_CODES)!!
        .split(";").forEach {
            runCatching {
                val s = it.split(",")
                val click = s[1].toIntOrNull()
                val longClick = s[2].toIntOrNull()
                val swipeDown = if (s.size > 3) s[3].toIntOrNull() else null
                map[ToolbarKey.valueOf(s[0])] = ToolbarKeyCustomCodes(click, longClick, swipeDown)
            }
        }
    return map
}

fun getCustomKeyCode(key: ToolbarKey, prefs: SharedPreferences): Int? {
    if (customToolbarKeyCodes == null)
        customToolbarKeyCodes = readCustomKeyCodes(prefs)
    return customToolbarKeyCodes!![key]?.click
}

fun getCustomLongpressKeyCode(key: ToolbarKey, prefs: SharedPreferences): Int? {
    if (customToolbarKeyCodes == null)
        customToolbarKeyCodes = readCustomKeyCodes(prefs)
    return customToolbarKeyCodes!![key]?.longClick
}

fun getCustomSwipeDownKeyCode(key: ToolbarKey, prefs: SharedPreferences): Int? {
    if (customToolbarKeyCodes == null)
        customToolbarKeyCodes = readCustomKeyCodes(prefs)
    return customToolbarKeyCodes!![key]?.swipeDown
}

fun clearCustomToolbarKeyCodes() {
    customToolbarKeyCodes = null
}

fun getCodeForToolbarKeySwipeDown(keyName: String): Int {
    val key = try { ToolbarKey.valueOf(keyName) } catch (_: Exception) { null }
    return if (key == null) KeyCode.UNSPECIFIED else (Settings.getInstance().getCustomToolbarSwipeDownCode(key) ?: KeyCode.UNSPECIFIED)
}

fun getTextForToolbarKey(keyName: String): String? {
    return if (keyName.startsWith("__str_")) keyName.substringAfter("__str_").decodeFromHex()
    else null
}

fun onClickToolbarKey(view: View, onCodeInput: (Int) -> Unit, onTextInput: (String) -> Unit = {}) {
    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, view, HapticEvent.KEY_PRESS)
    val tag = view.tag as String
    val code = getCodeForToolbarKey(tag)
    if (code == KeyCode.MULTIPLE_CODE_POINTS) {
        getTextForToolbarKey(tag)?.let { text ->
            val textToSend = if (text.length > 1 && text.count { it == '|' } == 1) "\u001D$text" else text
            onTextInput(textToSend)
        }
    } else if (code != KeyCode.UNSPECIFIED) {
        onCodeInput(code)
    }
}

fun onLongClickToolbarKey(view: View, onCodeInput: (Int, Boolean) -> Unit, onTextInput: (String) -> Unit = {}) {
    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, view, HapticEvent.KEY_LONG_PRESS)
    val code = getCodeForToolbarKeyLongClick(view.tag as String)
    if (code == KeyCode.KEY_REPEAT) {
        onClickToolbarKey(view, { onCodeInput(it, false) }, onTextInput)
        repeatToolbarKey(view) { onClickToolbarKey(view, { onCodeInput(it, true) }, onTextInput) }
    } else if (code != KeyCode.UNSPECIFIED) {
        onCodeInput(code, false)
    }
}

fun onSwipeDownToolbarKey(view: View, onCodeInput: (Int) -> Unit) {
    val code = getCodeForToolbarKeySwipeDown(view.tag as String)
    if (code != KeyCode.UNSPECIFIED) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, view, HapticEvent.KEY_PRESS)
        onCodeInput(code)
    }
}

private fun repeatToolbarKey(view: View, onClick: (view: View) -> Unit) {
    view.handler.postDelayed({
        if (view.isPressed) {
            onClick(view)
            repeatToolbarKey(view, onClick)
        }
    }, view.resources.getInteger(R.integer.config_key_repeat_interval).toLong())
}

private var customToolbarKeyCodes: EnumMap<ToolbarKey, ToolbarKeyCustomCodes>? = null

data class ToolbarKeyCustomCodes(
    val click: Int?,
    val longClick: Int?,
    val swipeDown: Int?
)

private fun String.decodeFromHex(): String {
    val sb = StringBuilder()
    var i = 0
    while (i < length) {
        val code = substring(i, i + 4).toInt(16)
        sb.append(code.toChar())
        i += 4
    }
    return sb.toString()
}

fun String.encodeToHex(): String {
    return this.map { it.code.toString(16).padStart(4, '0') }.joinToString("")
}
