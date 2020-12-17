package com.navarambh.aiotests.utils;

import java.io.File;

public class FileUtils {
        public static File getFile(String dir, String userPath) {
            File file = new File(dir + userPath);
            if(file.exists()) {
                return file;
            }
            return null;
        }
}
