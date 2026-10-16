package ru.terabitvpn.desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/** Windows 11: dark title bar + Mica-ish backdrop colour matching the app. */
object WindowsChrome {

    private interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attr: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: Dwm? by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    fun darkTitleBar(window: Window) {
        val lib = dwm ?: return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            lib.DwmSetWindowAttribute(hwnd, 20, IntByReference(1), 4) // DWMWA_USE_IMMERSIVE_DARK_MODE
            // DWMWA_CAPTION_COLOR = 35, COLORREF 0x00BBGGRR → #030D08
            lib.DwmSetWindowAttribute(hwnd, 35, IntByReference(0x00080D03), 4)
        }
    }
}
