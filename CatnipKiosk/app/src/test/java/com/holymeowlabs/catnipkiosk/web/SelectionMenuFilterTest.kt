package com.holymeowlabs.catnipkiosk.web

import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Proxy
import org.junit.Test

class SelectionMenuFilterTest {

    @Test
    fun editingItemsWithoutAnIntentAreKept() {
        listOf(
            android.R.id.copy,
            android.R.id.cut,
            android.R.id.paste,
            android.R.id.pasteAsPlainText,
            android.R.id.selectAll,
        ).forEach { assertThat(SelectionMenuFilter.shouldKeep(it, entryName = null, hasIntent = false)).isTrue() }
    }

    @Test
    fun chromiumEditingItemsAreKeptByResourceName() {
        // Chromium's menu uses ids from the WebView package; observed on the API 36 emulators.
        listOf(
            "select_action_menu_copy",
            "select_action_menu_cut",
            "select_action_menu_paste",
            "select_action_menu_paste_as_plain_text",
            "select_action_menu_select_all",
        ).forEach { assertThat(SelectionMenuFilter.shouldKeep(0x0201010a, it, hasIntent = false)).isTrue() }
    }

    @Test
    fun chromiumShareAndWebSearchAreDropped() {
        assertThat(SelectionMenuFilter.shouldKeep(0x02010111, "select_action_menu_share", hasIntent = false)).isFalse()
        assertThat(SelectionMenuFilter.shouldKeep(0x02010113, "select_action_menu_web_search", hasIntent = false))
            .isFalse()
    }

    @Test
    fun anyItemCarryingAnIntentIsDropped() {
        assertThat(SelectionMenuFilter.shouldKeep(0x7f0a1234, entryName = null, hasIntent = true)).isFalse()
        assertThat(SelectionMenuFilter.shouldKeep(android.R.id.copy, entryName = null, hasIntent = true)).isFalse()
        assertThat(SelectionMenuFilter.shouldKeep(0x0201010a, "select_action_menu_copy", hasIntent = true)).isFalse()
    }

    @Test
    fun unknownItemWithoutAnIntentIsDropped() {
        // WebView's own Share / Web search items use ids from its package.
        assertThat(SelectionMenuFilter.shouldKeep(0x7f0a1234, entryName = null, hasIntent = false)).isFalse()
        assertThat(SelectionMenuFilter.shouldKeep(android.R.id.shareText, entryName = null, hasIntent = false)).isFalse()
    }

    @Test
    fun prepareHidesDisallowedItemsAfterTheDelegateRuns() {
        val copy = FakeItem(0x0201010a)
        val share = FakeItem(0x02010111)
        val processText = FakeItem(0, intent = true)
        val delegate = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = true
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                // The delegate (WebView) may re-show items while preparing.
                listOf(copy, share, processText).forEach { it.visible = true }
                return true
            }
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }

        val names = mapOf(0x0201010a to "select_action_menu_copy", 0x02010111 to "select_action_menu_share")
        val result = FilteringActionModeCallback(delegate, names::get)
            .onPrepareActionMode(null, fakeMenu(copy, share, processText))

        assertThat(result).isTrue()
        assertThat(copy.visible).isTrue()
        assertThat(share.visible).isFalse()
        assertThat(processText.visible).isFalse()
    }

    private class FakeItem(val id: Int, val intent: Boolean = false) {
        var visible = true
        val proxy: MenuItem = Proxy.newProxyInstance(
            MenuItem::class.java.classLoader,
            arrayOf(MenuItem::class.java),
        ) { self, method, args ->
            when (method.name) {
                "getItemId" -> id
                "getIntent" -> if (intent) Intent() else null
                "isVisible" -> visible
                "setVisible" -> { visible = args[0] as Boolean; self }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as MenuItem
    }

    private fun fakeMenu(vararg items: FakeItem): Menu =
        Proxy.newProxyInstance(Menu::class.java.classLoader, arrayOf(Menu::class.java)) { _, method, args ->
            when (method.name) {
                "size" -> items.size
                "getItem" -> items[args[0] as Int].proxy
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Menu
}
