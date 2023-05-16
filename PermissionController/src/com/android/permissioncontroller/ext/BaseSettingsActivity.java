package com.android.permissioncontroller.ext;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.fragment.NavHostFragment;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.SettingsActivity;

public abstract class BaseSettingsActivity extends SettingsActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nav_host_fragment);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavInflater inflater = navHost.getNavController().getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph);
        graph.setStartDestination(getNavGraphStart());

        navHost.getNavController().setGraph(graph, getFragmentArgs());
    }

    public abstract int getNavGraphStart();

    @Nullable
    public Bundle getFragmentArgs() {
        return null;
    }
}

