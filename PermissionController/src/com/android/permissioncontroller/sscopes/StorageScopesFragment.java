/*
 * Copyright (C) 2022 GrapheneOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.sscopes;

import android.app.Activity;
import android.app.StorageScope;
import android.app.compat.gms.GmsCompat;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.GosPackageState;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.ext.PackageExtraConfigFragment;
import com.android.permissioncontroller.ext.PreferenceWithImageButton;
import com.android.settingslib.widget.ActionButtonsPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.arrayListOf;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.getFullLabelForPackage;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.getStorageScopes;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.revokeStoragePermissions;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.storageScopesEnabled;

import static com.android.permissioncontroller.ext.PreferenceFragementUtilsKt.addOrRemove;
import static com.android.permissioncontroller.ext.PreferenceFragementUtilsKt.createCategory;
import static com.android.permissioncontroller.ext.PreferenceFragementUtilsKt.createFooterPreference;

public final class StorageScopesFragment extends PackageExtraConfigFragment {
    private static final String TAG = "StorageScopesFragment";

    private Context context;

    private MainSwitchPreference mainSwitch;
    private ActionButtonsPreference actionButtons;

    private PreferenceCategory categoryFolders;
    private PreferenceCategory categoryFiles;

    private FooterPreference footer;

    @Override
    public CharSequence getTitle() {
        return context.getText(R.string.sscopes);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = requireContext();

        {
            MainSwitchPreference s = new MainSwitchPreference(context);
            s.setTitle(R.string.sscopes_enable);
            s.addOnSwitchChangeListener((v, isChecked) -> {
                if (isChecked && !ignoreMainSwitchChange) {
                    setEnabled(true);
                }
            });

            mainSwitch = s;
        }
        {
            ActionButtonsPreference b = new ActionButtonsPreference(context);

            b.setButton1Text(R.string.sscopes_btn_add_folder);
            b.setButton1Icon(R.drawable.ic_sscopes_add_folder);
            b.setButton1OnClickListener(v -> launchPicker(REQUEST_CODE_DIR));

            b.setButton2Text(R.string.sscopes_btn_add_file);
            b.setButton2Icon(R.drawable.ic_sscopes_add_file);
            b.setButton2OnClickListener(v -> launchPicker(REQUEST_CODE_FILE));

            b.setButton3Text(R.string.sscopes_btn_add_image);
            b.setButton3Icon(R.drawable.ic_sscopes_add_image);
            b.setButton3OnClickListener(v -> launchPicker(REQUEST_CODE_IMAGE));

            actionButtons = b;
        }
        categoryFolders = createCategory(this, R.string.sscopes_folders);
        categoryFiles = createCategory(this, R.string.sscopes_files);

        footer = createFooterPreference(this);
    }

    public void update() {
        StorageScope[] scopes = getStorageScopes(pkgName);
        boolean enabled = scopes != null;

        addOrRemove(this, mainSwitch, !enabled);

        if (!enabled) {
            ignoreMainSwitchChange = true;
            mainSwitch.setChecked(false);
            ignoreMainSwitchChange = false;
        }

        addOrRemove(this, actionButtons, enabled);
        addOrRemove(this, categoryFolders, enabled);
        addOrRemove(this, categoryFiles, enabled);

        updateListOfScopes(scopes);

        PackageManager pm = context.getPackageManager();
        String[] uidPkgs = null;
        try {
            int uid = pm.getPackageUid(pkgName, 0);
            uidPkgs = pm.getPackagesForUid(uid);
        } catch (Exception e) {
            Log.d(TAG, "", e);
        }

        if (uidPkgs == null) {
            pressBack(this);
            return;
        }

        boolean addFooter = scopes == null || scopes.length == 0 || uidPkgs.length > 1;
        if (addFooter) {
            StringBuilder summary = new StringBuilder();
            if (scopes == null) {
                summary.append(getString(R.string.sscopes_disabled_footer));
            } else if (scopes.length == 0) {
                summary.append(getString(R.string.sscopes_empty_footer));
            }

            if (uidPkgs.length > 1) {
                if (summary.length() != 0) {
                    summary.append("\n\n");
                }
                summary.append(getString(R.string.sscopes_shared_uid_warning, getFullLabelForPackage(
                        getActivity().getApplication(), uidPkgs, Process.myUserHandle())));
            }

            footer.setSummary(summary.toString());

            if (scopes == null || scopes.length == 0) {
                footer.setLearnMoreAction((v) -> {
                    String link = "https://grapheneos.org/usage#storage-access";
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                });
            }
        }
        addOrRemove(this, footer, addFooter);

        getActivity().invalidateOptionsMenu();
    }

    private void setEnabled(boolean enabled) {
        boolean killUid;
        if (enabled) {
            // GMS often needs a restart to properly handle permission grants
            killUid = GmsCompat.isGmsApp(pkgName, Process.myUserHandle().getIdentifier());

            if (revokeStoragePermissions(context, pkgName)) {
                getToastManager().showToast(R.string.sscopes_storage_permissions_revoked);
            }
        } else {
            killUid = true;
        }

        boolean psUpdated = GosPackageState.edit(pkgName)
                .setFlagsState(GosPackageState.FLAG_STORAGE_SCOPES_ENABLED, enabled)
                .setStorageScopes(null) // always empty initially
                .setKillUidAfterApply(killUid)
                .setNotifyUidAfterApply(true)
                .apply();

        invalidateMediaProviderCache(null);

        if (!psUpdated) {
            // failed to updated GosPackageState, likely because the package was uninstalled
            pressBack(this);
            return;
        }

        update();
    }

    boolean ignoreMainSwitchChange;

    private void updateListOfScopes(StorageScope[] scopes) {
        categoryFolders.removeAll();
        categoryFiles.removeAll();

        if (scopes == null) {
            categoryFolders.setVisible(false);
            categoryFiles.setVisible(false);
            return;
        }

        Context ctx = context;

        List<StorageVolume> storageVolumes = ctx.getSystemService(StorageManager.class).getStorageVolumes();

        for (StorageScope scope : scopes) {
            PreferenceCategory category;
            if (scope.isDirectory()) {
                category = categoryFolders;
            } else if (scope.isFile()) {
                category = categoryFiles;
            } else {
                throw new IllegalStateException();
            }

            var p = new PreferenceWithImageButton(ctx);
            p.setTitle(StorageScopesUtils.pathToUiLabel(ctx, storageVolumes, new File(scope.path)));

            p.setupButton(R.drawable.ic_item_remove, getText(R.string.scopes_btn_remove_scope),
                    () -> removeScope(scope));

            p.setSelectable(false);

            p.setKey(scope.path);

            category.addPreference(p);
        }

        categoryFolders.setVisible(categoryFolders.getPreferenceCount() != 0);
        categoryFiles.setVisible(categoryFiles.getPreferenceCount() != 0);
    }

    void removeScope(StorageScope scope) {
        GosPackageState ps = GosPackageState.get(pkgName);

        if (ps == null) {
            return;
        }

        StorageScope[] scopesArray = StorageScope.deserializeArray(ps);
        ArrayList<StorageScope> scopesList = arrayListOf(scopesArray);

        if (!scopesList.remove(scope)) {
            return;
        }

        scopesArray = scopesList.toArray(new StorageScope[0]);

        byte[] serializedScopes = StorageScope.serializeArray(scopesArray);

        boolean psUpdated = ps.edit()
                .addFlags(GosPackageState.FLAG_STORAGE_SCOPES_ENABLED)
                .setStorageScopes(serializedScopes)
                .apply();

        if (!psUpdated) {
            pressBack(this);
            return;
        }

        ArrayList<String> changedPaths = new ArrayList<>();
        changedPaths.add(scope.path);
        invalidateMediaProviderCache(changedPaths);

        update();
    }

    void launchPicker(int mode) {
        StorageScope[] scopes = getStorageScopes(pkgName);
        if (scopes == null) {
            pressBack(this);
            return;
        }
        int availableSpace = StorageScope.maxArrayLength() - scopes.length;
        if (availableSpace < 1) {
            getToastManager().showToast(R.string.scopes_list_is_full);
            return;
        }

        if (mode == REQUEST_CODE_IMAGE) {
            Intent i = new Intent(MediaStore.ACTION_PICK_IMAGES);
            if (availableSpace > 1) {
                int maxCnt = Math.min(availableSpace, MediaStore.getPickImagesMaxLimit());
                i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxCnt);
            }
            startActivityForResult(i, REQUEST_CODE_IMAGE);
            return;
        }

        Intent i = new Intent(mode == REQUEST_CODE_DIR ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, pkgName);

        ArrayList<String> allowedAuthorities = new ArrayList<>();
        allowedAuthorities.add(DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY);

        if (mode == REQUEST_CODE_FILE) {
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            i.addCategory(Intent.CATEGORY_OPENABLE);

            // com.android.providers.media.MediaDocumentsProvider
            allowedAuthorities.add("com.android.providers.media.documents");
            // com.android.providers.downloads.DownloadStorageProvider
            allowedAuthorities.add("com.android.providers.downloads.documents");
        }

        i.putStringArrayListExtra(Intent.EXTRA_RESTRICTIONS_LIST, allowedAuthorities);

        //noinspection deprecation
        startActivityForResult(i, mode);
    }

    private static final int REQUEST_CODE_DIR = 1;
    private static final int REQUEST_CODE_FILE = 2;
    private static final int REQUEST_CODE_IMAGE = 3;

    /** @noinspection deprecation*/
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        switch (requestCode) {
            case REQUEST_CODE_DIR:
            case REQUEST_CODE_FILE:
            case REQUEST_CODE_IMAGE:
                break;
            default:
                return;
        }

        if (resultCode != Activity.RESULT_OK || intent == null) {
            return;
        }

        GosPackageState curPkgState = GosPackageState.get(pkgName);
        if (!storageScopesEnabled(curPkgState)) {
            // other instance of this fragment updated curPkgState
            pressBack(this);
            return;
        }

        int scopeFlags = 0;

        if (curPkgState.hasDerivedFlag(GosPackageState.DFLAG_EXPECTS_STORAGE_WRITE_ACCESS)) {
            scopeFlags |= StorageScope.FLAG_ALLOW_WRITES;
        }

        ArrayList<StorageScope> newScopes = new ArrayList<>();

        if (requestCode == REQUEST_CODE_DIR) {
            Uri uri = Objects.requireNonNull(intent.getData());

            String path = StorageScopesUtils.dirUriToPath(context, uri);

            if (path != null) {
                scopeFlags |= StorageScope.FLAG_IS_DIR;
                newScopes.add(new StorageScope(path, scopeFlags));
            }
        } else {
            ClipData clipData = intent.getClipData();

            if (clipData != null) {
                List<StorageVolume> cachedVolumes = context.getSystemService(StorageManager.class).getStorageVolumes();

                for (int i = 0, m = clipData.getItemCount(); i < m; ++i) {
                    ClipData.Item item = clipData.getItemAt(i);
                    Uri uri = Objects.requireNonNull(item.getUri());

                    String path = StorageScopesUtils.fileUriToPath(context, uri, cachedVolumes);
                    if (path != null) {
                        newScopes.add(new StorageScope(path, scopeFlags));
                    }
                }
            } else {
                Uri uri = Objects.requireNonNull(intent.getData());

                String path = StorageScopesUtils.fileUriToPath(context, uri, null);
                if (path != null) {
                    newScopes.add(new StorageScope(path, scopeFlags));
                }
            }
        }

        if (newScopes.size() == 0) {
            getToastManager().showToast(R.string.sscopes_unknown_location);
            return;
        }

        ArrayList<StorageScope> currentScopes = arrayListOf(StorageScope.deserializeArray(curPkgState));

        if (currentScopes.size() >= StorageScope.maxArrayLength()) {
            getToastManager().showToast(R.string.scopes_list_is_full);
            return;
        }

        ArrayList<String> addedPaths = new ArrayList<>();

        for (StorageScope newScope : newScopes) {
            if (currentScopes.contains(newScope)) {
                continue;
            }

            if (currentScopes.size() >= StorageScope.maxArrayLength()) {
                break;
            }
            currentScopes.add(0, newScope);
            addedPaths.add(newScope.path);
        }

        if (addedPaths.size() == 0) {
            return;
        }

        StorageScope[] scopesArray = currentScopes.toArray(new StorageScope[0]);

        boolean psUpdated = GosPackageState.edit(pkgName)
                .addFlags(GosPackageState.FLAG_STORAGE_SCOPES_ENABLED)
                .setStorageScopes(StorageScope.serializeArray(scopesArray))
                .apply();

        invalidateMediaProviderCache(addedPaths);

        if (!psUpdated) {
            // unable to save newPkgState, likely because the package was racily uninstalled
            pressBack(this);
            return;
        }
    }

    private void invalidateMediaProviderCache(@Nullable ArrayList<String> changedPaths) {
        Bundle args = new Bundle(2);
        args.putString(Intent.EXTRA_PACKAGE_NAME, pkgName);
        args.putStringArrayList(Intent.EXTRA_TEXT, changedPaths);

        ContentResolver cr = context.getContentResolver();

        try {
            cr.call(MediaStore.AUTHORITY, StorageScope.MEDIA_PROVIDER_METHOD_INVALIDATE_MEDIA_PROVIDER_CACHE,
                    null, args);
        } catch (Exception e) {
            Log.d(TAG, "", e);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (storageScopesEnabled(pkgName)) {
            menuItemTurnOff = menu.add(R.string.scopes_menu_item_turn_off);
        }
    }

    private MenuItem menuItemTurnOff;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (menuItemTurnOff == item) {
            setEnabled(false);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
