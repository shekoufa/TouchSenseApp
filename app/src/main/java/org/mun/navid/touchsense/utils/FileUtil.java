package org.mun.navid.touchsense.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * Created by navid on 2016-11-02.
 */
public class FileUtil {
    public static void writeToFile(String data, Context context) {
        try {
            String filePath = context.getFilesDir() + "/information.txt";
            File theFile = new File(filePath);
            if (!theFile.exists()){
                theFile.createNewFile();
            }

            FileOutputStream f = new FileOutputStream(theFile, true);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(f);
            outputStreamWriter.write(data+"\n");
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
}
