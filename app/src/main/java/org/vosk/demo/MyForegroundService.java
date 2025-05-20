package org.vosk.demo;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class MyForegroundService extends Service {
    private static final int NOTIFICATION_ID = 123;
    private static final String CHANNEL_ID = "AssistantServiceChannel";
    private TextToSpeech textToSpeech;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported");
                }
            } else {
                Log.e("TTS", "Initialization failed");
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        if (intent != null && intent.hasExtra("volume")) {
            String volumeCommand = intent.getStringExtra("volume");
            changeVolumeInBackground(volumeCommand);
        }

        if (intent != null && intent.hasExtra("command")) {
            try {
                String commandJson = intent.getStringExtra("command");
                JSONObject commandMessage = new JSONObject(commandJson);
                JSONArray actions = commandMessage.getJSONArray("actions");

                for (int i = 0; i < actions.length(); i++) {
                    String action = actions.getString(i);
                    String[] parts = action.split("\\|", 2);

                    if (parts.length == 2) {
                        String actionType = parts[0].trim().toLowerCase();
                        String actionContent = parts[1].trim();

                        switch (actionType) {
                            case "голосовой ответ":
                                speakInBackground(actionContent);
                                break;
                            case "открытие ссылки":
                                openLink(actionContent);
                                break;
                            case "открытие приложения":
                                openAppInBackground(actionContent);
                                break;
                            case "изменение громкости":
                                changeVolumeInBackground(actionContent);
                                break;
                            case "изменение яркости":
                                changeBrightnessInBackground(actionContent);
                                break;
                            case "завершение процесса":
                                closeAppByPackageName(actionContent);
                                break;
                            case "музыка":
                                if (actionContent.equals("следующий трек")) {
                                    controlMediaPlayback("next");
                                } else if (actionContent.equals("предыдущий трек")) {
                                    controlMediaPlayback("previous");
                                } else if (actionContent.equals("выключить музыку")) {
                                    controlMediaPlayback("pause");
                                } else if (actionContent.equals("включить музыку")) {
                                    controlMediaPlayback("play");
                                }
                                break;
                            case "режим фото":
                                break;
                            default:
                                Log.w("CommandHandler", "Unknown action type: " + actionType);
                                showToast("Ошибка выполнения команды");
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e("CommandHandler", "Error processing command", e);
                showToast("Ошибка выполнения команды");
            }
        }
        return START_STICKY;
    }

    private void controlMediaPlayback(String action) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) return;

            KeyEvent keyEvent;
            switch (action) {
                case "play":
                    keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    break;
                case "pause":
                    keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    break;
                case "next":
                    keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    break;
                case "previous":
                    keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    audioManager.dispatchMediaKeyEvent(keyEvent);
                    break;
            }
        } catch (Exception e) {
            Log.e("MediaControl", "Error controlling media playback", e);
            showToast("Ошибка управления воспроизведением");
        }
    }

    public void closeAppByPackageName(String packageName) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                am.killBackgroundProcesses(packageName);
                showToast("Приложение " + packageName + " было закрыто");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.AppTask> tasks = am.getAppTasks();
                for (ActivityManager.AppTask task : tasks) {
                    ActivityManager.RecentTaskInfo taskInfo = task.getTaskInfo();
                    if (taskInfo.baseIntent != null && taskInfo.baseIntent.getComponent() != null) {
                        if (taskInfo.baseIntent.getComponent().getPackageName().equals(packageName)) {
                            task.finishAndRemoveTask();
                            showToast("Приложение " + packageName + " было закрыто");
                            return;
                        }
                    }
                }
            }

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            am.killBackgroundProcesses(packageName);
            showToast("Приложение " + packageName + " было закрыто");

        } catch (Exception e) {
            Log.e("AppCloser", "Error closing app: " + e.getMessage());
            showToast("Не удалось закрыть приложение " + packageName);
        }
    }

    private void speakInBackground(String text) {
        if (textToSpeech != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null);
            } else {
                textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null);
            }

            showNotificationWithText(text);

            // Обновляем UI через активность
            FridayActivity activity = FridayActivity.getInstance();
            if (activity != null) {
                activity.updateResultText("\nОтвет: " + text);
            }
        }
    }

    private void showNotificationWithText(String text) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w("Notification", "Notification permission not granted");
                return;
            }
        }

        Intent intent = new Intent(this, FridayActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("Ответ ассистента")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            notificationManager.notify(12345, builder.build());
        } catch (SecurityException e) {
            Log.e("Notification", "Failed to show notification", e);
        }
    }

    private void openLink(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = getPackageManager();
            if (intent.resolveActivity(pm) != null) {
                startActivity(intent);
            } else {
                showToast("Не найдено приложение для открытия ссылки");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://www.google.com"));
                if (browserIntent.resolveActivity(pm) != null) {
                    startActivity(browserIntent);
                }
            }
        } catch (ActivityNotFoundException e) {
            Log.e("Service", "No activity to handle link", e);
            showToast("Не удалось открыть ссылку: " + e.getMessage());
        } catch (Exception e) {
            Log.e("Service", "Error opening link", e);
            showToast("Ошибка при открытии ссылки");
        }
    }

    private void openAppInBackground(String packageName) {
        try {
            PackageManager pm = getPackageManager();

            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                return;
            }

            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                intent = pm.getLeanbackLaunchIntentForPackage(packageName);
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }

            Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            settingsIntent.setData(Uri.parse("package:" + packageName));
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);

        } catch (ActivityNotFoundException e) {
            Log.e("Service", "App not found", e);
            showToast("Приложение не найдено: " + packageName);
        } catch (Exception e) {
            Log.e("Service", "Error opening app", e);
            showToast("Не удалось открыть приложение");
        }
    }

    private void changeVolumeInBackground(String volumePercent) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                showToast("Не удалось получить доступ к аудио менеджеру");
                return;
            }

            int percent = Integer.parseInt(volumePercent.replaceAll("[^0-9]", ""));
            percent = Math.max(0, Math.min(100, percent));

            // Устанавливаем громкость только для медиа (STREAM_MUSIC)
            int maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetMusic = (int) (maxMusic * (percent / 100.0));
            audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    targetMusic,
                    AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND
            );

            audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            );

        } catch (NumberFormatException e) {
            Log.e("Service", "Invalid volume percentage", e);
            showToast("Некорректное значение громкости");
        } catch (Exception e) {
            Log.e("Service", "Error changing volume", e);
            showToast("Ошибка изменения громкости: " + e.getMessage());
        }
    }

    private void changeBrightnessInBackground(String brightnessPercent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    showToast("Пожалуйста, предоставьте разрешение на изменение яркости");
                    return;
                }
            }

            int percent = Integer.parseInt(brightnessPercent.replace("%", "").trim());
            percent = Math.max(0, Math.min(100, percent));
            int brightness = (int) (255 * percent / 100.0);

            Settings.System.putInt(
                    getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness
            );

            FridayActivity activity = FridayActivity.getInstance();
            if (activity != null) {
                Window window = activity.getWindow();
                if (window != null) {
                    WindowManager.LayoutParams layoutParams = window.getAttributes();
                    layoutParams.screenBrightness = brightness / 255.0f;
                    window.setAttributes(layoutParams);
                }
            }
        } catch (Exception e) {
            Log.e("Service", "Error changing brightness", e);
            showToast("Ошибка изменения яркости");
        }
    }

    private void showToast(final String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, FridayActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Голосовой ассистент")
                .setContentText("Работает в фоновом режиме")
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Фоновый сервис ассистента",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        stopForeground(true);
        stopSelf();
    }
}