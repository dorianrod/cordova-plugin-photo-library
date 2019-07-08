package com.terikon.cordova.photolibrary;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class BytesCache {
    String TAG = "BytesCache";

    byte[] fullyReadFileToBytes(File f) throws IOException {
        int size = (int) f.length();
        byte bytes[] = new byte[size];
        byte tmpBuff[] = new byte[size];
        FileInputStream fis= new FileInputStream(f);;
        try {

            int read = fis.read(bytes, 0, size);
            if (read < size) {
                int remain = size - read;
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain);
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                    remain -= read;
                }
            }
        }  catch (IOException e){
            throw e;
        } finally {
            fis.close();
        }

        return bytes;
    }

    public String MD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes("UTF-8"));
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        } catch(UnsupportedEncodingException ex){
        }
        return null;
    }

    private String getFilePathByKey(Context context, String key) {
        String filename = this.MD5(key);
        if(filename == null || key == null) return null;
        return context.getCacheDir().getAbsolutePath() + "/" + filename;
    }

    private File getFileByKey(Context context, String key) {
        String filePath = getFilePathByKey(context, key);
        if(filePath == null) return null;
        File file = new File(filePath);
        return file.exists() ? file : null;
    }

    public boolean keyExists(Context context, String key) {
        return getFileByKey(context, key) != null;
    }

    public byte[] get(Context context, String key) {
        File file = getFileByKey(context, key);
        if(file != null) {
            try {
                byte[] bytes = fullyReadFileToBytes(file);
                return bytes;
            } catch (IOException e) {
                return null;
            }
        }

        return null;
    }

    public boolean put(Context context, String key, byte[] bytes) {
        String filepath = getFilePathByKey(context, key);
        if(filepath == null) return false;

        try {
            File file = new File(filepath);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();

            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return false;
    }
}