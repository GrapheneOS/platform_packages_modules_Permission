/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.television;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.R;

public abstract class PermissionsFrameFragment extends PreferenceFragmentCompat {

    // Key identifying the preference used on TV as the extra header in a permission fragment.
    // This is to distinguish it from the rest of the preferences
    protected static final String HEADER_PREFERENCE_KEY = "HeaderPreferenceKey";

    private ViewGroup mPreferencesContainer;

    // TV-specific instance variable
    @Nullable private RecyclerView mGridView;

    private View mLoadingView;
    private ViewGroup mPrefsView;
    private boolean mIsLoading;
    private TextView mEmptyView;

    /**
     * Returns the view group that holds the preferences objects. This will
     * only be set after {@link #onCreateView} has been called.
     */
    protected final ViewGroup getPreferencesContainer() {
        return mPreferencesContainer;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.permissions_frame, container,
                        false);
        mPrefsView = (ViewGroup) rootView.findViewById(R.id.prefs_container);
        if (mPrefsView == null) {
            mPrefsView = rootView;
        }
        mLoadingView = rootView.findViewById(R.id.loading_container);
        mEmptyView = (TextView) rootView.findViewById(R.id.no_permissions);
        mPreferencesContainer = (ViewGroup) super.onCreateView(
                inflater, mPrefsView, savedInstanceState);
        setLoading(mIsLoading, false, true /* force */);
        mPrefsView.addView(mPreferencesContainer);
        return rootView;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen preferences = getPreferenceScreen();
        if (preferences == null) {
            preferences = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(preferences);
        }
    }

    protected void setLoading(boolean loading, boolean animate) {
        setLoading(loading, animate, false);
    }

    private void setLoading(boolean loading, boolean animate, boolean force) {
        if (mIsLoading != loading || force) {
            mIsLoading = loading;
            if (getView() == null) {
                // If there is no created view, there is no reason to animate.
                animate = false;
            }
            if (mPrefsView != null) {
                setViewShown(mPrefsView, !loading, animate);
            }
            if (mLoadingView != null) {
                setViewShown(mLoadingView, loading, animate);
            }
            if (!mIsLoading) {
                checkEmpty();
            }
        }
    }

    private void setViewShown(final View view, boolean shown, boolean animate) {
        if (animate) {
            Animation animation = AnimationUtils.loadAnimation(getContext(),
                    shown ? android.R.anim.fade_in : android.R.anim.fade_out);
            if (shown) {
                view.setVisibility(View.VISIBLE);
            } else {
                animation.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        view.setVisibility(View.INVISIBLE);
                    }
                });
            }
            view.startAnimation(animation);
        } else {
            view.clearAnimation();
            view.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                                             Bundle savedInstanceState) {
        VerticalGridView verticalGridView = (VerticalGridView) inflater.inflate(
                androidx.leanback.preference.R.layout.leanback_preferences_list, parent, false);
        verticalGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
        verticalGridView.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED);
        mGridView = verticalGridView;
        return mGridView;
    }

    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(PreferenceScreen preferenceScreen) {
        final RecyclerView.Adapter<?> adapter = super.onCreateAdapter(preferenceScreen);

        if (adapter != null) {
            onSetEmptyText(mEmptyView);
            adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    checkEmpty();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    checkEmpty();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    checkEmpty();
                }
            });

            checkEmpty();
        }

        return adapter;
    }

    private void checkEmpty() {
        if (mIsLoading || getListView() == null || getListView().getAdapter() == null) return;

        boolean isEmpty = isPreferenceListEmpty();
        mEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        getListView().setVisibility(isEmpty && getListView().getAdapter().getItemCount() == 0
                ? View.GONE : View.VISIBLE);
        if (!isEmpty && mGridView != null) {
            mGridView.requestFocus();
        }
    }

    private boolean isPreferenceListEmpty() {
        PreferenceScreen screen = getPreferenceScreen();
        return screen.getPreferenceCount() == 0 || (
                screen.getPreferenceCount() == 1 &&
                        (screen.findPreference(HEADER_PREFERENCE_KEY) != null));
    }

    /**
     * Hook for subclasses to change the default text of the empty view.
     * Base implementation leaves the default empty view text.
     *
     * @param textView the empty text view
     */
    protected void onSetEmptyText(TextView textView) {
    }
}
