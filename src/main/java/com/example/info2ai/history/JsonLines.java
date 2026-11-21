package com.example.info2ai.history;

import java.io.*;

/** Bounded line allocation even for malformed/untrusted local recordings. */
final class JsonLines {
    private JsonLines() { }
    static String read(Reader input) throws IOException {
        var line=new StringBuilder();int c;
        while((c=input.read())!=-1) {
            if(c=='\n')return line.toString();
            if(line.length()>=1_048_576)throw new IOException("jsonl row exceeds 1 MiB");
            if(c!='\r')line.append((char)c);
        }
        return line.isEmpty()?null:line.toString();
    }
}
