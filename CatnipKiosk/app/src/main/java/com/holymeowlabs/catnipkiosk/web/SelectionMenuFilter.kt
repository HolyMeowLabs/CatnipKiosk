package com.holymeowlabs.catnipkiosk.web

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View

/** Text-selection menu allow-list: editing actions only, never anything that opens another app. */
object SelectionMenuFilter {
    val allowedIds: Set<Int> = setOf(
        android.R.id.copy,
        android.R.id.cut,
        android.R.id.paste,
        android.R.id.pasteAsPlainText,
        android.R.id.selectAll,
    )

    /** Chromium's own item ids live in the WebView package, so they are matched by resource name. */
    val allowedEntryNames: Set<String> = setOf(
        "select_action_menu_copy",
        "select_action_menu_cut",
        "select_action_menu_paste",
        "select_action_menu_paste_as_plain_text",
        "select_action_menu_select_all",
    )

    fun shouldKeep(itemId: Int, entryName: String?, hasIntent: Boolean): Boolean =
        !hasIntent && (itemId in allowedIds || entryName in allowedEntryNames)

    fun hideDisallowed(menu: Menu, entryName: (Int) -> String?) {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (!shouldKeep(item.itemId, entryName(item.itemId), item.intent != null)) item.isVisible = false
        }
    }
}

/** Wraps WebView's selection callback, hiding disallowed items after WebView prepares the menu. */
class FilteringActionModeCallback(
    private val delegate: ActionMode.Callback,
    private val entryName: (Int) -> String?,
) : ActionMode.Callback2() {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = delegate.onCreateActionMode(mode, menu)

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        val result = delegate.onPrepareActionMode(mode, menu)
        if (menu != null) SelectionMenuFilter.hideDisallowed(menu, entryName)
        return result
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = delegate.onActionItemClicked(mode, item)

    override fun onDestroyActionMode(mode: ActionMode?) = delegate.onDestroyActionMode(mode)

    // Floating toolbars position themselves from the delegate's content rect.
    override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: Rect?) {
        if (delegate is ActionMode.Callback2) delegate.onGetContentRect(mode, view, outRect)
        else super.onGetContentRect(mode, view, outRect)
    }
}
