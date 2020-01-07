package com.terikon.cordova.photolibrary;

import android.util.Log;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Base64;

import com.dorian.cordova.cacheurl.BytesCache;
import com.dorian.cordova.cacheurl.CachedData;

import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TimeZone;

class PictureCachedData extends CachedData {
    private String TAG = "PictureCachedData";

    private Integer width;
    private Integer height;
    private double quality;

    private static byte[] getJpegBytesFromBitmap(Bitmap bitmap, double quality) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, (int)(quality * 100), stream);
        return stream.toByteArray();
    }

    public PictureCachedData(byte[] bytes, String extension, int w, int h, double q) {
        super(bytes, extension);
        width = w;
        height = h;
        quality = q;
    }

    public byte[] loadBytes(Context context, String imageURL, String cleanUrl) throws IOException {
        Bitmap bitmap = null;
        //long startTime       = System.currentTimeMillis();

        try {
            //startTime = System.currentTimeMillis();

            bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(imageURL), width, height,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);

            if(bitmap == null) {
                byte[] bytesEmpty = new byte[0];
                return bytesEmpty;
            }

            //Log.d(TAG, "Extract thumbnail: " + (System.currentTimeMillis() - startTime));

            int orientation = PhotoLibraryService.getURLOrientation(imageURL);
            if(orientation > 1) {
                Bitmap rotatedBitmap = PhotoLibraryService.rotateImage(bitmap, orientation);
                if (bitmap != rotatedBitmap) bitmap.recycle();
                bitmap = rotatedBitmap;
            }

            //startTime = System.currentTimeMillis();
            //this.cache.put(context, imageURL, bytes, directory);
            //Log.d(TAG, "Save bytes: " + (System.currentTimeMillis() - startTime));

            bytes = getJpegBytesFromBitmap(bitmap, quality);
            //Log.d(TAG, "Get bytes from bitmap: " + (System.currentTimeMillis() - startTime));

            bitmap.recycle();

            return bytes;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } catch (OutOfMemoryError o) {
            Log.e(TAG, o.getMessage());
        }

        throw new IOException("Unable to load library thumbnail");
    }
}


public class PhotoLibraryService {
    BytesCache cache = null;
    String TAG = "PhotoLibraryService";

    protected PhotoLibraryService(Context context) {
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        cache = BytesCache.getInstance(context);
        cache.clear("photos", 700);
    }

    public static final String PERMISSION_ERROR = "Permission Denial: This application is not allowed to access Photo data.";

    public static PhotoLibraryService getInstance(Context context) {
        if (instance == null) {
            synchronized(PhotoLibraryService.class) {
                if (instance == null) {
                    instance = new PhotoLibraryService(context);
                }
            }
        }
        return instance;
    }

    public void getLibrary(Context context, PhotoLibraryGetLibraryOptions options, ChunkResultRunnable completion) throws JSONException {

        Calendar c = Calendar.getInstance();
        Date date2 = c.getTime();
        c.add(Calendar.MONTH, -1);
        Date date1 = c.getTime();

        String whereClause = ""; //MediaStore.MediaColumns.DATE_ADDED + ">=" + date1.getTime() / 1000;


        if (options.dateStart != null && options.dateEnd != null) {
            whereClause = MediaStore.MediaColumns.DATE_ADDED + ">=" + options.dateStart + " and " + MediaStore.MediaColumns.DATE_ADDED + "<=" + options.dateEnd;
        } else if (options.dateStart == null && options.dateEnd != null) {
            whereClause = MediaStore.MediaColumns.DATE_ADDED + "<=" + options.dateEnd;
        } else if (options.dateStart != null && options.dateEnd == null) {
            whereClause = MediaStore.MediaColumns.DATE_ADDED + ">=" + options.dateStart;
        }

        Log.i("whereClause", whereClause);

        queryLibrary(context, options.itemsInChunk, options.chunkTimeSec, options.includeAlbumData, whereClause, completion);

    }

