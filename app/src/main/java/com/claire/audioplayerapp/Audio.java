package com.claire.audioplayerapp;

import java.io.Serializable;
//Serializable是序列化的意思，表示將一個對象轉換成可存儲或可傳輸的狀態。序列化後的對象可以在網絡上進行傳輸，也可以存儲到本地。
/**
 * 附加功能 - 加載本地音頻文件
 * 用戶可能希望從實際設備加載音樂而不是在線式傳輸，可以使用從設備加載音頻文件 ContentResolver
 * 將權限加到AndroidManifest.xml <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
 * 以下包含音頻文件所需的關鍵信息
 */
public class Audio implements Serializable {

    private String data;
    private String title;
    private String album;
    private String artist;

    public Audio(String data, String title, String album, String artist) {
        this.data = data;
        this.title = title;
        this.album = album;
        this.artist = artist;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }
}
