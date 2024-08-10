/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.auto;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALL_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALWAYS_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_FOREGROUND_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ONE_TIME_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_SELECTED_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.COARSE_RADIO_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_BOTH_LOCATIONS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_COARSE_LOCATION_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_FINE_LOCATION_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DONT_ALLOW_MORE_SELECTED_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.FINE_RADIO_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.LOCATION_ACCURACY_LAYOUT;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NEXT_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NEXT_LOCATION_DIALOG;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.CANCELED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_MORE;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ONE_TIME;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_USER_SELECTED;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.text.method.LinkMovementMethod;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.RawRes;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An extension of {@link GrantPermissionsAutoViewHandler} that updates the current auto location
 * permission prompt with a custom view
 */
public class AutoLocationPermissionPromptView implements View.OnClickListener {
    private static final int[] LOCATION_ACCURACY_DIALOGS =
    {
        DIALOG_WITH_BOTH_LOCATIONS,
        DIALOG_WITH_FINE_LOCATION_ONLY,
        DIALOG_WITH_COARSE_LOCATION_ONLY
    };
    private static final long ANIMATION_DURATION_MILLIS = 200;
    private static final float LOCATION_IMAGE_SIZE_MODIFIER = 2;
    private final Context mContext;
    private GrantPermissionsViewHandler.ResultListener mResultListener;
    private String mGroupName;
    private Icon mGroupIcon;
    private CharSequence mGroupMessage;
    private CharSequence mDetailMessage;
    private boolean[] mButtonVisibilities;
    private boolean[] mLocationVisibilities;
    private SparseIntArray mButtonResIdToNum = new SparseIntArray();
    private SparseIntArray mLocationResIdToNum = new SparseIntArray();
    private boolean mIsLocationPermissionDialogActionClicked = false;
    private LottieDrawable mCoarseOffDrawable = null;
    private LottieDrawable mCoarseOnDrawable = null;
    private LottieDrawable mFineOffDrawable = null;
    private LottieDrawable mFineOnDrawable = null;

    // Views
    private ViewGroup mRootView = null;
    private TextView mMessageView = null;
    private TextView mDetailedMessageView = null;
    private Button[] mButtons = new Button[NEXT_BUTTON];
    private View[] mLocationViews = new View[NEXT_LOCATION_DIALOG];

    // Dialog Config
    private RadioButton mCoarseRadioButton = null;
    private RadioButton mFineRadioButton = null;
    private int mSelectedPrecision = 0;
    private float mLocationAccuracyImageDiameter = 0;

    /**
     * Constructs a new AutoLocationPermissionPromptView.
     *
     * @param context the Context in which the view is created
     */
    public AutoLocationPermissionPromptView(Context context) {
        mContext = context;
        generateResIdToButtonMap();
        mLocationAccuracyImageDiameter =
            mContext.getResources().getDimension(R.dimen.location_accuracy_image_size)
                    + LOCATION_IMAGE_SIZE_MODIFIER;
    }

    /**
     * Sets the ResultListener for this view. The ResultListener will be notified when
     * the user makes a selection in the permission prompt.
     *
     * @param listener the ResultListener to set
     */
    public void setResultListener(GrantPermissionsViewHandler.ResultListener listener) {
        mResultListener = listener;
    }

    /**
     * Creates and initializes the custom view for the auto location permission prompt.
     * This method inflates the layout, sets up various UI elements, and configures their behaviors
     * such as click listeners and gravity adjustments.
     */
    public View createView() {
        ViewGroup rootView = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.auto_grant_permissions_material3, null);

        this.mRootView = rootView;

        int gravity = rootView.requireViewById(R.id.grant_singleton).getForegroundGravity();
        int verticalGravity = Gravity.VERTICAL_GRAVITY_MASK & gravity;
        if (mContext instanceof Activity activity) {
            Window window = activity.getWindow();
            window.setGravity(Gravity.CENTER_HORIZONTAL | verticalGravity);
        }

        rootView.requireViewById(R.id.grant_singleton).setOnClickListener(this);
        rootView.requireViewById(R.id.grant_dialog).setOnClickListener(this);

