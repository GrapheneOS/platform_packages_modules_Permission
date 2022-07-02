package com.android.permissioncontroller.sscopes;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.fragment.NavHostFragment;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.SettingsActivity;

public class StorageScopesActivity extends SettingsActivity {
    private static final String TAG = "StorageScopesActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nav_host_fragment);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavInflater inflater = navHost.getNavController().getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph);
        graph.setStartDestination(R.id.storage_scopes);

        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        navHost.getNavController().setGraph(graph, StorageScopesFragment.createArgs(packageName));
    }
}
