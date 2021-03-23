package com.fan.nanwang.service;

import com.fan.nanwang.config.ZipDirectory;
import com.fan.nanwang.entity.Power;
import com.fan.nanwang.entity.PowerPlant;
import com.fan.nanwang.repository.PowerPlantRepository;
import com.fan.nanwang.repository.PowerRepository;
import com.fan.nanwang.utils.ControlMaster;
import com.fan.nanwang.utils.FileUtil;
import com.fan.nanwang.utils.SlaveThread;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PowerService {

    @Autowired
    private ZipDirectory zipDirectory;

    @Autowired
    private PowerRepository powerRepository;

    @Autowired
    private PowerPlantRepository powerPlantRepository;

    @Autowired
    private RealPowerService realPowerService;

    List<PowerPlant> powerPlantList = new ArrayList<>();

    public void setPowerPlantList(){
        powerPlantList = powerPlantRepository.findAll();
    }

    /**
     * 保存实际功率数据到数据库
     */
    public void saveDataToDataBase(){
        setPowerPlantList();
        // 注册管理对象
        ControlMaster controlMaster = new ControlMaster();
        zipDirectory.getList().forEach(region -> {
            PowerThread powerThread = new PowerThread(region);
            powerThread.joinMaster(controlMaster);
            powerThread.start();
        });
        //等待上面线程处理结束
        controlMaster.safeDone();
        // 实际功率数据迁移到db2数据库
        realPowerService.saveRealPowerData();
    }

    private class PowerThread extends SlaveThread {
        private String region;
        PowerThread(String region) {
            this.region = region;
        }
        public void slaveRun() {
            log.info(region + "开始入库");
            Long startDateNum = getStartDateStrByRegion(region);
            // 遍历压缩包
            List<String> zipFileNameList = Arrays.asList(new File(zipDirectory.getZipPath() + "/" + region).list());
            // 过滤掉已入库数据文件
            zipFileNameList = zipFileNameList.stream().filter(zipName -> getDateNum(zipName) > startDateNum).collect(Collectors.toList());
            for(String zipName : zipFileNameList){
                log.info(region + ": " + (zipFileNameList.indexOf(zipName) + 1) + "/" + zipFileNameList.size());
                // 清空数据文件目录
                cleanDataFiles(region);
                // 解压数据压缩包到文件夹
                FileUtil.deCompressGZipFile(zipDirectory.getZipPath() + "/" + region + "/" + zipName, zipDirectory.getDirPath() + "/" + region);
                // 功率数据入库
                savePowerData(region);
            }
            log.info(region + "入库完成");
        }
    }

    public Long getStartDateStrByRegion(String region){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try {
            Date maxDate = format.parse(powerRepository.findMaxDateByRegion(region));
            String dateStr = format.format(maxDate);
            dateStr = dateStr.replaceAll("-", "").replaceAll(":", "").replaceAll(" ", "");
            return Long.parseLong(dateStr);
        }catch (Exception e){
            return 0L;
        }
    }

    public Long getDateNum(String str){
        Long dateNum = 0L;
        try {
            String dateStr = str.substring(str.indexOf("_xny_") + 5, str.lastIndexOf("_"));
            if(dateStr.length() == 14){
                dateStr = dateStr.substring(0, dateStr.length() - 2);
            }
            dateNum = Long.parseLong(dateStr);
        }catch (Exception e){
            log.error("压缩包名称提取日期失败: " + str);
        }
        return dateNum;
    }

    /**
     * 功率数据入库
     * @return
     */
    public void savePowerData(String region){
        File directory = new File(zipDirectory.getDirPath() + "/" + region);
        File[] files = directory.listFiles();
        for(File file : files){
            // "_SYZXX_"文件里才包含所需数据
            if(file.getName().indexOf("_SYZXX_") != -1){
                // 获取文件中的功率数据
                Power power = FileUtil.getPowerByFile(file, "BWD", "并网点有功功率");
                if(Objects.nonNull(power) && !"null".equals(power.getPower())){
                    // 保存数据到数据库
                    savePower(power);
                }
            }
        }
    }

    /**
     * 查重 -> 保存数据
     * @param power
     */
    public void savePower(Power power){
        List<Power> data = powerRepository.findByNameAbbreviationAndDate(power.getNameAbbreviation(), power.getDate());
        if(data.size() <= 0){
            powerRepository.save(power);
        }
        // 检测是否新电厂，是就保存到电厂信息表
        checkPowerPlantAndSave(power);
    }

    /**
     * 检测是否新电厂，是就保存到电厂信息表
     * @param power
     */
    public void checkPowerPlantAndSave(Power power){
        try {
            // 检测是否新电厂
            boolean isPresent = powerPlantList.stream().filter(item -> item.getNameAbbreviation().equals(power.getNameAbbreviation())).findFirst().isPresent();
            if(!isPresent){
                // 保存到电厂信息表
                PowerPlant powerPlant = new PowerPlant();
                powerPlant.setRegion(power.getRegion());
                powerPlant.setNameAbbreviation(power.getNameAbbreviation());
                powerPlant.setName(power.getName());
                powerPlantRepository.save(powerPlant);
                // 重新查询电厂信息表
                setPowerPlantList();
            }
        }catch (Exception e){
            log.error(e.getMessage() + "新电厂信息异常={}", power);
        }
    }

    /**
     * 删除处理目录下所有数据文件
     */
    public void cleanDataFiles(String region){
        File file = new File(zipDirectory.getDirPath() + "/" + region);
        String[] content = file.list();
        for (String name : content) {
            File temp = new File(zipDirectory.getDirPath() + "/" + region, name);
            temp.delete();
        }
    }
}
