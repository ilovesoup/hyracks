package edu.uci.ics.hyracks.imru.example.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class R {
    public static void p(Object object) {
        p((String)("" + object),new Object[0]);
    }

    public static void p(String format, Object... args) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        String line = "(" + elements[3].getFileName() + ":"
                + elements[3].getLineNumber() + ")";
        String info = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new Date())
                + " " + line;
        synchronized (System.out) {
            System.out.println(info + ": " + String.format(format, args));
        }
    }
}
