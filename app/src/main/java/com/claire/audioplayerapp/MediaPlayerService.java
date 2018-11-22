package com.claire.audioplayerapp;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;

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

    // Binder given to clients
    private final IBinder iBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
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


    /**
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

    }

    /**
     *  onError(MediaPlayer mp, int what, int extra)
     *  在異步操作期料發生錯誤時調用. (Invoked when there has been an error during an asynchronous operation.)
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
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
    public void onAudioFocusChange(int focusChange) {

    }

    public class LocalBinder extends Binder {

        public MediaPlayerService getService(){
            return MediaPlayerService.this;
        }
    }

}