        mMessageView = rootView.requireViewById(R.id.permission_message);
        mDetailedMessageView = rootView.requireViewById(R.id.detail_message);
        mDetailedMessageView.setMovementMethod(LinkMovementMethod.getInstance());

        Button[] buttons = new Button[NEXT_BUTTON];
        int numButtons = mButtonResIdToNum.size();
        for (int i = 0; i < numButtons; i++) {
            Button button = rootView.requireViewById(mButtonResIdToNum.keyAt(i));
            button.setOnClickListener(this);
            buttons[mButtonResIdToNum.valueAt(i)] = button;
        }
        this.mButtons = buttons;

        View[] locationViews = new View[NEXT_LOCATION_DIALOG];
        for (int i = 0; i < mLocationResIdToNum.size(); i++) {
            View locationView = rootView.requireViewById(mLocationResIdToNum.keyAt(i));
            locationViews[mLocationResIdToNum.valueAt(i)] = locationView;
        }

        initializeAnimatedImages();

        mCoarseRadioButton = (RadioButton) locationViews[COARSE_RADIO_BUTTON];
        mFineRadioButton = (RadioButton) locationViews[FINE_RADIO_BUTTON];
        mCoarseRadioButton.setOnClickListener(this);
        mFineRadioButton.setOnClickListener(this);
        this.mLocationViews = locationViews;

