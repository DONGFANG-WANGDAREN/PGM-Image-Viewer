package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;


import com.DONGFANG_WANGDAREN.Station_RX.R;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class ActivityAboutApp extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_app);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_about_app);
        toolbar.setNavigationOnClickListener(view -> finish());

        TextView appNameValue = findViewById(R.id.text_view_about_app_name_value);
        TextView versionCodeValue = findViewById(R.id.text_view_about_version_code_value);
        TextView versionNameValue = findViewById(R.id.text_view_about_version_name_value);
        MaterialButton openReadmeButton = findViewById(R.id.button_about_open_readme);

        appNameValue.setText(getString(R.string.app_name));
        bindVersionInfo(versionCodeValue, versionNameValue);
        openReadmeButton.setOnClickListener(view ->
                startActivity(ActivityPictureViewer.createOpenBundledReadmeIntent(this)));
    }

    private void bindVersionInfo(@NonNull TextView versionCodeValue, @NonNull TextView versionNameValue) {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionCodeValue.setText(String.valueOf(packageInfo.getLongVersionCode()));
            versionNameValue.setText(packageInfo.versionName == null ? "" : packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException exception) {
            versionCodeValue.setText("");
            versionNameValue.setText("");
        }
    }
}
