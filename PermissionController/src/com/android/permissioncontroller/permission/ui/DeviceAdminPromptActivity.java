package com.android.permissioncontroller.permission.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.widget.SecureButton;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

// based on .permission.ui.GrantPermissionsActivity  and .permission.ui.handheld.GrantPermissionsViewHandlerImpl
public class DeviceAdminPromptActivity extends SettingsActivity implements View.OnClickListener {

    private static final String INTENT_SUFFIX = "_PROMPT";
    private Intent intent;
    private String pkgName;
    private CharSequence appLabel;
    private int promptStringRes;

    private boolean validateIntent() {
        Intent i = getIntent();
        String action = i.getAction();
        if (action == null) {
            return false;
        }

        promptStringRes = R.string.device_admin_request;

        ComponentName pkgComponent = i.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName.class);
        String pkg = null;

        if (pkgComponent != null) {
            pkg = pkgComponent.getPackageName();
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

        View root = LayoutInflater.from(this).inflate(R.layout.grant_permissions_material3, null);

        // not applicable, this is a custom permission dialog
        root.requireViewById(R.id.permission_rationale_container).setVisibility(View.GONE);

        SecureButton allowButton = root.requireViewById(R.id.permission_allow_button);
        allowButton.setText(R.string.grant_dialog_button_allow_in_settings);

        ViewGroup buttons = (ViewGroup) allowButton.getParent();

        int[] visibleButtons = { R.id.permission_allow_button,
                R.id.permission_deny_button };
        for (int b : visibleButtons) {
            SecureButton button = buttons.requireViewById(b);
            button.setOnClickListener(this);
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

        CharSequence promptText = Utils.getRequestMessage(appLabel.toString(), pkgName,
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
            Bundle extras = intent.getExtras();
            Intent i = new Intent(origAction, intent.getData());
            if (extras != null) {
                i.putExtras(extras);
            }
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
