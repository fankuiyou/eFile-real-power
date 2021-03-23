package com.fan.nanwang.timeTask;

import com.fan.nanwang.config.ZipDirectory;
import com.fan.nanwang.service.PowerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class PowerTask {

    @Autowired
    private ZipDirectory zipDirectory;

    @Autowired
    private PowerService powerService;

    @Value("${task.power.enable}")
    private boolean enabled;

    @Value("${task.weather.enable}")
    private boolean weatherEnabled;

    @Value("${task.cleanZip.enable}")
    private boolean cleanZipEnabled;

    @Scheduled(cron = "${task.power.cron}")
    public void saveDataToDataBase(){
        if (!enabled) { return; }
        // 通过oss接口获取实时数据文件
        executeLinuxCmd("/home/dky/ossutil64 cp -r oss://os2-wjzz/xny/ " + zipDirectory.getZipPath() + " -u");
        // 解析数据文件并入库
        powerService.saveDataToDataBase();
    }

    @Scheduled(cron = "${task.weather.cron}")
    public void getWeatherData(){
        if (!weatherEnabled) { return; }
        log.info("下载天气数据");
        executeLinuxCmd("/home/dky/ossutil64 cp -r oss://wbsj/hlpt/zdy/zgdky/ " + zipDirectory.getWeatherDataPath() + " -u --config-file /home/dky/weather/.ossutilconfig");
    }

    public void executeLinuxCmd(String cmd) {
        log.info("got cmd job : " + cmd);
        Runtime run = Runtime.getRuntime();
        try {
            Process process = run.exec(cmd);
            InputStream in = process.getInputStream();
            BufferedReader bs = new BufferedReader(new InputStreamReader(in));
            String result = null;
            while ((result = bs.readLine()) != null) {
                log.info("job result [" + result + "]");
            }
            in.close();
            process.destroy();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 每周清理一次压缩包
     */
    @Scheduled(cron = "${task.cleanZip.cron}")
    public void cleanZip(){
        if (!cleanZipEnabled) { return; }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm");
        Date date = new Date();
        date.setMonth(date.getMonth() - 1);
        Long cleanDateNum = Long.parseLong(format.format(date));
        zipDirectory.getList().forEach(region -> {
            List<String> zipFileNameList = Arrays.asList(new File(zipDirectory.getZipPath() + "/" + region).list());
            for(String zipName : zipFileNameList){
                Long zipDateNum = powerService.getDateNum(zipName);
                if(zipDateNum != 0 && zipDateNum < cleanDateNum){
                    new File(zipDirectory.getZipPath() + "/" + region, zipName).delete();
                }
            }
        });
        log.info("压缩包清理成功");
    }
}
