package com.DONGFANG_WANGDAREN.PGM_Image_Viewer;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class ActivityPictureViewer extends AppCompatActivity {

    private ActivityResultLauncher<String[]> launcherOpenDocument;
    private ViewPictureZoom viewPictureZoom;
    private LinearLayout layoutEmptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_viewer);

        launcherOpenDocument = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleSelectedFile
        );

        MaterialButton buttonOpenPgm = findViewById(R.id.button_open_pgm);
        viewPictureZoom = findViewById(R.id.view_picture_zoom);
        layoutEmptyState = findViewById(R.id.layout_empty_state);

        buttonOpenPgm.setOnClickListener(view -> launcherOpenDocument.launch(new String[]{"*/*"}));
    }

    private void handleSelectedFile(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        String fileName = getDisplayName(uri);
        if (!isPgmFile(fileName)) {
            showToast(R.string.toast_only_pgm);
            return;
        }

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Input stream is null.");
            }

            ParserPicturePgm.DataPicturePgm dataPicturePgm = ParserPicturePgm.parse(inputStream);
            Bitmap bitmap = Bitmap.createBitmap(
                    dataPicturePgm.getWidth(),
                    dataPicturePgm.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmap.setPixels(
                    dataPicturePgm.getArgbPixels(),
                    0,
                    dataPicturePgm.getWidth(),
                    0,
                    0,
                    dataPicturePgm.getWidth(),
                    dataPicturePgm.getHeight()
            );

            viewPictureZoom.setBitmap(bitmap);
            layoutEmptyState.setVisibility(View.GONE);
        } catch (IOException | IllegalArgumentException exception) {
            showToast(R.string.toast_open_failed);
        }
    }

    private boolean isPgmFile(@Nullable String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.toLowerCase(Locale.US).endsWith(".pgm");
    }

    @Nullable
    private String getDisplayName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        return cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return uri.getLastPathSegment();
    }

    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }
}
