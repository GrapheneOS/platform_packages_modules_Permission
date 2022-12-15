/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.v34;

import static android.content.Intent.EXTRA_PERMISSION_GROUP_NAME;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ACCOUNT_MANAGEMENT;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ADVERTISING;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ANALYTICS;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_APP_FUNCTIONALITY;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_DEVELOPER_COMMUNICATIONS;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_FRAUD_PREVENTION_SECURITY;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_PERSONALIZATION;
import static com.android.permissioncontroller.permission.ui.v34.PermissionRationaleViewHandler.Result.CANCELLED;

import android.content.Intent;
import android.icu.lang.UCharacter;
import android.icu.text.ListFormatter;
import android.os.Build;
import android.os.Bundle;
import android.text.Annotation;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.permission.safetylabel.DataPurposeConstants.Purpose;
import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.SettingsActivity;
import com.android.permissioncontroller.permission.ui.handheld.v34.PermissionRationaleViewHandlerImpl;
import com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModel;
import com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModel.PermissionRationaleInfo;
import com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * An activity which displays runtime permission rationale on behalf of an app. This activity is
 * based on GrantPermissionActivity to keep view behavior and theming consistent.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class PermissionRationaleActivity extends SettingsActivity implements
        PermissionRationaleViewHandler.ResultListener {

    private static final String LOG_TAG = PermissionRationaleActivity.class.getSimpleName();

    private static final String KEY_SESSION_ID = PermissionRationaleActivity.class.getName()
            + "_SESSION_ID";

    /**
     * [Annotation] key for span annotations replacement within the permission rationale purposes
     * string resource
     */
    public static final String ANNOTATION_ID_KEY = "id";
    /**
     * [Annotation] id value for span annotations replacement of link annotations within the
     * permission rationale purposes string resource
     */
    public static final String LINK_ANNOTATION_ID = "link";
    /**
     * [Annotation] id value for span annotations replacement of install source annotations within
     * the permission rationale purposes string resource
     */
    public static final String INSTALL_SOURCE_ANNOTATION_ID = "install_source";
    /**
     * [Annotation] id value for span annotations replacement of purpose list annotations within
     * the permission rationale purposes string resource
     */
    public static final String PURPOSE_LIST_ANNOTATION_ID = "purpose_list";

    /** Unique Id of a request. Inherited from GrantPermissionDialog if provide via intent extra */
    private long mSessionId;
    /** Package that shall have permissions granted */
    private String mTargetPackage;
    /** The permission group that initiated the permission rationale details activity */
    private String mPermissionGroupName;
    /** The permission rationale info resulting from the specified permission and group */
    private PermissionRationaleInfo mPermissionRationaleInfo;

    private PermissionRationaleViewHandler mViewHandler;
    private PermissionRationaleViewModel mViewModel;

    private float mOriginalDimAmount;
    private View mRootView;


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (!KotlinUtils.INSTANCE.isPermissionRationaleEnabled()) {
            Log.e(
                    LOG_TAG,
                    "Permission rationale feature disabled");
            finishAfterTransition();
            return;
        }

        if (icicle == null) {
            mSessionId =
                    getIntent().getLongExtra(Constants.EXTRA_SESSION_ID, new Random().nextLong());
        } else {
            mSessionId = icicle.getLong(KEY_SESSION_ID);
        }

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        mPermissionGroupName = getIntent().getStringExtra(EXTRA_PERMISSION_GROUP_NAME);
        if (mPermissionGroupName == null) {
            Log.e(
                    LOG_TAG,
                    "null EXTRA_PERMISSION_GROUP_NAME. Must be set for permission rationale");
            finishAfterTransition();
            return;
        }

        mTargetPackage = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (mTargetPackage == null) {
            Log.e(LOG_TAG, "null EXTRA_PACKAGE_NAME. Must be set for permission rationale");
            finishAfterTransition();
            return;
        }

        setFinishOnTouchOutside(false);

        setTitle(R.string.permission_rationale_title);

        if (DeviceUtils.isTelevision(this)
                || DeviceUtils.isWear(this)
                || DeviceUtils.isAuto(this)) {
            finishAfterTransition();
        } else {
            mViewHandler = new PermissionRationaleViewHandlerImpl(this, this);
        }

        PermissionRationaleViewModelFactory factory = new PermissionRationaleViewModelFactory(
                getApplication(), mTargetPackage, mPermissionGroupName, mSessionId, icicle);
        mViewModel = factory.create(PermissionRationaleViewModel.class);
        mViewModel.getPermissionRationaleInfoLiveData()
                .observe(this, this::onPermissionRationaleInfoLoad);

        mRootView = mViewHandler.createView();
        mRootView.setVisibility(View.GONE);
        setContentView(mRootView);
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        mOriginalDimAmount = layoutParams.dimAmount;
        window.setAttributes(layoutParams);

        if (getResources().getBoolean(R.bool.config_useWindowBlur)) {
            java.util.function.Consumer<Boolean> blurEnabledListener = enabled -> {
                mViewHandler.onBlurEnabledChanged(window, enabled);
            };
            mRootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    window.getWindowManager().addCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    window.getWindowManager().removeCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }
            });
        }
        // Restore UI state after lifecycle events. This has to be before we show the first request,
        // as the UI behaves differently for updates and initial creations.
        if (icicle != null) {
            mViewHandler.loadInstanceState(icicle);
        } else {
            // Do not show screen dim until data is loaded
            window.setDimAmount(0f);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mViewHandler == null) {
            return;
        }

        mViewHandler.saveInstanceState(outState);

        outState.putLong(KEY_SESSION_ID, mSessionId);
    }

    @Override
    public void onBackPressed() {
        if (mViewHandler == null) {
            return;
        }
        mViewHandler.onBackPressed();
    }

    // LINT.IfChange(dispatchTouchEvent)
    /**
     * Used to dismiss dialog when tapping outside of dialog bounds
     * Follows the same logic as GrantPermissionActivity
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View rootView = getWindow().getDecorView();
        if (rootView.getTop() != 0) {
            // We are animating the top view, need to compensate for that in motion events.
            ev.setLocation(ev.getX(), ev.getY() - rootView.getTop());
        }
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        if ((x < 0) || (y < 0) || (x > (rootView.getWidth())) || (y > (rootView.getHeight()))) {
            if (MotionEvent.ACTION_DOWN == ev.getAction()) {
                mViewHandler.onCancelled();
            }
            finishAfterTransition();
        }
        return super.dispatchTouchEvent(ev);
    }
    // LINT.ThenChange(GrantPermissionsActivity.java:dispatchTouchEvent)

    @Override
    public void onPermissionRationaleResult(@Nullable String groupName, int result) {
        if (result == CANCELLED) {
            finishAfterTransition();
        }
    }

    private void onPermissionRationaleInfoLoad(PermissionRationaleInfo permissionRationaleInfo) {
        if (permissionRationaleInfo == null) {
            finishAfterTransition();
            return;
        }

        mPermissionRationaleInfo = permissionRationaleInfo;
        showPermissionRationale();
    }

    private void showPermissionRationale() {
        List<String> purposesList =
                new ArrayList<>(mPermissionRationaleInfo.getPurposeSet().size());
        for (@Purpose int purpose : mPermissionRationaleInfo.getPurposeSet()) {
            purposesList.add(getStringForPurpose(purpose));
        }

        // TODO(b/260144215): update purposes join based on l18n feedback
        String purposesString = ListFormatter.getInstance().format(purposesList);

        String installSourcePackageName = mPermissionRationaleInfo.getInstallSourcePackageName();
        CharSequence installSourceLabel = mPermissionRationaleInfo.getInstallSourceLabel();
        CharSequence purposeMessage;
        if (installSourcePackageName == null || installSourcePackageName.length() == 0
                || installSourceLabel == null || installSourceLabel.length() == 0) {
            purposeMessage = getString(
                    R.string.permission_rationale_purpose_default_source_message,
                    purposesString);
        } else {
            purposeMessage =
                    createPurposeMessageWithLink(
                            getText(R.string.permission_rationale_purpose_message),
                            installSourceLabel,
                            purposesString,
                            getLinkToAppStore(installSourcePackageName));
        }

        // TODO(b/260144330): link to permission settings
        String groupName = mPermissionRationaleInfo.getGroupName();
        String permissionGroupLabel =
                KotlinUtils.INSTANCE.getPermGroupLabel(this, groupName).toString();
        CharSequence settingsMessage =
                getString(R.string.permission_rationale_permission_settings_message,
                        UCharacter.toLowerCase(permissionGroupLabel));

        // TODO(b/259963582): link to safety label help center article
        CharSequence learnMoreMessage =
                getString(R.string.permission_rationale_permission_learn_more_title);

        mViewHandler.updateUi(
                groupName,
                purposeMessage,
                settingsMessage,
                learnMoreMessage
        );

        getWindow().setDimAmount(mOriginalDimAmount);
        if (mRootView.getVisibility() == View.GONE) {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            manager.hideSoftInputFromWindow(mRootView.getWindowToken(), 0);
            mRootView.setVisibility(View.VISIBLE);
        }
    }

    private String getStringForPurpose(@Purpose int purpose) {
        switch (purpose) {
            case PURPOSE_APP_FUNCTIONALITY:
                return getString(R.string.permission_rational_purpose_app_functionality);
            case PURPOSE_ANALYTICS:
                return getString(R.string.permission_rational_purpose_analytics);
            case PURPOSE_DEVELOPER_COMMUNICATIONS:
                return getString(R.string.permission_rational_purpose_developer_communications);
            case PURPOSE_FRAUD_PREVENTION_SECURITY:
                return getString(R.string.permission_rational_purpose_fraud_prevention_security);
            case PURPOSE_ADVERTISING:
                return getString(R.string.permission_rational_purpose_advertising);
            case PURPOSE_PERSONALIZATION:
                return getString(R.string.permission_rational_purpose_personalization);
            case PURPOSE_ACCOUNT_MANAGEMENT:
                return getString(R.string.permission_rational_purpose_account_management);
            default:
                throw new IllegalArgumentException("Invalid purpose: " + purpose);
        }
    }

    private CharSequence createPurposeMessageWithLink(
            CharSequence purposeText,
            CharSequence installSourceLabel,
            CharSequence purposes,
            ClickableSpan link) {
        SpannableStringBuilder text = SpannableStringBuilder.valueOf(purposeText);
        Annotation[] annotations = text.getSpans(0, text.length(), Annotation.class);
        // Sort the annotations in reverse order.
        Arrays.sort(annotations, (a, b) -> text.getSpanStart(b) - text.getSpanStart(a));
        SpannableStringBuilder messageWithSpan = new SpannableStringBuilder(text);
        for (android.text.Annotation annotation : annotations) {
            if (!annotation.getKey().equals(ANNOTATION_ID_KEY)) {
                continue;
            }

            int spanStart = text.getSpanStart(annotation);
            int spanEnd = text.getSpanEnd(annotation);
            messageWithSpan.removeSpan(annotation);

            switch (annotation.getValue()) {
                case INSTALL_SOURCE_ANNOTATION_ID:
                    messageWithSpan.replace(spanStart, spanEnd, installSourceLabel);
                    break;
                case LINK_ANNOTATION_ID:
                    messageWithSpan.setSpan(link, spanStart, spanEnd, 0);
                    break;
                case PURPOSE_LIST_ANNOTATION_ID:
                    messageWithSpan.replace(spanStart, spanEnd, purposes);
                    break;
                default:
                    continue;
            }
        }
        return messageWithSpan;
    }

    private ClickableSpan getLinkToAppStore(String installSourcePackageName) {
        boolean canLinkToAppStore = mViewModel
                .canLinkToAppStore(PermissionRationaleActivity.this, installSourcePackageName);
        if (!canLinkToAppStore) {
            return null;
        }
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // TODO(b/259961958): metrics for click events
                mViewModel.sendToAppStore(PermissionRationaleActivity.this,
                        installSourcePackageName);
            }
        };
    }
}
