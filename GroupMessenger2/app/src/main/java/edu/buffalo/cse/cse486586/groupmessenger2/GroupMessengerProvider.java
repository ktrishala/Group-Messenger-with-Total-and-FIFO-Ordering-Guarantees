package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String key = values.getAsString("key");
        String value = values.getAsString("value");
        /*
        Referenced from:
        https://developer.android.com/training/data-storage/files#java
         */
        FileOutputStream outputStream;

        try {
            outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
            outputStream.write(value.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        String line = null;
        /*
        Referenced from:
        https://developer.android.com/training/data-storage/files#java
        https://stackoverflow.com/questions/4716503/reading-a-plain-text-file-in-java
        https://stackoverflow.com/questions/18368359/using-addrow-for-matrixcursor-how-to-add-different-object-types
         */
        try{

            File directory = getContext().getFilesDir();
            File file = new File(directory, selection);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            line = bufferedReader.readLine();

        }
        catch (FileNotFoundException e){
            e.printStackTrace();
        }
        catch (Exception e){
            System.out.println(e);
        }
        MatrixCursor cursor = new MatrixCursor(new String[] {"key", "value"});
        cursor.addRow(new String[] { selection, line});
        Log.v("query", selection);
        //Log.v("value", line);
        return cursor;
    }
}
