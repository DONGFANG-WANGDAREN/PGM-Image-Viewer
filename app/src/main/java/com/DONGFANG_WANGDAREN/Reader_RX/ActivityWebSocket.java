package com.DONGFANG_WANGDAREN.Reader_RX;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActivityWebSocket extends AppCompatActivity {

    private static final String TAG = "ActivityWebSocket";

    private TextView textViewAddress;
    private TextView textViewHttpAddress;
    private TextView textViewStatus;
    private MaterialButton buttonToggle;
    private EditText editTextMessage;
    private MaterialButton buttonSend;
    private MaterialButton buttonImage;
    private TextView textViewLog;
    private ScrollView scrollViewLog;

    @Nullable
    private WebSocketService webSocketService;
    private boolean serviceBound;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            result -> {
                if (result != null) {
                    sendSelectedImage(result);
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder service) {
            WebSocketService.LocalBinder binder = (WebSocketService.LocalBinder) service;
            webSocketService = binder.getService();
            serviceBound = true;
            webSocketService.addEventListener(serviceEventListener);
            if (webSocketService.isRunning()) {
                webSocketService.rotateChatFile();
            }
            updateUi(webSocketService.isRunning(), webSocketService.getConnectedClientCount());
            AppLogger.i(TAG, "Service connected.");
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            webSocketService = null;
            serviceBound = false;
            updateUi(false, 0);
            AppLogger.i(TAG, "Service disconnected.");
        }
    };

    private final WebSocketService.EventListener serviceEventListener = new WebSocketService.EventListener() {
        @Override
        public void onServerStarted(@NonNull String address) {
            updateUi(true, webSocketService != null ? webSocketService.getConnectedClientCount() : 0);
        }

        @Override
        public void onServerStopped() {
            updateUi(false, 0);
        }

        @Override
        public void onClientConnected(int connectedClientCount) {
            updateClientCount(connectedClientCount);
        }

        @Override
        public void onClientDisconnected(int connectedClientCount) {
            updateClientCount(connectedClientCount);
        }

        @Override
        public void onLogEntry(@NonNull String logEntry) {
            log(logEntry);
        }

        @Override
        public void onImageMessage(@NonNull String sender, @NonNull String imageUrl) {
            appendImageMessage(sender, imageUrl);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_socket);
        AppLogger.i(TAG, "onCreate.");

        textViewAddress = findViewById(R.id.text_view_websocket_address);
        textViewHttpAddress = findViewById(R.id.text_view_websocket_http_address);
        textViewStatus = findViewById(R.id.text_view_websocket_status);
        buttonToggle = findViewById(R.id.button_websocket_toggle);
        textViewHttpAddress.setOnClickListener(view -> openHttpAddressInBrowser());
        editTextMessage = findViewById(R.id.edit_text_websocket_message);
        buttonSend = findViewById(R.id.button_websocket_send);
        buttonImage = findViewById(R.id.button_websocket_image);
        textViewLog = findViewById(R.id.text_view_websocket_log);
        scrollViewLog = findViewById(R.id.scroll_view_websocket_log);

        buttonToggle.setOnClickListener(view -> toggleServer());
        buttonSend.setOnClickListener(view -> sendCustomMessage());
        buttonImage.setOnClickListener(view -> pickImage());

        startAndBindService();
        updateUi(false, 0);
    }

    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void toggleServer() {
        if (webSocketService == null) {
            showToast(R.string.websocket_error_not_connected);
            return;
        }
        if (webSocketService.isRunning()) {
            webSocketService.stopServer();
        } else {
            webSocketService.startServer();
        }
    }

    private void sendCustomMessage() {
        if (webSocketService == null || !webSocketService.isRunning()) {
            showToast(R.string.websocket_error_not_connected);
            return;
        }
        String message = editTextMessage.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }
        webSocketService.sendCustomMessage(message);
        editTextMessage.setText("");
    }

    private void updateUi(boolean running, int clientCount) {
        runOnUiThread(() -> {
            String address = webSocketService != null ? webSocketService.getServerAddress() : null;
            String httpAddress = webSocketService != null ? webSocketService.getHttpAddress() : null;
            textViewAddress.setText(address != null ? address : "Unknown");
            textViewHttpAddress.setText(httpAddress != null ? httpAddress : "Unknown");
            buttonToggle.setText(running ? R.string.websocket_stop : R.string.websocket_start);
            textViewStatus.setText(running ? R.string.websocket_status_running : R.string.websocket_status_stopped);
            updateClientCount(clientCount);
            buttonSend.setEnabled(running);
        });
    }

    private void updateClientCount(int count) {
        runOnUiThread(() -> {
            String status = getString(R.string.websocket_status_running)
                    + " — " + getString(R.string.websocket_clients, count);
            if (webSocketService != null && webSocketService.isRunning()) {
                textViewStatus.setText(status);
            }
        });
    }

    private void log(@NonNull String line) {
        runOnUiThread(() -> {
            appendToLog(line);
            scrollToBottomWithoutFocus();
        });
    }

    private void appendToLog(@NonNull CharSequence text) {
        if (textViewLog.getText().length() > 0) {
            textViewLog.append("\n");
        }
        textViewLog.append(text);
        textViewLog.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void appendImageMessage(@NonNull String sender, @NonNull String imageUrl) {
        runOnUiThread(() -> {
            String label = getString(R.string.websocket_image_label);
            String displayText = "[" + sender + "] " + label;
            SpannableString spannable = new SpannableString(displayText);
            ClickableSpan span = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    showImageViewer(imageUrl);
                }
            };
            int start = displayText.indexOf(label);
            if (start >= 0) {
                spannable.setSpan(span, start, start + label.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            appendToLog(spannable);
            scrollToBottomWithoutFocus();
        });
    }

    private void scrollToBottomWithoutFocus() {
        scrollViewLog.post(() -> {
            int contentHeight = scrollViewLog.getChildAt(0).getHeight();
            int scrollViewHeight = scrollViewLog.getHeight();
            int scrollTo = Math.max(0, contentHeight - scrollViewHeight);
            scrollViewLog.scrollTo(0, scrollTo);
        });
    }

    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }

    private void openHttpAddressInBrowser() {
        String httpAddress = webSocketService != null ? webSocketService.getHttpAddress() : null;
        if (httpAddress == null || httpAddress.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(httpAddress));
        startActivity(intent);
    }

    private void pickImage() {
        imagePickerLauncher.launch("image/*");
    }

    private void sendSelectedImage(@NonNull Uri imageUri) {
        if (webSocketService == null || !webSocketService.isRunning()) {
            showToast(R.string.websocket_error_not_connected);
            return;
        }
        File imageFile = copyUriToChatImages(imageUri);
        if (imageFile == null) {
            showToast(R.string.websocket_image_copy_failed);
            return;
        }
        String httpAddress = webSocketService.getHttpAddress();
        if (httpAddress == null || httpAddress.isEmpty()) {
            showToast(R.string.websocket_image_url_failed);
            return;
        }
        String imageUrl = httpAddress + AppConfig.get().getImageUrlPrefix() + imageFile.getName();
        webSocketService.sendImageMessage(imageUrl);
    }

    @Nullable
    private File copyUriToChatImages(@NonNull Uri sourceUri) {
        File imagesDirectory = AppStoragePaths.resolveWebSocketChatImagesDirectory(this);
        AppConfig config = AppConfig.get();
        String extension = resolveImageExtension(sourceUri);
        String baseName = new SimpleDateFormat(config.getChatImageFileNameFormat(), Locale.US).format(new Date());
        File destinationFile = new File(imagesDirectory, baseName + extension);
        int suffix = 1;
        while (destinationFile.exists()) {
            destinationFile = new File(imagesDirectory, baseName + "_" + suffix + extension);
            suffix++;
        }
        try (InputStream inputStream = getContentResolver().openInputStream(sourceUri);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {
            if (inputStream == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            return destinationFile;
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to copy selected image.", exception);
            return null;
        }
    }

    @NonNull
    private String resolveImageExtension(@NonNull Uri imageUri) {
        String lastSegment = imageUri.getLastPathSegment();
        if (lastSegment != null) {
            int dotIndex = lastSegment.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < lastSegment.length() - 1) {
                String extension = lastSegment.substring(dotIndex + 1).toLowerCase(Locale.US);
                if (extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg")
                        || extension.equals("gif") || extension.equals("webp") || extension.equals("bmp")) {
                    return "." + extension;
                }
            }
        }
        return ".jpg";
    }

    private void showImageViewer(@NonNull String imageUrl) {
        String fileName = extractFileNameFromUrl(imageUrl);
        if (fileName == null) {
            showToast(R.string.websocket_image_open_failed);
            return;
        }
        File imageFile = new File(AppStoragePaths.resolveWebSocketChatImagesDirectory(this), fileName);
        if (!imageFile.exists()) {
            showToast(R.string.websocket_image_open_failed);
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        if (bitmap == null) {
            showToast(R.string.websocket_image_open_failed);
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_viewer);
        ImageView imageView = dialog.findViewById(R.id.image_view_dialog);
        if (imageView != null) {
            imageView.setImageBitmap(bitmap);
            imageView.setOnClickListener(view -> dialog.dismiss());
        }
        dialog.show();
    }

    @Nullable
    private static String extractFileNameFromUrl(@NonNull String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash >= url.length() - 1) {
            return null;
        }
        String candidate = url.substring(lastSlash + 1);
        int queryIndex = candidate.indexOf('?');
        if (queryIndex >= 0) {
            candidate = candidate.substring(0, queryIndex);
        }
        return candidate.isEmpty() ? null : candidate;
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            if (webSocketService != null) {
                webSocketService.removeEventListener(serviceEventListener);
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
        AppLogger.i(TAG, "onDestroy.");
        super.onDestroy();
    }
}
