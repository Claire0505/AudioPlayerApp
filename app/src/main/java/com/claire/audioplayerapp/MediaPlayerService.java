package com.claire.audioplayerapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;

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

    /**
     * Service lifecycle methods 生命週期
     */
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
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
            //buildNotification(PlaybackState.PAUSE);
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
     * Service Binder
     */
    public class LocalBinder extends Binder {

        public MediaPlayerService getService(){
            return MediaPlayerService.this;
        }
    }

}