    public ArrayList < JSONObject > getAlbums(Context context) throws JSONException {

        // All columns here: https://developer.android.com/reference/android/provider/MediaStore.Images.ImageColumns.html,
        // https://developer.android.com/reference/android/provider/MediaStore.MediaColumns.html
        JSONObject columns = new JSONObject() {
            {
                put("id", MediaStore.Images.ImageColumns.BUCKET_ID);
                put("title", MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME);
            }
        };

        final ArrayList < JSONObject > queryResult = queryContentProvider(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, "1) GROUP BY 1,(2");

        return queryResult;

    }

    public PictureData getEmptyPicture() {
        byte[] bytesEmpty = new byte[0];
        return new PictureData(bytesEmpty, "image/jpeg");
    }

    public PictureData getThumbnail(Context context, String photoId, int thumbnailWidth, int thumbnailHeight, double quality, boolean useCache) {
        try {
            String imageURL = getImageURL(photoId);
            long imageid = getImageId(photoId);
            if (imageid == -1 || imageURL == null) {
                return getEmptyPicture();
            }

            Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), imageid, MediaStore.Images.Thumbnails.MINI_KIND, null);

            //Bitmap bitmap = ThumbnailUtils.createImageThumbnail(imageURL, MediaStore.Images.Thumbnails.MINI_KIND);
            if (bitmap == null) {
                return getEmptyPicture();
            }

            int orientation = PhotoLibraryService.getURLOrientation(imageURL);
            if(orientation > 1) {
                Bitmap rotatedBitmap = PhotoLibraryService.rotateImage(bitmap, orientation);
                if (bitmap != rotatedBitmap) bitmap.recycle();
                bitmap = rotatedBitmap;
            }

            byte[] bytes = getJpegBytesFromBitmap(bitmap, 1.0); // minimize data loss with 1.0 quality
            bitmap.recycle();

