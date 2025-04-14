package com.navarambh.aiotests.utils;

import com.navarambh.aiotests.postbuildsteps.AIOTestsResultRecorder;
import hudson.FilePath;
import hudson.model.Run;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileUtils {

    private static FilePath getFilePath(FilePath filePath, String userPath, Run<?, ?> run, String ext, PrintStream logger, StringBuilder reportText) throws IOException, InterruptedException {
        if (userPath.startsWith("/")) {
            userPath = userPath.substring(1);
        }
        if (userPath.endsWith("**.xml") || userPath.endsWith("**.json")) {
            userPath = userPath.substring(0, userPath.length() - 6);
        }
        if (userPath.endsWith("*.xml") || userPath.endsWith("*.json")) {
            userPath = userPath.substring(0, userPath.length() - 5);
        }
        if (filePath.isRemote()) {
            AIOTestsResultRecorder.aioLogger("Build running on slave", logger, reportText);
            FilePath localPathRoot = new FilePath(run.getRootDir());
            FilePath localPathAbsolute = new FilePath(localPathRoot, userPath);
            FilePath remoteFilePath = new FilePath(filePath, userPath);
            if (remoteFilePath.isDirectory()) {
                try {
                    filePath.copyRecursiveTo(userPath, localPathRoot);
                } catch (Throwable e) {
                    AIOTestsResultRecorder.aioLogger("Error in copy recursive : " + e.getMessage(), logger, reportText);
                    remoteFilePath.list().forEach(f -> {
                        if (f.getName().trim().endsWith(ext)) {
                            FilePath s = new FilePath(remoteFilePath, f.getName());
                            FilePath d = new FilePath(localPathAbsolute, f.getName());
                            try {
                                s.copyTo(d);
                            } catch (Exception ex) {
                                AIOTestsResultRecorder.aioLogger("Error copying file : " + s.getRemote(), logger, reportText);
                            }
                        }
                    });
                }
            } else {
                remoteFilePath.copyTo(localPathAbsolute);
            }
            return localPathAbsolute;

        } else {
            return new FilePath(filePath, userPath);
        }

    }

    public static List<File> getFiles(FilePath filePath, String userPath, Run<?, ?> run, String frameworkType, PrintStream logger, StringBuilder reportText) throws IOException, InterruptedException {
        String ext = frameworkType.equals("cucumber") ? ".json" : ".xml";
        FilePath sourceDir = getFilePath(filePath, userPath, run, ext, logger, reportText);
        List<File> testResultFiles = new ArrayList<>();
        File file = new File(sourceDir.getRemote());
        AIOTestsResultRecorder.aioLogger("File path : " + file.getAbsolutePath(), logger, reportText);
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] matchingFiles = file.listFiles((dir1, name) -> name.trim().matches(".*" + ext));
                if (matchingFiles != null && matchingFiles.length > 0) {
                    testResultFiles.addAll(Arrays.asList(matchingFiles));
                }
            } else {
                testResultFiles.add(file);
            }

        }
        return testResultFiles;
    }

    public static void deleteFile(List<File> files, PrintStream logger, StringBuilder reportText) {
        AIOTestsResultRecorder.aioLogger("Deleting copied files", logger, reportText);
        files.forEach(file1 -> {
            try {
                if (!file1.delete()) {
                    AIOTestsResultRecorder.aioLogger("File could not be deleted @ " + file1.getAbsolutePath() + ".  Please check permissions", logger, reportText);
                }
            } catch (Exception e) {
                AIOTestsResultRecorder.aioLogger("File could not be deleted @ " + file1.getAbsolutePath() + e.getCause(), logger, reportText);
            }
        });
    }

}
