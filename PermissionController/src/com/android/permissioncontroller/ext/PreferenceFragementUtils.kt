package com.android.permissioncontroller.ext

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.android.settingslib.widget.FooterPreference

fun PreferenceFragmentCompat.createFooterPreference(): FooterPreference {
    return FooterPreference(requireContext()).apply {
        isSelectable = false
    }
}

fun PreferenceGroup.addOrRemove(p: Preference,  add: Boolean) {
    val added = p.parent != null
    if (add == added) {
        return
    }
    if (add) {
        addPreference(p)
    } else {
        removePreference(p)
    }
}

fun PreferenceFragmentCompat.createCategory(title: Int): PreferenceCategory {
    return PreferenceCategory(requireContext()).apply {
        setTitle(title)
        key = Integer.toString(title)
    }
}

fun createPref(ctx: Context, title: CharSequence, @DrawableRes icon: Int,
                                        l: Preference.OnPreferenceClickListener): Preference {
    val p = Preference(ctx)
    p.title = title
    if (icon != 0) {
        p.setIcon(icon)
    }
    p.onPreferenceClickListener = l
    return p
}

fun PreferenceFragmentCompat.addPref(title: CharSequence, l: Preference.OnPreferenceClickListener) {
    addPref(title, 0, l)
}

fun PreferenceFragmentCompat.addPref(title: CharSequence, icon: Int, l: Preference.OnPreferenceClickListener) {
    val p = createPref(requireContext(), title, icon, l)
    preferenceScreen.addPreference(p)
}

fun PreferenceFragmentCompat.addOrRemove(p: Preference, add: Boolean) {
    preferenceScreen.addOrRemove(p, add)
}

fun addMenuItem(@StringRes title: Int, menu: Menu): MenuItem {
    return menu.add(Menu.NONE, title, Menu.NONE, title)
}
