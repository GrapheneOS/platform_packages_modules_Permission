package com.android.permissioncontroller.privacy

import android.content.Intent
import android.content.pm.GosPackageState
import android.content.pm.GosPackageStateFlag
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigFragment
import com.android.permissioncontroller.ext.addOrRemove
import com.android.permissioncontroller.ext.createFooterPreference
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.MainSwitchPreference

class LocationIndicatorFragment : PackageExtraConfigFragment() {
    lateinit var mainSwitch: MainSwitchPreference
    lateinit var footer: FooterPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainSwitch = MainSwitchPreference(context_).apply {
            setTitle(R.string.hide_location_indicator_title)
            setOnPreferenceChangeListener { _, newValue ->
                val editor = GosPackageState.edit(pkgName, context_.user)
                editor.setFlagState(
                    GosPackageStateFlag.HIDE_LOCATION_INDICATOR,
                    newValue as Boolean
                )
                val result = editor.apply()
                if (result) {
                    Toast.makeText(
                        context_,
                        R.string.hide_location_indicator_toast,
                        Toast.LENGTH_LONG
                    ).show()
                }
                result
            }
        }

        footer = createFooterPreference()

        update()
    }

    override fun getTitle(): CharSequence = getText(R.string.location_indicator)

    override fun update() {
        val state = getGosPackageState()
        mainSwitch.isChecked = state.hasFlag(GosPackageStateFlag.HIDE_LOCATION_INDICATOR)
        addOrRemove(mainSwitch, true)

        footer.setSummary(R.string.location_indicator_footer)
        footer.setLearnMoreAction {
            val link = "https://grapheneos.org/features#location-data-access-indicator"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }
        addOrRemove(footer, true)
    }
}