package com.fan.nanwang.utils;

import com.fan.nanwang.entity.Power;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.util.zip.GZIPInputStream;

@Slf4j
public class FileUtil {

    public static Power getPowerByFile(File file, String label, String key) {
        InputStreamReader in = null;
        BufferedReader br = null;
        try {
            in = new InputStreamReader(new FileInputStream(file),"GBK");
            br = new BufferedReader(in);
            StringBuffer content = new StringBuffer();
            String s = "";
            while ((s=br.readLine())!=null){
                content = content.append(s);
            }
            // 场站所属地区
            String region = getRegion(content.toString(), label);
            String dataStr = content.substring(content.indexOf("<" + label), content.indexOf("</" + label));
            String date = dataStr.substring(dataStr.indexOf("Date='") + 6, dataStr.indexOf("Date='") + 16);
            String time = dataStr.substring(dataStr.indexOf("Time='") + 6, dataStr.indexOf("Time='") + 11);
            time = time.replace("-", ":");
            // 数据日期
            String dateTime = date + " " + time;
            int minute = Integer.parseInt(dateTime.substring(14));
            minute = minute - (minute % 5);
            if(minute < 10){
                dateTime = dateTime.substring(0, 14) + "0" + minute;
            }else{
                dateTime = dateTime.substring(0, 14) + minute;
            }
            // 场站名称缩写
            String nameAbbreviation = dataStr.substring(dataStr.indexOf("<" + label) + label.length() + 3, dataStr.indexOf("\tDate"));
            // 字段下标
            int columnEndIndex = 0;
            columnEndIndex = dataStr.indexOf("#\t");
            if(columnEndIndex == -1){
                columnEndIndex = dataStr.length();
            }
            String[] columns = dataStr.substring(dataStr.indexOf("@"), columnEndIndex).split("\t");
            int keyIndex = findIndex(columns, key);
            dataStr = dataStr.substring(dataStr.indexOf("#"), dataStr.length());

            // 场站名
            String name = "";
            Double powerNum = 0.0;
            // 并网点有功功率
            String powerStr = "";
            String[] dataList = dataStr.split("#\t");
            // 如果有多条数据
            if(dataList.length > 2){
                name = dataList[1].split("\t")[1];
                for(int i = 1; i < dataList.length; i ++){
                    try {
                        String[] data = dataList[i].split("\t");
                        powerNum += Double.parseDouble(data[keyIndex - 1]);
                    }catch (Exception e){
                        powerNum += 0;
                    }
                }
                powerStr = powerNum.toString();
            }else{  // 如果只有一条数据
                String[] data = dataStr.split("\t");
                name = data[2];
                powerStr = data[keyIndex];
            }

            Power power = new Power();
            try {
                power.setDate(dateTime);
                power.setName(name);
                power.setPower(powerStr);
                power.setRegion(region);
                power.setNameAbbreviation(nameAbbreviation);
                return power;
            }catch (Exception e){
                log.error("数据异常：" + dateTime + " - " + name + " - " + powerNum + " - " + region);
                return null;
            }
        }catch (Exception e){
            return null;
        }finally {
            if(in != null){
                try {
                    in.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            if(br != null){
                try {
                    br.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getRegion(String content, String label){
        int fromIndex = content.indexOf("<" + label + "::") + (3 + label.length());
        int endIndex = content.indexOf(".", fromIndex);
        String region = content.substring(fromIndex, endIndex);
        if("GZ".equalsIgnoreCase(region)){
            region = "GuiZ";
        }
        if(region.length() > 10){
            return "";
        }else{
            return region;
        }
    }

    /**
     * 解压压缩包
     * @param tarGzFile
     * @param destDir
     */
    public static void deCompressGZipFile(String tarGzFile, String destDir) {
        // 建立输出流，用于将从压缩文件中读出的文件流写入到磁盘
        TarArchiveEntry entry = null;
        TarArchiveEntry[] subEntries = null;
        File subEntryFile = null;
        try (FileInputStream fis = new FileInputStream(tarGzFile);
             GZIPInputStream gis = new GZIPInputStream(fis);
             TarArchiveInputStream taris = new TarArchiveInputStream(gis);) {
            while ((entry = taris.getNextTarEntry()) != null) {
                StringBuilder entryFileName = new StringBuilder();
                entryFileName.append(destDir).append(File.separator).append(entry.getName());
                File entryFile = new File(entryFileName.toString());
                if (entry.isDirectory()) {
                    if (!entryFile.exists()) {
                        entryFile.mkdir();
                    }
                    subEntries = entry.getDirectoryEntries();
                    for (int i = 0; i < subEntries.length; i++) {
                        try (OutputStream out = new FileOutputStream(subEntryFile)) {
                            subEntryFile = new File(entryFileName + File.separator + subEntries[i].getName());
                            IOUtils.copy(taris, out);
                        } catch (Exception e) {
                            log.error("deCompressing file failed:" + subEntries[i].getName() + "in" + tarGzFile);
                        }
                    }
                } else {
                    checkFileExists(entryFile);
                    OutputStream out = new FileOutputStream(entryFile);
                    IOUtils.copy(taris, out);
                    out.close();
                    //如果是gz文件进行递归解压
                    if (entryFile.getName().endsWith(".gz")) {
                        deCompressGZipFile(entryFile.getPath(), destDir);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("decompress failed", e);
        }
    }

    public static void checkFileExists(File file) {
        //判断是否是目录
        if (file.isDirectory()) {
            if (!file.exists()) {
                file.mkdir();
            }
        } else {
            //判断父目录是否存在，如果不存在，则创建
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

    public static int findIndex(String[] data, String key){
        for(int index = 0; index < data.length; index ++){
            if(data[index].equals(key)){
                return index;
            }
        }
        return -1;
    }

    public static void main(String[] args){
    }
}
