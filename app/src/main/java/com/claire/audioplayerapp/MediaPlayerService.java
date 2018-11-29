package com.claire.audioplayerapp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

// Service需要在AndroidManifest.xml文件中聲明 <service android:name=".MediaPlayerService"/>
public class MediaPlayerService extends Service implements
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener,
        //處理AudioFocus來自其他想要播放媒體文件的應用程序請求
        AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "com.claire.audioplayerapp.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.claire.audioplayerapp.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.claire.audioplayerapp.ACTION_PREVIOUS"; //以前
    public static final String ACTION_NEXT = "com.claire.audioplayerapp.ACTION_NEXT";
    public static final String ACTION_STOP = "com.claire.audioplayerapp.ACTION_STOP";

    /**
     *  MediaSession 控制媒體播放，允許與媒體控制器，音量鍵，媒體按鈕和傳輸控件進行交互
     */
    private MediaSessionManager mediaSessionManager;
    private MediaSession mediaSession;
    private MediaController.TransportControls transportControls;

    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;


    private MediaPlayer mediaPlayer;
    //path to the audio file
    private String mediaFile;

    //Used to pause/resume MediaPlayer 儲存暫停/重啟的位置
    private int resumePosition;

    //AudioFocus
    private AudioManager audioManager;

    // Binder given to clients
    private final IBinder iBinder = new LocalBinder();

    //處理來電 Handle incoming phone calls
    private boolean ongoingCall = false; //正在進行通話  ongoing (前進、不斷的)
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    //List of available Audio files 可用音頻文件列表
    private ArrayList<Audio> audioList;
    private int audioIndex = -1;
    private Audio activeAudio; //an object of the currently playing audio 當前播放音頻的對象

    /**
     * Service lifecycle methods 生命週期
     */
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    /**
     * 註冊BroadcastReceiver
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures 執行一次性設置程序

        //Manage incoming phone calls during playback. 在播放期間管理來電
        //Pause MediaPlayer on incoming call, 在來電時暫停MediaPlayer
        //Resume on hangup.掛機時恢復
        callStateListener();

        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();
    }



    /**
     * The system calls method when an activity, request the service be started
     * 系統在活動時調用此方法，請求啟動服務
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            //An audio file is passed to the service through putExtra();
            //音頻文件通過putExtra()傳遞給服務
            mediaFile = intent.getExtras().getString("media");
        }catch (NullPointerException e){

            stopSelf();
        }

        //Request audio focus 請求音頻焦點
        if (requestAudioFocus() == false){
            //Could nto gain focus //無法獲很焦點
            stopSelf();
        }

        if (mediaFile != null && !mediaFile.equals(""))
            initMediaPlayer();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null){
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();

        //Disable the PhoneStateListener
        if (phoneStateListener != null){
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //unregister BroadcastReceivers 取消註冊
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

        //clear cached playlist 清除緩存的播放列表
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    /**
     * MediaPlayer actions
     */
    private void initMediaPlayer(){
        if (mediaPlayer == null){
            mediaPlayer = new MediaPlayer(); //new MediaPlayer instance
        }
        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        //Reset to that the MediaPlayer is not pointing to another data source
        //重置，以便MediaPlayer不指向其他數據源
        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            // set the data source the mediaFile location
            mediaPlayer.setDataSource(mediaFile);
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }

        mediaPlayer.prepareAsync();

    }

    private void playMedia(){
        if (!mediaPlayer.isPlaying()){
            mediaPlayer.start();
        }
    }

    private void stopMedia(){
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()){
            mediaPlayer.stop();
        }
    }

    private void pauseMedia(){
        if (mediaPlayer.isPlaying()){
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMedia(){
        if (!mediaPlayer.isPlaying()){
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }


    /**
     * MediaPlayer callback methods
     * onBufferingUpdate(MediaPlayer mp, int percent)被調用以更新緩衝通過漸進式HTTP下載接收的媒體流的狀態。
     * 接收的緩衝百分比表示已緩衝或播放了多少內容。例如，當已經播放了一半內容時，緩衝更新為80％表示要播放的下一個30％的內容已被緩衝。
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    /**
     * onCompletion(MediaPlayer mp)在播放期間到達媒體源結尾時調用。
     * Invoked when playback of a media source has completed.
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        //Invoked when playback of a media source has completed
        stopMedia();

        //stop the service
        stopSelf();
    }

    /**
     *  onError(MediaPlayer mp, int what, int extra)
     *  在異步操作期料發生錯誤時調用. (Invoked when there has been an error during an asynchronous operation.)
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        //Invoke when there has been an error during an asynchronous operation
        switch (what){
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK" + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED" + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN" + extra);
                break;

        }
        return false;
    }

    /**
     * onInfo(MediaPlayer mp, int what, int extra)
     * 調用來傳達一些信息。 (Invoked to communicate some info.)
     */
    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    /**
     * onPrepared(MediaPlayer mp)
     * 媒體源準備好，播放時調用。 (Invoked when the media source is ready for playback.)
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        playMedia();
    }

    /**
     * onPrepared(MediaPlayer mp)
     * 調用表示搜索操作已完成 (Invoked indicating the completion of a seek operation.)
     */
    @Override
    public void onSeekComplete(MediaPlayer mp) {

    }

    /**
     * 在更新系統的音頻焦點時調用 (Invoked when the audio focus of the system is updated.)
     */
    @Override
    public void onAudioFocusChange(int focusState) {
        //Invoked when the audio focus of the system is updated.
        switch (focusState){
            case AudioManager.AUDIOFOCUS_GAIN: //該服務獲取了音頻焦點，因此需要開始播放
                //resume playback 恢復播放
                if (mediaPlayer == null){
                    initMediaPlayer();
                }
                else if (!mediaPlayer.isPlaying()){
                    mediaPlayer.start();;
                    mediaPlayer.setVolume(1.0f, 1.0f);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS: //該服務丟失了音頻焦點，用戶可能轉移到另一個應用程序上播放媒體，因此釋放媒體播放器
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: //Focus失去了一小段時間，暫停了MediaPlayer。
                // Lost focus a short time, but we have to stop playback
                // We don't release the media player because playback is likely to resume
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: //短時間丟失焦點，可能是設備上的通知，降低了播放音量
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f,0.1f);
                break;

        }

    }

    /**
     * AudioFocus 請求焦點
     */
    private boolean requestAudioFocus(){
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            //Focus gained
            return true;
        }
        //Could not gain focus
        return false;
    }
    //釋放音頻焦點
    private boolean removeAudioFocus(){
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    /**
     * 更改音頻輸出(耳機已移除)，當用戶從插孔中取出耳機時，媒體停止播戶放
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
            buildNotification(PlaybackStatusEnum.PAUSED);
        }
    };

    //要使其BroadcastReceiver可用，必須註冊它
    private void registerBecomingNoisyReceiver(){
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * 處理來電 Handle incoming phone calls
     * callStateListener()是PhoneStateListener偵聽TelephonyManager狀態更改的實現。
     * TelephonyManager提供對設備上電話服務的信息訪問，並監聽設備呼叫狀態的變化並對這些變化作出反應。
     */
    private void callStateListener(){
        // Get the telephony manager 獲取電話管理員
        telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener(){
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state){
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaFile != null){
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if(mediaPlayer != null){
                            if (ongoingCall){
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * Play new Audio 播放新的音頻廣播
     */
    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Get the new media index form SharedPreferences
            audioIndex = new StorageUtil(getApplicationContext()).loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()){
                //index is in a valid range (index處於有效範圍內)
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio (重置mediaPlayer以播放新音頻)
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackStatusEnum.PAUSED);
        }
    };

    private void register_playNewAudio(){
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }

    /**
     * MediaSession and Notification actions
     * 設置MediaSession回調以處理來自通知按鈕的事件
     */
    private void initMediaSession() throws RemoteException{
        if (mediaSessionManager != null) return; //mediaSessionManager exists 存在

        mediaSessionManager = (MediaSessionManager)getSystemService(Context.MEDIA_SESSION_SERVICE);
        //Create a new MediaSession
        mediaSession = new MediaSession(getApplicationContext(),"AudioPlayer");
        //Get MediaSessions transport controls (獲取MediaSessions傳輸控件)
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands (準備接收命令)
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands (表示MediaSession處理傳輸控制命令)
        // through its MediaSession.Callback.
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //Set mediaSession's MetaData
        updateMetaData();

        //Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSession.Callback() {

            // Implement callbacks

            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlaybackStatusEnum.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatusEnum.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlaybackStatusEnum.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();

                skipToPrevious();
                updateMetaData();
                buildNotification(PlaybackStatusEnum.PLAYING);
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
            }
        });

    }

    private void skipToNext(){
        if (audioIndex == audioList.size() - 1){
            //if last in playlist
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        } else {
            //get next in playlist
            activeAudio = audioList.get(++audioIndex);
        }

        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPrevious(){
        if (audioIndex == 0){
            //if first in playlist
            //set index to the last of audioList
            audioIndex = audioList.size() - 1;
            activeAudio = audioList.get(audioIndex);
        } else {
            //get previous in playlist 獲取上一個播放列表
            activeAudio = audioList.get(--audioIndex);
        }

        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    /**
     * 跟踪其播放狀態
     * buildNotification()功能主要目的是構建通知UI並設罝當用戶單擊通知按鈕時將觸發的所有事件。
     * 可以通過函數生成PendingIntents playbackAction()。
     * Notification actions -> playbackAction()
     *  0 -> Play
     *  1 -> Pause
     *  2 -> Next track
     *  3 -> Previous track
     */
    private void buildNotification(PlaybackStatusEnum playbackStatusEnum) {
        int notificationAction = android.R.drawable.ic_media_pause; //needs to be initialized
        PendingIntent play_pauseAction = null;

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatusEnum == PlaybackStatusEnum.PLAYING){
            notificationAction = android.R.drawable.ic_media_pause;
            //create the pause action
            play_pauseAction = playbackAction(1);
        } else if (playbackStatusEnum == PlaybackStatusEnum.PAUSED){
            notificationAction = android.R.drawable.ic_media_play;
            //create the play action
            play_pauseAction = playbackAction(0);
        }

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),R.drawable.image1); //replace with your own image

        //Create a new Notification
        Notification.Builder notificationBuilder =
                (Notification.Builder) new Notification.Builder(this)
                .setShowWhen(false)
                // Set the Notification style
                .setStyle(new Notification.MediaStyle()
                    //Attach our MediaSession token
                    .setMediaSession(mediaSession.getSessionToken())
                    // Show our playback controls in the notification view)
                    .setShowActionsInCompactView(0,1,2))
                // Set the Notification color
                .setColor(getResources().getColor(R.color.colorAccent))
                // Set the large and small icons
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                // Set Notification content information
                .setContentText(activeAudio.getArtist())
                .setContentTitle(activeAudio.getAlbum())
                .setContentInfo(activeAudio.getTitle())
                // Add playback actions
                .addAction(android.R.drawable.ic_media_previous,"previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, notificationBuilder.build());


    }

    private void removeNotification(){
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber){
            case 0:
                //Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction,0);
            case 1:
                //Pause
                return PendingIntent.getService(this,actionNumber,playbackAction,0);
            case 2:
                //Next track 下一首曲目
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                //Next track 下一首曲目
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }

        return null;
    }

    /**
     * 當用戶單擊通知按鈕時，服務會生成操作，因此需要方法來處理傳入操作
     */
    private void handleIncomingActions(Intent playbackAction){

        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)){
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)){
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)){
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)){
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)){
            transportControls.stop();
        }
    }

    private void updateMetaData() {
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.image1); //replace with medias albumArt
        // Update the current metadata
        mediaSession.setMetadata(new MediaMetadata.Builder()
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, activeAudio.getArtist())
            .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio.getAlbum())
            .putString(MediaMetadata.METADATA_KEY_TITLE, activeAudio.getTitle())
            .build());
    }

    /**
     * Service Binder
     */
    public class LocalBinder extends Binder {

        public MediaPlayerService getService(){
            return MediaPlayerService.this;
        }
    }

}
