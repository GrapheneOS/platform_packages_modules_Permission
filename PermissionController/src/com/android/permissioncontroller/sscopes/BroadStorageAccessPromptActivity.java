package com.android.permissioncontroller.sscopes;

import android.app.StorageScope;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.SettingsActivity;
import com.android.permissioncontroller.permission.ui.widget.SecureButton;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

// based on .permission.ui.GrantPermissionsActivity  and .permission.ui.handheld.GrantPermissionsViewHandlerImpl
public class BroadStorageAccessPromptActivity extends SettingsActivity implements View.OnClickListener {

    private static final String INTENT_SUFFIX = "_PROMPT";

    private Intent intent;
    private ApplicationInfo appInfo;
    private String pkgName;
    private CharSequence appLabel;
    private int promptStringRes;

    private boolean validateIntent() {
        Intent i = getIntent();
        String action = i.getAction();
        if (action == null) {
            return false;
        }

        switch (action) {
            case Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION + INTENT_SUFFIX:
                promptStringRes = R.string.sscopes_manage_external_storage_request;
                break;
            case Settings.ACTION_REQUEST_MANAGE_MEDIA + INTENT_SUFFIX:
                promptStringRes = R.string.sscopes_manage_media_request;
                break;
            default:
                return false;
        }

        Uri uri = i.getData();
        String pkg = null;
        if (uri != null && "package".equals(uri.getScheme())) {
            pkg = uri.getSchemeSpecificPart();
        }

        if (TextUtils.isEmpty(pkg)) {
            return false;
        }

        String label = KotlinUtils.INSTANCE.getPackageLabel(getApplication(), pkg, Process.myUserHandle());

        if (TextUtils.isEmpty(label)) {
            return false;
        }

        pkgName = pkg;
        appLabel = label;
        intent = i;
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!validateIntent()) {
            finish();
            return;
        }

        setFinishOnTouchOutside(false);

        setTitle(R.string.permission_request_title); // invisible, needed for accessibility

        View root = LayoutInflater.from(this).inflate(R.layout.grant_permissions, null);

        SecureButton allowButton = root.requireViewById(R.id.permission_allow_button);
        allowButton.setText(R.string.grant_dialog_button_allow_in_settings);

        ViewGroup buttons = (ViewGroup) allowButton.getParent();

        int[] visibleButtons = { R.id.permission_allow_button,
                R.id.permission_deny_button, R.id.permission_storage_scopes_button };
        for (int b : visibleButtons) {
            buttons.requireViewById(b).setOnClickListener(this);
        }

        for (int i = 0, m = buttons.getChildCount(); i < m; ++i) {
            View b = buttons.getChildAt(i);
            if (b instanceof SecureButton) {
                if (!contains(visibleButtons, b.getId())) {
                    b.setVisibility(View.GONE);
                }
            }
        }

        int[] viewsToHide = { R.id.permission_icon, R.id.detail_message, R.id.permission_location_accuracy };
        for (int viewId : viewsToHide) {
            root.requireViewById(viewId).setVisibility(View.GONE);
        }

        CharSequence promptText = Utils.getRequestMessage(appLabel, pkgName,
                "", this, promptStringRes);
        TextView prompt = root.requireViewById(R.id.permission_message);
        prompt.setText(promptText);

        setContentView(root);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.permission_allow_button) {
            String promptAction = intent.getAction();
            String origAction = promptAction.substring(0, promptAction.length() - INTENT_SUFFIX.length());
            Intent i = new Intent(origAction, intent.getData());
            startActivity(i);
        } else if (id == R.id.permission_storage_scopes_button) {
            Intent i = StorageScope.createConfigActivityIntent(pkgName);
            startActivity(i);
        }

        finish();
    }

    private static boolean contains(int[] arr, int v) {
        for (int e : arr) {
            if (e == v) {
                return true;
            }
        }
        return false;
    }
}
