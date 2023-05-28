package com.android.permissioncontroller.cscopes

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.ext.cscopes.ContactScope
import android.ext.cscopes.ContactScopesApi
import android.ext.cscopes.ContactScopesApi.SCOPED_CONTACTS_PROVIDER_AUTHORITY
import android.ext.cscopes.ContactScopesStorage
import android.ext.cscopes.ContactsGroup
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.SparseArray
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.preference.PreferenceCategory
import com.android.permissioncontroller.R
import com.android.permissioncontroller.cscopes.ContactScopesUtils.maybeSpecifyPackage
import com.android.permissioncontroller.ext.PackageExtraConfigFragment
import com.android.permissioncontroller.ext.PreferenceWithImageButton
import com.android.permissioncontroller.ext.addMenuItem
import com.android.permissioncontroller.ext.addOrRemove
import com.android.permissioncontroller.ext.createCategory
import com.android.permissioncontroller.ext.createFooterPreference
import com.android.permissioncontroller.permission.ui.handheld.pressBack
import com.android.settingslib.widget.ActionButtonsPreference
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.MainSwitchPreference
import java.util.function.Function

class ContactScopesFragment : PackageExtraConfigFragment(), MenuProvider {
    lateinit var mainSwitch: MainSwitchPreference
    lateinit var actionButtons: ActionButtonsPreference

    lateinit var categories: Array<PreferenceCategory>

    lateinit var footer: FooterPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainSwitch = MainSwitchPreference(context).apply {
            setTitle(R.string.cscopes_enable)
            addOnSwitchChangeListener { _, isChecked ->
                if (isChecked) {
                    setContactScopesEnabled(true)
                }
            }
        }

        actionButtons = ActionButtonsPreference(context).apply {
            setButton1Text(R.string.cscopes_btn_add_label)
            setButton1Icon(R.drawable.ic_cscopes_label)
            setButton1OnClickListener { _ ->
                showContactGroupListDialog()
            }

            setButton2Text(R.string.cscopes_btn_add_contact)
            setButton2Icon(R.drawable.ic_cscopes_contact)
            setButton2OnClickListener {
                launchPicker(ContactScope.TYPE_CONTACT)
            }

            setButton3Text(R.string.cscopes_btn_add_number)
            setButton3Icon(R.drawable.ic_cscopes_number)
            setButton3OnClickListener {
                launchPicker(ContactScope.TYPE_NUMBER)
            }

            setButton4Text(R.string.cscopes_btn_add_email)
            setButton4Icon(R.drawable.ic_cscopes_email)
            setButton4OnClickListener {
                launchPicker(ContactScope.TYPE_EMAIL)
            }
        }

        categories = arrayOf(
            createScopeTypeCategory(ContactScope.TYPE_GROUP, R.string.cscopes_labels),
            createScopeTypeCategory(ContactScope.TYPE_CONTACT, R.string.cscopes_contacts),
            createScopeTypeCategory(ContactScope.TYPE_NUMBER, R.string.cscopes_numbers),
            createScopeTypeCategory(ContactScope.TYPE_EMAIL, R.string.cscopes_emails),
        )

        footer = createFooterPreference()