            return new PictureData(bytes, "image/jpeg");
        } catch (IOException e) {
            return getEmptyPicture();
        }
    }

    public PictureAsStream getEmptyStream() {
        byte[] bytesEmpty = new byte[0];
        InputStream streamEmpty = new ByteArrayInputStream(bytesEmpty);
        return new PictureAsStream(streamEmpty, "image/jpeg");
    }

    public PictureAsStream getPhotoAsStream(Context context, String photoId) throws IOException {
        return getPhotoAsStream(context, photoId, null, null);
    }
    public PictureAsStream getPhotoAsStream(Context context, String photoId, Integer maxWidth, Integer maxHeight) throws IOException {
        try {
            long imageId = getImageId(photoId);
            String imageURL = getImageURL(photoId);

            if (imageURL == null || imageId == -1) {
                return this.getEmptyStream();
            }
           // maxWidth = 200;
          //  maxHeight = 200;

            File imageFile = new File(imageURL);
            Uri imageUri = Uri.fromFile(imageFile);

            String mimeType = queryMimeType(context, imageId);

            InputStream is = context.getContentResolver().openInputStream(imageUri);

            if (mimeType.equals("image/jpeg")) {
                Bitmap bitmap = null;

                //Resize if needed
                if(maxWidth != null || maxHeight != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                    int imageHeight = options.outHeight;
                    int imageWidth = options.outWidth;

                    bitmap = BitmapFactory.decodeStream(is, null, null);

                    Integer threshold = Math.max(maxWidth == null ? 0 : maxWidth, maxHeight == null ? 0 : maxHeight);
                    if(threshold != null && (imageWidth > threshold  || imageHeight > threshold)) {
                        bitmap = getScaledDownBitmap(bitmap, threshold, true );
                    }
                }

                //Reorient if needed
                int orientation = getImageOrientation(imageFile);
                if (orientation > 1) { // Image should be rotated
                    if(bitmap == null) bitmap = BitmapFactory.decodeStream(is, null, null);

                    Bitmap rotatedBitmap = rotateImage(bitmap, orientation);
                    if(bitmap != rotatedBitmap) bitmap.recycle();
                    bitmap = rotatedBitmap;
                }

                if(bitmap != null) {
                    // Here we perform conversion with data loss, but it seems better than handling orientation in JavaScript.
                    // Converting to PNG can be an option to prevent data loss, but in price of very large files.
                    byte[] bytes = getJpegBytesFromBitmap(bitmap, 1.0); // minimize data loss with 1.0 quality
                    is.close();
                    is = new ByteArrayInputStream(bytes);

                    bitmap.recycle();
                }
            }

            return new PictureAsStream(is, mimeType);
        } catch (Exception e) {
            return this.getEmptyStream();
        }
    }

    public PictureData getPhoto(Context context, String photoId) throws IOException {
        return getPhoto(context, photoId, null, null);

    }
    public PictureData getPhoto(Context context, String photoId, Integer maxWidth, Integer maxHeight) throws IOException {
        PictureAsStream pictureAsStream = getPhotoAsStream(context, photoId, maxWidth, maxHeight);

        byte[] bytes = readBytes(pictureAsStream.getStream());
        pictureAsStream.getStream().close();

        return new PictureData(bytes, pictureAsStream.getMimeType());

    }

    public void saveImage(final Context context, final CordovaInterface cordova, final String url, String album, final JSONObjectRunnable completion)
            throws IOException, URISyntaxException {

        saveMedia(context, cordova, url, album, imageMimeToExtension, new FilePathRunnable() {
            @Override
            public void run(String filePath) {
                try {
                    // Find the saved image in the library and return it as libraryItem
                    String whereClause = MediaStore.MediaColumns.DATA + " = \"" + filePath + "\"";
                    queryLibrary(context, whereClause, new ChunkResultRunnable() {
                        @Override
                        public void run(ArrayList < JSONObject > chunk, int chunkNum, boolean isLastChunk) {
                            completion.run(chunk.size() == 1 ? chunk.get(0) : null);
                        }
                    });
                } catch (Exception e) {
                    completion.run(null);
                }
            }
        });

    }

    public void saveVideo(final Context context, final CordovaInterface cordova, String url, String album)
            throws IOException, URISyntaxException {

        saveMedia(context, cordova, url, album, videMimeToExtension, new FilePathRunnable() {
            @Override
            public void run(String filePath) {
                // TODO: call queryLibrary and return libraryItem of what was saved
            }
        });

    }

    public class PictureData {

        public final byte[] bytes;
        public final String mimeType;

        public PictureData(byte[] bytes, String mimeType) {
            this.bytes = bytes;
            this.mimeType = mimeType;
        }

    }

    public class PictureAsStream {

        public PictureAsStream(InputStream stream, String mimeType) {
            this.stream = stream;
            this.mimeType = mimeType;
        }

        public InputStream getStream() {
            return this.stream;
        }

        public String getMimeType() {
            return this.mimeType;
        }

        private InputStream stream;
        private String mimeType;

    }

    private static PhotoLibraryService instance = null;

    private SimpleDateFormat dateFormatter;

    private Pattern dataURLPattern = Pattern.compile("^data:(.+?)/(.+?);base64,");

    private ArrayList < JSONObject > queryContentProvider(Context context, Uri collection, JSONObject columns, String whereClause) throws JSONException {

        final ArrayList < String > columnNames = new ArrayList < String > ();
        final ArrayList < String > columnValues = new ArrayList < String > ();

        Iterator < String > iteratorFields = columns.keys();

        while (iteratorFields.hasNext()) {
            String column = iteratorFields.next();

            columnNames.add(column);
            columnValues.add("" + columns.getString(column));
        }

        final String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        final Cursor cursor = context.getContentResolver().query(
                collection,
                columnValues.toArray(new String[columns.length()]),
                whereClause, null, sortOrder);

        final ArrayList < JSONObject > buffer = new ArrayList < JSONObject > ();

        if (cursor.moveToFirst()) {
            do {
                JSONObject item = new JSONObject();

                for (String column: columnNames) {
                    int columnIndex = cursor.getColumnIndex(columns.get(column).toString());

                    if (column.startsWith("int.")) {
                        item.put(column.substring(4), cursor.getInt(columnIndex));
                        if (column.substring(4).equals("width") && item.getInt("width") == 0) {
                            System.err.println("cursor: " + cursor.getInt(columnIndex));
                        }
                    } else if (column.startsWith("float.")) {
                        item.put(column.substring(6), cursor.getFloat(columnIndex));
                    } else if (column.startsWith("date.")) {
                        long intDate = cursor.getLong(columnIndex);
                        Date date = new Date(intDate);
                        item.put(column.substring(5), dateFormatter.format(date));
                    } else {
                        item.put(column, cursor.getString(columnIndex));
                    }
                }
                buffer.add(item);

                // TODO: return partial result

            }
            while (cursor.moveToNext());
        }

        cursor.close();

        return buffer;

    }

    private void queryLibrary(Context context, String whereClause, ChunkResultRunnable completion) throws JSONException {
        queryLibrary(context, 0, 0, false, whereClause, completion);
    }

    private void queryLibrary(Context context, int itemsInChunk, double chunkTimeSec, boolean includeAlbumData, String whereClause, ChunkResultRunnable completion)
            throws JSONException {

        // All columns here: https://developer.android.com/reference/android/provider/MediaStore.Images.ImageColumns.html,
        // https://developer.android.com/reference/android/provider/MediaStore.MediaColumns.html
        JSONObject columns = new JSONObject() {
            {
                put("int.id", MediaStore.Images.Media._ID);
                put("fileName", MediaStore.Images.ImageColumns.DISPLAY_NAME);
                put("int.width", MediaStore.Images.ImageColumns.WIDTH);
                put("int.height", MediaStore.Images.ImageColumns.HEIGHT);
                put("albumId", MediaStore.Images.ImageColumns.BUCKET_ID);
                put("date.creationDate", MediaStore.Images.ImageColumns.DATE_TAKEN);
                put("float.latitude", MediaStore.Images.ImageColumns.LATITUDE);
                put("float.longitude", MediaStore.Images.ImageColumns.LONGITUDE);
                put("nativeURL", MediaStore.MediaColumns.DATA); // will not be returned to javascript
            }
        };

        final ArrayList < JSONObject > queryResults = queryContentProvider(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, whereClause);

        ArrayList < JSONObject > chunk = new ArrayList < JSONObject > ();

        long chunkStartTime = SystemClock.elapsedRealtime();
        int chunkNum = 0;

        int size = queryResults.size();

        if(size == 0) {

            completion.run(chunk, chunkNum, true);

        } else {

            for (int i = 0; i < size; i++) {
                JSONObject queryResult = queryResults.get(i);

                // swap width and height if needed
                try {
                    int orientation = getImageOrientation(new File(queryResult.getString("nativeURL")));
                    if (isOrientationSwapsDimensions(orientation)) { // swap width and height
                        int tempWidth = queryResult.getInt("width");
                        queryResult.put("width", queryResult.getInt("height"));
                        queryResult.put("height", tempWidth);
                    }
                } catch (IOException e) {
                    // Do nothing
                }

                // photoId is in format "imageid;imageurl"
                queryResult.put("id",
                        queryResult.get("id") + ";" +
                                queryResult.get("nativeURL"));

                queryResult.remove("nativeURL"); // Not needed

                String albumId = queryResult.getString("albumId");
                queryResult.remove("albumId");
                if (includeAlbumData) {
                    JSONArray albumsArray = new JSONArray();
                    albumsArray.put(albumId);
                    queryResult.put("albumIds", albumsArray);
                }

                chunk.add(queryResult);


                if (i == queryResults.size() - 1) { // Last item
                    Log.d("resPhotos", "lastchunk");
                    completion.run(chunk, chunkNum, true);
                } else if ((itemsInChunk > 0 && chunk.size() == itemsInChunk) || (chunkTimeSec > 0 && (SystemClock.elapsedRealtime() - chunkStartTime) >= chunkTimeSec * 1000)) {

                    Log.d("resPhotos", "size:" + chunk.size());
                    completion.run(chunk, chunkNum, false);
                    chunkNum += 1;
                    chunk = new ArrayList<JSONObject>();
                    chunkStartTime = SystemClock.elapsedRealtime();
                }

            }
        }
    }

    private String queryMimeType(Context context, long imageId) {

        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        MediaStore.Images.ImageColumns.MIME_TYPE
                },
                MediaStore.MediaColumns._ID + "=?",
                new String[] {
                        Long.toString(imageId)
                }, null);

        if (cursor != null && cursor.moveToFirst()) {
            String mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE));
            cursor.close();

            return mimeType;

        }

        cursor.close();
        return null;
    }

    // From https://developer.android.com/training/displaying-bitmaps/load-bitmap.html
    private static int calculateInSampleSize(

            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight &&
                    (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;

    }

    private static byte[] getJpegBytesFromBitmap(Bitmap bitmap, double quality) {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, (int)(quality * 100), stream);

        return stream.toByteArray();

    }

    private static void copyStream(InputStream source, OutputStream target) throws IOException {

        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = source.read(buffer)) != -1) {
            target.write(buffer, 0, len);
        }

    }

    private static byte[] readBytes(InputStream inputStream) throws IOException {

        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }

        return byteBuffer.toByteArray();

    }

    private static String decodePhotoIdURL(String photoId) {
        if (photoId == null) return photoId;
        String[] parts = photoId.split(";");

        //Log.e("decodePhotoIdURL", Integer.toString(parts.length));

        if (parts.length == 1) { //parfois on recoit 118607%3B%2Fstorage%2Femulated%2F0%2FDCIM%2FImages%2Fwashington%20avant%2F20993929_1644849542257108_801754638464679218_n.jpg au lieu de 118607;/storage/emulated/0/DCIM/Images/washington avant/20993929_1644849542257108_801754638464679218_n.jpg
            try {
                String result = URLDecoder.decode(photoId, "UTF-8");
                //	Log.e("decodePhotoIdURL result decode", result);

                return result;
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        } else {

            //	Log.e("decodePhotoIdURL parsLengh >0 photoId:", parts[0]);

            return photoId;
        }
    }

    // photoId is in format "imageid;imageurl;[swap]"
    private static long getImageId(String photoId) {
        photoId = decodePhotoIdURL(photoId);

        if (photoId == null) return -1;

        String[] parts = photoId.split(";");
        // try {
        //  Log.e("Part0", parts[0]);
        try {
            return parts.length >= 1 ? Long.parseLong(parts[0]) : -1;
        } catch (Exception e) {
            return -1;
        }
  /*} catch(Exception e) {
    return -1;
  }*/
        // return Integer.parseInt(photoId.split(";")[0]);
    }

    // photoId is in format "imageid;imageurl;[swap]"
    private static String getImageURL(String photoId) {
        photoId = decodePhotoIdURL(photoId);

        if (photoId == null || photoId.length() == 0) return null;

        String[] parts = photoId.split(";");
        return parts.length >= 2 ? parts[1] : null;
    }

    public static int getURLOrientation(String url) throws IOException {

        try {
            ExifInterface exif = new ExifInterface(url);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            return orientation;
        } catch (IOException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }

    }

    public static int getImageOrientation(File imageFile) throws IOException {
        return getURLOrientation(imageFile.getAbsolutePath());
    }

    // see http://www.daveperrett.com/articles/2012/07/28/exif-orientation-handling-is-a-ghetto/
    public static Bitmap rotateImage(Bitmap source, int orientation) {

        Matrix matrix = new Matrix();

        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL: // 1
                return source;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: // 2
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180: // 3
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: // 4
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE: // 5
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90: // 6
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE: // 7
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270: // 8
                matrix.setRotate(-90);
                break;
            default:
                return source;
        }

        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, false);

    }

    // Returns true if orientation rotates image by 90 or 270 degrees.
    private static boolean isOrientationSwapsDimensions(int orientation) {
        return orientation == ExifInterface.ORIENTATION_TRANSPOSE // 5
                ||
                orientation == ExifInterface.ORIENTATION_ROTATE_90 // 6
                ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE // 7
                ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270; // 8
    }

    private static File makeAlbumInPhotoLibrary(String album) {
        File albumDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), album);
        if (!albumDirectory.exists()) {
            albumDirectory.mkdirs();
        }
        return albumDirectory;
    }

    private File getImageFileName(File albumDirectory, String extension) {
        Calendar calendar = Calendar.getInstance();
        String dateStr = calendar.get(Calendar.YEAR) +
                "-" + calendar.get(Calendar.MONTH) +
                "-" + calendar.get(Calendar.DAY_OF_MONTH);
        int i = 1;
        File result;
        do {
            String fileName = dateStr + "-" + i + extension;
            i += 1;
            result = new File(albumDirectory, fileName);
        } while (result.exists());
        return result;
    }

    private void addFileToMediaLibrary(Context context, File file, final FilePathRunnable completion) {

        String filePath = file.getAbsolutePath();

        MediaScannerConnection.scanFile(context, new String[] {
                filePath
        }, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
                completion.run(path);
            }
        });

    }

    private Map < String, String > imageMimeToExtension = new HashMap < String, String > () {
        {
            put("jpeg", ".jpg");
        }
    };

    private Map < String, String > videMimeToExtension = new HashMap < String, String > () {
        {
            put("quicktime", ".mov");
            put("ogg", ".ogv");
        }
    };

    private void saveMedia(Context context, CordovaInterface cordova, String url, String album, Map < String, String > mimeToExtension, FilePathRunnable completion)
            throws IOException, URISyntaxException {

        File albumDirectory = makeAlbumInPhotoLibrary(album);
        File targetFile;

        if (url.startsWith("data:")) {

            Matcher matcher = dataURLPattern.matcher(url);
            if (!matcher.find()) {
                throw new IllegalArgumentException("The dataURL is in incorrect format");
            }
            String mime = matcher.group(2);
            int dataPos = matcher.end();

            String base64 = url.substring(dataPos); // Use substring and not replace to keep memory footprint small
            byte[] decoded = Base64.decode(base64, Base64.DEFAULT);

            if (decoded == null) {
                throw new IllegalArgumentException("The dataURL could not be decoded");
            }

            String extension = mimeToExtension.get(mime);
            if (extension == null) {
                extension = "." + mime;
            }

            targetFile = getImageFileName(albumDirectory, extension);

            FileOutputStream os = new FileOutputStream(targetFile);

            os.write(decoded);

            os.flush();
            os.close();

        } else {

            String extension = url.contains(".") ? url.substring(url.lastIndexOf(".")) : "";
            targetFile = getImageFileName(albumDirectory, extension);

            InputStream is;
            FileOutputStream os = new FileOutputStream(targetFile);

            if (url.startsWith("file:///android_asset/")) {
                String assetUrl = url.replace("file:///android_asset/", "");
                is = cordova.getActivity().getApplicationContext().getAssets().open(assetUrl);
            } else {
                is = new URL(url).openStream();
            }

            copyStream(is, os);

            os.flush();
            os.close();
            is.close();

        }

        addFileToMediaLibrary(context, targetFile, completion);

    }

    public interface ChunkResultRunnable {

        void run(ArrayList < JSONObject > chunk, int chunkNum, boolean isLastChunk);

    }

    public interface FilePathRunnable {

        void run(String filePath);

    }

    public interface JSONObjectRunnable {

        void run(JSONObject result);

    }

    /**
     * https://gist.github.com/vxhviet/873d142b41217739a1302d337b7285ba
     * @param bitmap the Bitmap to be scaled
     * @param threshold the maxium dimension (either width or height) of the scaled bitmap
     * @param isNecessaryToKeepOrig is it necessary to keep the original bitmap? If not recycle the original bitmap to prevent memory leak.
     * */
    public static Bitmap getScaledDownBitmap(Bitmap bitmap, int threshold, boolean isNecessaryToKeepOrig){
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newWidth = width;
        int newHeight = height;

        if(width > height && width > threshold){
            newWidth = threshold;
            newHeight = (int)(height * (float)newWidth/width);
        }

        if(width > height && width <= threshold){
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        if(width < height && height > threshold){
            newHeight = threshold;
            newWidth = (int)(width * (float)newHeight/height);
        }

        if(width < height && height <= threshold){
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        if(width == height && width > threshold){
            newWidth = threshold;
            newHeight = newWidth;
        }

        if(width == height && width <= threshold){
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        return getResizedBitmap(bitmap, newWidth, newHeight, isNecessaryToKeepOrig);
    }

    private static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight, boolean isNecessaryToKeepOrig) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        if(!isNecessaryToKeepOrig){
            bm.recycle();
        }
        return resizedBitmap;
    }

}