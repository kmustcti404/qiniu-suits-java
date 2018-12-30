package com.qiniu.persistence;

import java.io.*;
import java.util.*;
import java.util.Map.*;

public class FileMap implements Cloneable {

    private HashMap<String, BufferedWriter> writerMap;
    private HashMap<String, BufferedReader> readerMap;
    private List<String> defaultWriters;
    private String targetFileDir = null;
    private String prefix = null;
    private String suffix = null;

    public FileMap() {
        this.defaultWriters = Collections.singletonList("success");
        this.writerMap = new HashMap<>();
        this.readerMap = new HashMap<>();
    }

    public FileMap(String targetFileDir, String prefix, String suffix) {
        this();
        this.targetFileDir = targetFileDir;
        this.prefix = (prefix == null || "".equals(prefix)) ? "" : prefix + "_";
        this.suffix = (suffix == null || "".equals(suffix)) ? "_0" : "_" + suffix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void addDefaultWriters(String writer) throws IOException {
        if (writer == null || "".equals(writer)) throw new IOException("not valid writer.");
        this.defaultWriters.add(writer);
    }

    public void initDefaultWriters() throws IOException {
        if (targetFileDir == null || "".equals(targetFileDir)) throw new IOException("no targetFileDir.");
        if (prefix == null) throw new IOException("no prefix.");
        if (suffix == null || "".equals(suffix)) throw new IOException("no suffix.");
        for (String targetWriter : defaultWriters) {
            addWriter(prefix + targetWriter + this.suffix);
        }
    }

    public void initDefaultWriters(String targetFileDir, String prefix, String suffix) throws IOException {
        if (targetFileDir != null && !"".equals(targetFileDir)) this.targetFileDir = targetFileDir;
        if (prefix != null && !"".equals(prefix)) this.prefix = prefix + "_";
        else if (this.prefix == null) this.prefix = "";
        if (suffix != null && !"".equals(suffix)) this.suffix = "_" + suffix;
        else if (this.suffix == null) this.suffix = "_0";
        initDefaultWriters();
    }

    private void addWriter(String key) throws IOException {
        File resultFile = new File(targetFileDir, key + ".txt");
        mkDirAndFile(resultFile);
        BufferedWriter writer = new BufferedWriter(new FileWriter(resultFile, true));
        this.writerMap.put(key, writer);
    }

    private synchronized void mkDirAndFile(File filePath) throws IOException {

        int count = 3;
        while (!filePath.getParentFile().exists()) {
            if (count == 0) {
                throw new IOException("can not make directory.");
            }
            filePath.getParentFile().mkdirs();
            count--;
        }

        if (count < 3) System.out.println(filePath.getParentFile());

        count = 3;
        while (!filePath.exists()) {
            if (count == 0) {
                throw new IOException("can not make directory.");
            }
            filePath.createNewFile();
            count--;
        }
    }

    public BufferedWriter getWriter(String key) {
        return this.writerMap.get(key);
    }

    public void closeWriter() {
        for (Map.Entry<String, BufferedWriter> entry : this.writerMap.entrySet()) {
            try {
                this.writerMap.get(entry.getKey()).close();
            } catch (IOException ioException) {
                System.out.println("Writer " + entry.getKey() + " close failed.");
                ioException.printStackTrace();
            }
        }
    }

    public void initReaders(String fileDir) throws IOException {
        File sourceDir = new File(fileDir);
        File[] fs = sourceDir.listFiles();
        String fileName;
        BufferedReader reader;
        assert fs != null;
        for(File f : fs) {
            if (!f.isDirectory()) {
                FileReader fileReader = new FileReader(f.getAbsoluteFile().getPath());
                reader = new BufferedReader(fileReader);
                fileName = f.getName();
                if (fileName.endsWith(".txt")) this.readerMap.put(fileName.substring(0, fileName.length() - 4), reader);
            }
        }
    }

    public void initReader(String fileDir, String fileName) throws IOException {
        if (fileName.endsWith(".txt")) {
            File sourceFile = new File(fileDir, fileName);
            FileReader fileReader = new FileReader(sourceFile);
            BufferedReader reader = new BufferedReader(fileReader);
            this.readerMap.put(fileName.substring(0, fileName.length() - 4), reader);
        } else throw new IOException("please provide the .txt file.");
    }

    public BufferedReader getReader(String key) {
        return this.readerMap.get(key);
    }

    public HashMap<String, BufferedReader> getReaderMap() {
        return readerMap;
    }

    public void closeReader() {
        for (Entry<String, BufferedReader> entry : this.readerMap.entrySet()) {
            try {
                this.readerMap.get(entry.getKey()).close();
            } catch (IOException ioException) {
                System.out.println("Reader " + entry.getKey() + " close failed.");
                ioException.printStackTrace();
            }
        }
    }

    private void doWrite(String key, String item) throws IOException {
        getWriter(key).write(item);
        getWriter(key).newLine();
    }

    public void writeKeyFile(String key, String item) throws IOException {
        if (!writerMap.keySet().contains(prefix + key + suffix)) addWriter(prefix + key + suffix);
        doWrite(prefix + key + suffix, item);
    }

    public void writeSuccess(String item) throws IOException {
        doWrite(this.prefix + "success" + suffix, item);
    }

    public void writeError(String item) throws IOException {
        if (!writerMap.keySet().contains(prefix + "error" + suffix))
            addWriter(prefix + "error" + suffix);
        doWrite(this.prefix + "error" + suffix, item);
    }

    public boolean changeResultName(String newName) {
        File resultFile = new File(targetFileDir, prefix + "success" + suffix + ".txt");
        File newResultFile = new File(targetFileDir, prefix + newName + suffix + ".txt");
        return resultFile.renameTo(newResultFile);
    }
}