        requireActivity().addMenuProvider(this, this)
    }

    private fun createScopeTypeCategory(type: Int, label: Int): PreferenceCategory {
        return createCategory(label).apply {
            key = type.toString()
        }
    }

    override fun update() {
        val gosPackageState = getGosPackageStateOrDefault()
        val enabled = gosPackageState.hasFlag(GosPackageState.FLAG_CONTACT_SCOPES_ENABLED)

        addOrRemove(mainSwitch, !enabled)
        if (!enabled) {
            mainSwitch.isChecked = false
        }

        addOrRemove(actionButtons, enabled)

        categories.forEach { addOrRemove(it, enabled) }

        if (enabled) {
            updateCategories()
        }

        var footerSummary = 0

        if (enabled) {
            if (ContactScopesStorage.isEmpty(gosPackageState)) {
                footerSummary = R.string.cscopes_empty_footer
            }
        } else {
            footerSummary = R.string.cscopes_disabled_footer
        }

        if (footerSummary != 0) {
            footer.setSummary(footerSummary)
            footer.setLearnMoreAction {
                val link = "https://grapheneos.org/usage#contact-scopes"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
        }

        addOrRemove(footer, footerSummary != 0)

        requireActivity().invalidateMenu()
    }

    fun updateCategories() {
        val model = callScopedContactsProvider(ContactScopesApi.METHOD_GET_VIEW_MODEL, arg = pkgName)

        if (model == null) {
            pressBack()
            return
        }

        for (category in categories) {
            category.removeAll()

            val typeStr: String = category.key
            val scopes = model.getParcelableArrayList(typeStr, ContactScope::class.java)

            category.isVisible = scopes != null

            if (scopes == null) {
                continue
            }

            val type = typeStr.toInt()

            for (scope in scopes) {
                PreferenceWithImageButton(context).apply {
                    val scopeId = scope.id
                    val scopeTitle = scope.title
                    if (scopeTitle != null) {
                        title = scopeTitle
                        scope.summary?.let { summary = it }
                    } else {
                        title = getString(R.string.cscope_missing_item, scopeId)
                    }
                    setupButton(R.drawable.ic_item_remove, getText(R.string.cscope_btn_remove)) {
                        removeScope(type, scopeId)
                    }
                    val detailsUri = scope.detailsUri
                    if (detailsUri != null) {
                        setOnPreferenceClickListener {
                            if (type == ContactScope.TYPE_GROUP) {
                                openGroup(scopeId)
                            } else {
                                launchIntentView(detailsUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                            }
                            true
                        }
                    } else {
                        isSelectable = false
                    }
                    key = "${type}_${scopeId}"
                }.let {
                    category.addPreference(it)
                }
            }
        }
    }

    fun openGroup(id: Long) {
        val uri = ContactsContract.Groups.CONTENT_URI.buildUpon()
                .appendPath(id.toString()).build()
        launchIntentView(uri, ContactsContract.Groups.CONTENT_ITEM_TYPE)
    }

    fun launchIntentView(uri: Uri, type: String) {
        Intent(Intent.ACTION_VIEW).run {
            setDataAndType(uri, type)
            maybeSpecifyPackage(context_, this)
            startContactsActivityOrToast(this)
        }
    }

    fun onPickerResult(type: Int, intent: Intent?) {
        if (intent == null) {
            return
        }

        val uris: List<Uri> = run {
            val clipData = intent.clipData
            if (clipData != null) {
                val list = ArrayList<Uri>(clipData.itemCount)
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri ?: continue
                    list.add(uri)
                }
                list
            } else {
                val uri = intent.data ?: return@onPickerResult
                listOf(uri)
            }
        }

        val ids = getIdsFromUris(type, uris)

        if (ids == null) {
            val template = resources.getQuantityText(R.plurals.cscopes_toast_unknown_scope, uris.size).toString()
            val text = String.format(template, uris.joinToString())
            toastManager.showToast(text)
            return
        }

        updateStorage { css ->
            var res = false
            for (id in ids) {
                if (css.count >= ContactScopesStorage.MAX_COUNT_PER_PACKAGE) {
                    toastManager.showToast(R.string.scopes_list_is_full)
                    break
                }
                if (addScope(css, type, id)) {
                    res = true
                }
            }
            res
        }
    }

    fun getIdsFromUris(type: Int, uris: List<Uri>): LongArray? {
         when (type) {
            ContactScope.TYPE_CONTACT -> {
                val args = Bundle().apply {
                    putParcelableArray(ContactScopesApi.KEY_URIS, uris.toTypedArray())
                }
                val res = callScopedContactsProvider(ContactScopesApi.METHOD_GET_IDS_FROM_URIS, args)
                if (res == null) {
                    return null
                }
                return res.getLongArray(ContactScopesApi.KEY_IDS)!!
            }
            ContactScope.TYPE_NUMBER, ContactScope.TYPE_EMAIL -> {
                return uris.map {
                    it.lastPathSegment!!.toLong()
                }.toLongArray()
            }
            else -> error(type)
        }
    }

    fun callScopedContactsProvider(method: String, args: Bundle? = null, arg: String? = null): Bundle? {
        return context_.contentResolver.call(SCOPED_CONTACTS_PROVIDER_AUTHORITY, method, arg, args)
    }

    fun addScope(css: ContactScopesStorage, type: Int, id: Long): Boolean {
        check(css.count < ContactScopesStorage.MAX_COUNT_PER_PACKAGE)
        return css.add(type, id)
    }

    fun removeScope(type: Int, id: Long) {
        updateStorage {
            val res = it.remove(type, id)
            if (!res) {
                // GosPackageState was racily modified elsewhere
                update()
            }
            res
        }
    }

    fun notifyContentObservers() {
        val i = Intent(ContactScopesApi.ACTION_NOTIFY_CONTENT_OBSERVERS)
        i.`package` = pkgName
        context_.sendBroadcast(i)
    }

    fun updateStorage(action: Function<ContactScopesStorage, Boolean>) {
        val packageState = getGosPackageStateOrPressBack() ?: return
        val css = ContactScopesStorage.deserialize(packageState)

        if (!action.apply(css)) {
            return
        }

        packageState.edit().run {
            setContactScopes(css.serialize())
            applyOrPressBack()
        }

        notifyContentObservers()
    }

    fun setContactScopesEnabled(enabled: Boolean) {
        if (enabled) {
            if (ContactScopesUtils.revokeContactPermissions(context_, pkgName)) {
                toastManager.showToast(R.string.cscopes_toast_all_contacts_permissions_denied)
            }
        }

        GosPackageState.edit(pkgName).run {
            setFlagsState(GosPackageState.FLAG_CONTACT_SCOPES_ENABLED, enabled)
            setContactScopes(null)
            setKillUidAfterApply(!enabled)
            setNotifyUidAfterApply(true)
            applyOrPressBack()
        }
    }

    fun showContactGroupListDialog() {
        AlertDialog.Builder(context_).run {
            val groupsBundle = callScopedContactsProvider(ContactScopesApi.METHOD_GET_GROUPS)!!

            val groups = groupsBundle.getParcelableArrayList(ContactScopesApi.KEY_RESULT, ContactsGroup::class.java)!!

            val items = groups.map { it.title } + getString(R.string.cscopes_create_label)

            setItems(items.toTypedArray()) { _, idx ->
                if (idx < groups.size) {
                    showContactGroupDialog(groups[idx])
                } else {
                    Intent(Intent.ACTION_INSERT).run {
                        type = ContactsContract.Groups.CONTENT_TYPE
                        maybeSpecifyPackage(context_, this)
                        startContactsActivityOrToast(this)
                    }
                }
            }

            show()
        }
    }

    fun showContactGroupDialog(group: ContactsGroup) {
        val items: Array<Int> = arrayOf(
                R.string.cscope_group_open,
                R.string.cscope_group_add_to_scopes,
        )

        val dialog = AlertDialog.Builder(context_).run {
            setTitle(group.title)
            setItems(items.map { getText(it) }.toTypedArray(), null)
            create()
        }

        dialog.listView.setOnItemClickListener { _, _, pos, _ ->
            when (items[pos]) {
                R.string.cscope_group_open -> {
                    openGroup(group.id)
                }
                R.string.cscope_group_add_to_scopes -> {
                    updateStorage {
                        var res = false
                        if (it.count < ContactScopesStorage.MAX_COUNT_PER_PACKAGE) {
                            res = addScope(it, ContactScope.TYPE_GROUP, group.id)
                        } else {
                            toastManager.showToast(R.string.scopes_list_is_full)
                        }
                        res
                    }

                    dialog.cancel()
                }
            }
        }

        dialog.show()
    }

    override fun getTitle() = getText(R.string.contact_scopes)

    class PickerActivityContract(val dataType: String, val scopeType: Int)
        : ActivityResultContract<Unit, Intent?>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(Intent.ACTION_PICK).apply {
                type = dataType
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                maybeSpecifyPackage(context, this)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
            return if (resultCode == Activity.RESULT_OK) intent!! else null
        }
    }

    val pickerLaunchers = run {
        val pairs = arrayOf(
            ContactScope.TYPE_CONTACT to ContactsContract.Contacts.CONTENT_TYPE,
            ContactScope.TYPE_NUMBER to ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE,
            ContactScope.TYPE_EMAIL to ContactsContract.CommonDataKinds.Email.CONTENT_TYPE,
        )

        val launchers = SparseArray<ActivityResultLauncher<Unit>>()

        pairs.forEach {
            val scopeType = it.first
            val dataType = it.second

            val launcher = registerForActivityResult(PickerActivityContract(dataType, scopeType)) { intent ->
                onPickerResult(scopeType, intent)
            }

            launchers.put(scopeType, launcher)
        }

        launchers
    }

    fun launchPicker(scopeType: Int) {
        try {
            pickerLaunchers[scopeType].launch(Unit)
        } catch (e: ActivityNotFoundException) {
            toastManager.showToast(R.string.cscopes_toast_contacts_app_not_found)
        }
    }

    fun startContactsActivityOrToast(i: Intent) {
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            toastManager.showToast(R.string.cscopes_toast_contacts_app_not_found)
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        if (mainSwitch.parent == null) {
            addMenuItem(R.string.cscopes_turn_off, menu)
        }

        addMenuItem(R.string.cscopes_settings, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.string.cscopes_turn_off ->
                setContactScopesEnabled(false)

            R.string.cscopes_settings -> {
                AlertDialog.Builder(context_).run {
                    setTitle(R.string.cscopes_settings)
                    val items = arrayOf(
                        getText(R.string.cscopes_allow_custom_contacts_app)
                    )
                    val checked = booleanArrayOf(
                        ContactScopesUtils.isCustomContactsAppAllowed(context_)
                    )
                    setMultiChoiceItems(items, checked) { _, pos, isChecked ->
                        check(pos == 0)
                        ContactScopesUtils.setCustomContactsAppAllowed(context_, isChecked)
                    }
                    setNeutralButton(R.string.cscopes_settings_dismiss, null)
                    show()
                }
            }

            else -> return false
        }

        return true
    }
}
