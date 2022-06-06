package com.dolphinsdao.tools;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ConvertResizer {
    public static void resize(File file, File to, int hor, int ver) throws InterruptedException, IOException {
        Process converter = new ProcessBuilder("convert", "-resize", hor + "x" + ver,
                file.getAbsolutePath(), to.getAbsolutePath()).start();
        converter.waitFor(10, TimeUnit.SECONDS);
    }
}
