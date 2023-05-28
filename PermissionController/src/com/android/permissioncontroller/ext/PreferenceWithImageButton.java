package com.android.permissioncontroller.ext;

import android.content.Context;
import android.view.View;
import android.widget.ImageButton;

import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.settingslib.widget.TwoTargetPreference;

public class PreferenceWithImageButton extends TwoTargetPreference implements View.OnClickListener {

    public PreferenceWithImageButton(Context ctx) {
        super(ctx);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageButton btn = (ImageButton) holder.findViewById(R.id.preference_widget_image_button);
        btn.setImageResource(imageButtonResource);
        btn.setContentDescription(imageButtonDescription);
        btn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        imageButtonListener.run();
    }

    private int imageButtonResource;
    private CharSequence imageButtonDescription;
    private Runnable imageButtonListener;

    public void setupButton(int resource, CharSequence description, Runnable onClickListener) {
        imageButtonResource = resource;
        imageButtonDescription = description;
        imageButtonListener = onClickListener;
    }

    @Override
    protected int getSecondTargetResId() {
        return R.layout.preference_widget_image_button;
    }
}