        if (mDetailMessage != null) {
            updateAll();
        }
        return rootView;
    }

    private void initializeAnimatedImages() {
        mCoarseOffDrawable = getLottieDrawable(R.raw.coarse_loc_off);
        mCoarseOnDrawable = getLottieDrawable(R.raw.coarse_loc_on);
        mFineOffDrawable = getLottieDrawable(R.raw.fine_loc_off);
        mFineOnDrawable = getLottieDrawable(R.raw.fine_loc_on);
    }

    private LottieDrawable getLottieDrawable(@RawRes int rawResId) {
        LottieComposition composition = LottieCompositionFactory
                .fromRawResSync(mContext, rawResId).getValue();
        if (composition == null) {
            throw new NullPointerException();
        }

        float scale = mLocationAccuracyImageDiameter / composition.getBounds().width();
        LottieDrawable drawable = new LottieDrawable() {
            @Override
            public int getIntrinsicHeight() {
                return ((int) (super.getIntrinsicHeight() * scale));
            }

            @Override
            public int getIntrinsicWidth() {
                return ((int) (super.getIntrinsicWidth() * scale));
            }
        };
        drawable.setComposition(composition);
        return drawable;
    }

    /**
     * Updates the state of the view with the provided parameters. This method is called to refresh
     * the UI elements with new data, ensuring that the view reflects the latest information.
     */
    public void updateState(String groupName, Icon icon,
            CharSequence message, CharSequence detailMessage, boolean[] buttonVisibilities,
            boolean[] locationVisibilities, int selectedPrecision) {

        mGroupName = groupName;
        mGroupIcon = icon;
        mGroupMessage = message;
        mDetailMessage = detailMessage;
        mSelectedPrecision = selectedPrecision;
        setButtonVisibilities(buttonVisibilities);
        setLocationVisibilities(locationVisibilities);

        // If the iconView is null, it's likely other related views are also null. Attempting to
        // update variables in this state could lead to a null object reference and crash the app.
        if (mMessageView != null) {
            updateAll();
        }
    }

    private void updateAll() {
        updateDescription();
        updateDetailDescription();
        updateButtons();
        updateLocationVisibilities();

        ChangeBounds growShrinkToNewContentSize = new ChangeBounds();
        growShrinkToNewContentSize.setDuration(ANIMATION_DURATION_MILLIS);
        growShrinkToNewContentSize.setInterpolator(
                AnimationUtils.loadInterpolator(mContext, android.R.interpolator.fast_out_slow_in));
        TransitionManager.beginDelayedTransition(mRootView, growShrinkToNewContentSize);
    }

    private void updateLocationVisibilities() {
        if (mLocationVisibilities[LOCATION_ACCURACY_LAYOUT]) {
            if (mIsLocationPermissionDialogActionClicked) {
                return;
            }
            mLocationViews[LOCATION_ACCURACY_LAYOUT].setVisibility(View.VISIBLE);
            for (int i : LOCATION_ACCURACY_DIALOGS) {
                if (mLocationVisibilities[i]) {
                    mLocationViews[i].setVisibility(View.VISIBLE);
                } else {
                    mLocationViews[i].setVisibility(View.GONE);
                }
            }

            if (mLocationVisibilities[DIALOG_WITH_BOTH_LOCATIONS]) {
                mCoarseRadioButton.setVisibility(View.VISIBLE);
                mFineRadioButton.setVisibility(View.VISIBLE);
                if (mSelectedPrecision == 0) {
                    mFineRadioButton.setChecked(mLocationVisibilities[FINE_RADIO_BUTTON]);
                    mCoarseRadioButton.setChecked(mLocationVisibilities[COARSE_RADIO_BUTTON]);
                } else {
                    mFineRadioButton.setChecked(mSelectedPrecision == FINE_RADIO_BUTTON);
                    mCoarseRadioButton.setChecked(mSelectedPrecision == COARSE_RADIO_BUTTON);
                }
                if (mCoarseRadioButton.isChecked()) {
                    runLocationAccuracyAnimation(false);

                } else if (mFineRadioButton.isChecked()) {
                    runLocationAccuracyAnimation(true);
                }
            } else if (mLocationVisibilities[DIALOG_WITH_COARSE_LOCATION_ONLY]) {
                ((ImageView) mLocationViews[DIALOG_WITH_COARSE_LOCATION_ONLY])
                        .setImageDrawable(mCoarseOnDrawable);
                mCoarseOnDrawable.start();
            } else if (mLocationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]) {
                ((ImageView) mLocationViews[DIALOG_WITH_FINE_LOCATION_ONLY])
                        .setImageDrawable(mFineOnDrawable);
                mFineOnDrawable.start();
            }
        } else {
            mLocationViews[LOCATION_ACCURACY_LAYOUT].setVisibility(View.GONE);
            for (int i : LOCATION_ACCURACY_DIALOGS) {
                mLocationVisibilities[i] = false;
                mLocationViews[i].setVisibility(View.GONE);
            }
        }
    }

    private void runLocationAccuracyAnimation(boolean isFineSelected) {
        if (isFineSelected) {
            mCoarseOnDrawable.stop();
            mFineOffDrawable.stop();
            // Sets the drawable to appear to the left, top, right, or bottom of the text
            mCoarseRadioButton.setCompoundDrawablesWithIntrinsicBounds(
                    /* left */ null,
                    /* top */ mCoarseOffDrawable,
                    /* right */ null,
                    /* bottom */ null
            );
            mFineRadioButton.setCompoundDrawablesWithIntrinsicBounds(
                    /* left */ null,
                    /* top */ mFineOnDrawable,
                    /* right */ null,
                    /* bottom */ null
            );
            mCoarseOffDrawable.start();
            mFineOnDrawable.start();
            mFineRadioButton.setTypeface(null, Typeface.BOLD);
            mCoarseRadioButton.setTypeface(null, Typeface.NORMAL);
        } else {
            mCoarseOffDrawable.stop();
            mFineOnDrawable.stop();
            mCoarseRadioButton.setCompoundDrawablesWithIntrinsicBounds(
                    /* left */ null,
                    /* top */ mCoarseOnDrawable,
                    /* right */ null,
                    /* bottom */ null
            );
            mFineRadioButton.setCompoundDrawablesWithIntrinsicBounds(
                    /* left */ null,
                    /* top */ mFineOffDrawable,
                    /* right */ null,
                    /* bottom */ null
            );
            mFineOffDrawable.start();
            mCoarseOnDrawable.start();
            mCoarseRadioButton.setTypeface(null, Typeface.BOLD);
            mFineRadioButton.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void updateButtons() {
        for (int i = 0; i < mButtonResIdToNum.size(); i++) {
            int pos = mButtonResIdToNum.valueAt(i);
            if (pos < mButtonVisibilities.length && mButtonVisibilities[pos]) {
                mButtons[pos].setVisibility(View.VISIBLE);
            } else {
                mButtons[pos].setVisibility(View.GONE);
            }
            if (pos == ALLOW_FOREGROUND_BUTTON && mButtonVisibilities[pos]) {
                if (mLocationVisibilities[LOCATION_ACCURACY_LAYOUT]
                        && mLocationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]
                ) {
                    mButtons[pos].setText(mContext.getResources().getString(
                            R.string.grant_dialog_button_change_to_precise_location));
                } else {
                    mButtons[pos].setText(mContext.getResources().getString(
                            R.string.grant_dialog_button_allow_foreground));
                }
            }
            if ((pos == DENY_BUTTON || pos == DENY_AND_DONT_ASK_AGAIN_BUTTON)) {
                if (
                        mLocationVisibilities[LOCATION_ACCURACY_LAYOUT]
                                && mLocationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]
                ) {
                    mButtons[pos].setText(mContext.getResources().getString(
                            R.string.grant_dialog_button_keey_approximate_location));
                } else {
                    mButtons[pos].setText(
                            mContext.getResources().getString(R.string.grant_dialog_button_deny));
                }
            }
            mButtons[pos].requestLayout();
        }
    }

    private void updateDetailDescription() {
        if (mDetailMessage == null) {
            mDetailedMessageView.setVisibility(View.GONE);
        } else {
            mDetailedMessageView.setText(mDetailMessage);
            mDetailedMessageView.setVisibility(View.VISIBLE);
        }
    }

    private void updateDescription() {
        mMessageView.setText(mGroupMessage);
    }

    private void setButtonVisibilities(boolean[] visibilities) {
        // If GrantPermissionsActivity sent the user directly to settings, button visibilities are
        // not created. If the activity was then destroyed by the system, once the activity is
        // recreated to perform onActivityResult, it will try to loadInstanceState in onCreate but
        // the button visibilities were never set, so they will be null.
        mButtonVisibilities = visibilities == null ? new boolean[0] : visibilities;
    }

    private void setLocationVisibilities(boolean[] visibilities) {
        mLocationVisibilities = visibilities == null ? new boolean[0] : visibilities;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.permission_location_accuracy_radio_fine) {
            if (mSelectedPrecision != FINE_RADIO_BUTTON) {
                ((RadioButton) mLocationViews[FINE_RADIO_BUTTON]).setChecked(true);
                mSelectedPrecision = FINE_RADIO_BUTTON;
                runLocationAccuracyAnimation(true);
            }
            return;
        }

        if (id == R.id.permission_location_accuracy_radio_coarse) {
            if (mSelectedPrecision != COARSE_RADIO_BUTTON) {
                ((RadioButton) mLocationViews[COARSE_RADIO_BUTTON]).setChecked(true);
                mSelectedPrecision = COARSE_RADIO_BUTTON;
                runLocationAccuracyAnimation(false);
            }
            return;
        }

        if (mLocationVisibilities[LOCATION_ACCURACY_LAYOUT]) {
            mIsLocationPermissionDialogActionClicked = true;
        }

        if (id == R.id.grant_singleton) {
            mResultListener.onPermissionGrantResult(mGroupName, CANCELED);
            return;
        }

        List<String> affectedForegroundPermissions = new ArrayList<>();
        if (mLocationVisibilities[DIALOG_WITH_BOTH_LOCATIONS]) {
            if (((RadioGroup) mLocationViews[DIALOG_WITH_BOTH_LOCATIONS])
                    .getCheckedRadioButtonId() == R.id.permission_location_accuracy_radio_coarse) {

                affectedForegroundPermissions.add(ACCESS_COARSE_LOCATION);
            } else if (((RadioGroup) mLocationViews[DIALOG_WITH_BOTH_LOCATIONS])
                    .getCheckedRadioButtonId() == R.id.permission_location_accuracy_radio_fine) {

                affectedForegroundPermissions.add(ACCESS_FINE_LOCATION);
                affectedForegroundPermissions.add(ACCESS_COARSE_LOCATION);
            }
        } else if (mLocationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]) {
            affectedForegroundPermissions.add(ACCESS_FINE_LOCATION);
        } else if (mLocationVisibilities[DIALOG_WITH_COARSE_LOCATION_ONLY]) {
            affectedForegroundPermissions.add(ACCESS_COARSE_LOCATION);
        }


        Map<Integer, Integer> buttonToResultValueMap = initializeButtonToResultValueMap();
        int resultValue = buttonToResultValueMap.getOrDefault(
                mButtonResIdToNum.get(id, -1), -1);
        if (resultValue != -1) {
            if (resultValue != DENIED_MORE) {
                view.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
            }
            mResultListener.onPermissionGrantResult(mGroupName, affectedForegroundPermissions,
                    resultValue);
        }
    }

    private Map<Integer, Integer> initializeButtonToResultValueMap() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(ALLOW_ALL_BUTTON, GRANTED_ALWAYS);
        map.put(ALLOW_BUTTON, GRANTED_ALWAYS);
        map.put(ALLOW_FOREGROUND_BUTTON, GRANTED_FOREGROUND_ONLY);
        map.put(ALLOW_ALWAYS_BUTTON, GRANTED_ALWAYS);
        map.put(ALLOW_ONE_TIME_BUTTON, GRANTED_ONE_TIME);
        map.put(ALLOW_SELECTED_BUTTON, GRANTED_USER_SELECTED);
        map.put(DONT_ALLOW_MORE_SELECTED_BUTTON, DENIED_MORE);
        map.put(DENY_BUTTON, DENIED);
        map.put(NO_UPGRADE_BUTTON, DENIED);
        map.put(NO_UPGRADE_OT_BUTTON, DENIED);
        map.put(DENY_AND_DONT_ASK_AGAIN_BUTTON, DENIED_DO_NOT_ASK_AGAIN);
        map.put(NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON, DENIED_DO_NOT_ASK_AGAIN);
        map.put(NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON, DENIED_DO_NOT_ASK_AGAIN);
        return map;
    }

    private void generateResIdToButtonMap() {
        mButtonResIdToNum.put(R.id.permission_allow_button, ALLOW_BUTTON);
        mButtonResIdToNum.put(
                R.id.permission_allow_foreground_only_button,
                ALLOW_FOREGROUND_BUTTON
        );
        mButtonResIdToNum.put(R.id.permission_deny_button, DENY_BUTTON);
        mButtonResIdToNum.put(
                R.id.permission_deny_and_dont_ask_again_button,
                DENY_AND_DONT_ASK_AGAIN_BUTTON
        );
        mButtonResIdToNum.put(R.id.permission_allow_one_time_button, ALLOW_ONE_TIME_BUTTON);
        mButtonResIdToNum.put(R.id.permission_no_upgrade_button, NO_UPGRADE_BUTTON);
        mButtonResIdToNum.put(
                R.id.permission_no_upgrade_and_dont_ask_again_button,
                NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON
        );
        mButtonResIdToNum.put(
                R.id.permission_no_upgrade_one_time_button,
                NO_UPGRADE_OT_BUTTON
        );
        mButtonResIdToNum.put(
                R.id.permission_no_upgrade_one_time_and_dont_ask_again_button,
                NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON
        );
        mButtonResIdToNum.put(R.id.permission_allow_all_button, ALLOW_ALL_BUTTON);
        mButtonResIdToNum.put(R.id.permission_allow_selected_button, ALLOW_SELECTED_BUTTON);
        mButtonResIdToNum.put(
                R.id.permission_dont_allow_more_selected_button,
                DONT_ALLOW_MORE_SELECTED_BUTTON
        );

        mLocationResIdToNum.put(R.id.permission_location_accuracy, LOCATION_ACCURACY_LAYOUT);
        mLocationResIdToNum.put(
                R.id.permission_location_accuracy_radio_fine,
                FINE_RADIO_BUTTON
        );
        mLocationResIdToNum.put(
                R.id.permission_location_accuracy_radio_coarse,
                COARSE_RADIO_BUTTON
        );
        mLocationResIdToNum.put(
                R.id.permission_location_accuracy_radio_group,
                DIALOG_WITH_BOTH_LOCATIONS
        );
        mLocationResIdToNum.put(
                R.id.permission_location_accuracy_fine_only,
                DIALOG_WITH_FINE_LOCATION_ONLY
        );
        mLocationResIdToNum.put(
                R.id.permission_location_accuracy_coarse_only,
                DIALOG_WITH_COARSE_LOCATION_ONLY
        );
    }
}
