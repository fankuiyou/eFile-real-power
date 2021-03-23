package com.fan.nanwang.service;

import com.fan.nanwang.entity.PowerPlant;
import com.fan.nanwang.entity2.AreaInfo;
import com.fan.nanwang.entity2.FarmInfo;
import com.fan.nanwang.entity2.NetInfo;
import com.fan.nanwang.entity2.RealPower;
import com.fan.nanwang.repository.PowerPlantRepository;
import com.fan.nanwang.repository.PowerRepository;
import com.fan.nanwang.repository2.AreaInfoRepository;
import com.fan.nanwang.repository2.FarmInfoRepository;
import com.fan.nanwang.repository2.NetInfoRepository;
import com.fan.nanwang.repository2.RealPowerRepository;
import com.fan.nanwang.utils.BeanMethodUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RealPowerService {

    @Autowired
    private PowerRepository powerRepository;

    @Autowired
    private PowerPlantRepository powerPlantRepository;

    // 数据源2
    @Autowired
    private FarmInfoRepository farmInfoRepository;

    @Autowired
    private RealPowerRepository realPowerRepository;

    @Autowired
    private AreaInfoRepository areaInfoRepository;

    @Autowired
    private NetInfoRepository netInfoRepository;

    /**
     * 接入实际功率数据
     */
    void saveRealPowerData(){
        String maxDataDate = powerRepository.findMaxDate();
        if(Objects.isNull(maxDataDate)){ return; }
        String startDate = realPowerRepository.findMaxDate();
        if(startDate == null){
            startDate = powerRepository.findMinDate();
        }
        List<FarmInfo> farmInfoList = getHaveDataFarmInfoList();
        while (getNumByDateStr(startDate) <= getNumByDateStr(maxDataDate)){
            log.info(startDate);
            // 按场站保存数据
            saveRealPowerByStation(startDate, farmInfoList);
            // 按地区保存数据
            saveRealPowerByArea(startDate);
            // 按网省保存数据
            saveRealPowerByNet(startDate);
            startDate = addDay(startDate);
        }

        log.info("实际功率数据迁移完成");
    }

    long getNumByDateStr(String dateStr){
        return Long.parseLong(dateStr.replaceAll("-", ""));
    }

    List<FarmInfo> getHaveDataFarmInfoList(){
        List<FarmInfo> haveDataFarmInfo = new ArrayList<>();
        List<PowerPlant> powerPlantList = powerPlantRepository.findAll();
        List<FarmInfo> farmInfoList = farmInfoRepository.findAll();
        for(FarmInfo farmInfo : farmInfoList){
            for(PowerPlant powerPlant : powerPlantList){
                if(Objects.nonNull(farmInfo.getYcId()) && farmInfo.getYcId().equals(powerPlant.getYcId())){
                    haveDataFarmInfo.add(farmInfo);
                    break;
                }
            }
        }
        return haveDataFarmInfo;
    }

    void saveRealPowerByStation(String dateStr, List<FarmInfo> farmInfoList){
        farmInfoList.stream().forEach(farmInfo -> {
            List<Map> powerList = powerRepository.findByYcIdAndDate(farmInfo.getYcId(), dateStr);
            if(powerList.size() > 0){
                RealPower realPower = initRealPower(powerList);
                realPower.setDataDate(dateStr);
                realPower.setDataType(farmInfo.getDataType());
                realPower.setObjType("FARM");
                realPower.setObjId(farmInfo.getFarmId());

                saveRealPower(realPower);
            }
        });
    }

    void saveRealPowerByArea(String dateStr){
        // 按地区和风电光伏保存
        saveAreaByRegion(dateStr);
        // 按网省和风电光伏保存
        saveAreaByProvince(dateStr);
        // 按全网风电和光伏保存
        saveAreaByAllArea(dateStr);
    }

    void saveAreaByRegion(String dateStr){
        List<AreaInfo> areaInfoList = areaInfoRepository.findBypIdIsNotNull();
        areaInfoList.stream().forEach(areaInfo -> {
            try {
                List<String> farmIdList = farmInfoRepository.findByAreaId(areaInfo.getAreaId()).stream().map(FarmInfo::getFarmId).collect(Collectors.toList());
                RealPower realPower = realPowerRepository.findByObjIdListAndDataDate(farmIdList, dateStr);
                if(Objects.nonNull(realPower)){
                    realPower.setId(null);
                    realPower.setObjId(areaInfo.getAreaId());
                    realPower.setObjType(areaInfo.getObjType());
                    realPower.setDataType(areaInfo.getDataType());

                    saveRealPower(realPower);
                }
            }catch (Exception e){
                e.printStackTrace();
                log.error(e.getMessage());
            }
        });
    }

    void saveAreaByProvince(String dateStr){
        List<AreaInfo> areaInfoList = areaInfoRepository.findBypIdIsNull();
        // ANW301、ANW302是南网全网地区编号
        areaInfoList.stream().filter(item -> !"ANW301".equals(item.getAreaId()) && !"ANW302".equals(item.getAreaId())).collect(Collectors.toList());
        areaInfoList.stream().forEach(areaInfo -> {
            try {
                List<String> areaIdList = areaInfoRepository.findBypId(areaInfo.getAreaId()).stream().map(AreaInfo::getAreaId).collect(Collectors.toList());
                RealPower realPower = realPowerRepository.findByObjIdListAndDataDate(areaIdList, dateStr);
                if(Objects.nonNull(realPower)){
                    realPower.setId(null);
                    realPower.setObjId(areaInfo.getAreaId());
                    realPower.setObjType(areaInfo.getObjType());
                    realPower.setDataType(areaInfo.getDataType());

                    saveRealPower(realPower);
                }
            }catch (Exception e){
                e.printStackTrace();
                log.error(e.getMessage());
            }
        });
    }

    void saveAreaByAllArea(String dateStr){
        List<AreaInfo> areaInfoList = areaInfoRepository.findBypIdIsNull();
        // 全网风电 ANW301
        List<String> allNet1IdList = areaInfoList.stream().filter(item -> !"ANW301".equals(item.getAreaId())
                && !"ANW302".equals(item.getAreaId())
                && item.getDataType() == 1).collect(Collectors.toList())
                .stream().map(AreaInfo::getAreaId).collect(Collectors.toList());
        try {
            RealPower realPower = realPowerRepository.findByObjIdListAndDataDate(allNet1IdList, dateStr);
            if(Objects.nonNull(realPower)){
                realPower.setId(null);
                realPower.setObjId("ANW301");
                realPower.setObjType("NET1");
                realPower.setDataType(1);

                saveRealPower(realPower);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error(e.getMessage());
        }

        // 全网光伏 ANW302
        List<String> allNet2IdList = areaInfoList.stream().filter(item -> !"ANW301".equals(item.getAreaId())
                && !"ANW302".equals(item.getAreaId())
                && item.getDataType() == 2).collect(Collectors.toList())
                .stream().map(AreaInfo::getAreaId).collect(Collectors.toList());
        try {
            RealPower realPower = realPowerRepository.findByObjIdListAndDataDate(allNet2IdList, dateStr);
            if(Objects.nonNull(realPower)){
                realPower.setId(null);
                realPower.setObjId("ANW302");
                realPower.setObjType("NET2");
                realPower.setDataType(2);

                saveRealPower(realPower);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    void saveRealPowerByNet(String dateStr){
        // 按网省和风电光伏保存
        saveNetByProvince(dateStr);
        // 按全网风电和光伏保存
        saveNetByAllArea(dateStr);
    }

    void saveNetByProvince(String dateStr){
        List<NetInfo> netInfoList = netInfoRepository.findAll().stream().filter(item -> !"NNW0001".equals(item.getNetId())).collect(Collectors.toList());
        netInfoList.stream().forEach(netInfo -> {
            try {
                List<String> areaIdList = areaInfoRepository.findBypIdIsNullAndNetId(netInfo.getNetId()).stream().map(AreaInfo::getAreaId).collect(Collectors.toList());
                RealPower realPower = realPowerRepository.findByObjIdListAndDataDate(areaIdList, dateStr);
                if(Objects.nonNull(realPower)){
                    realPower.setId(null);
                    realPower.setObjId(netInfo.getNetId());
                    realPower.setObjType("NET");
                    realPower.setDataType(0);

                    saveRealPower(realPower);
                }
            }catch (Exception e){
                e.printStackTrace();
                log.error(e.getMessage());
            }
        });
    }

    void saveNetByAllArea(String dateStr){
        // 全网电场 NNW0001
        try {
            List<String> netIdList = netInfoRepository.findAll().stream().filter(item -> !"NNW0001".equals(item.getNetId()))
                    .collect(Collectors.toList())
                    .stream().map(NetInfo::getNetId).collect(Collectors.toList());
            RealPower realPower = realPowerRepository.findByObjIdListAndDataDate(netIdList, dateStr);
            if(Objects.nonNull(realPower)){
                realPower.setId(null);
                realPower.setObjId("NNW0001");
                realPower.setObjType("NET");
                realPower.setDataType(0);

                saveRealPower(realPower);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    void saveRealPower(RealPower realPower){
        RealPower oldRealPower = realPowerRepository.findRealPowerByObjIdAndDataDate(realPower.getObjId(), realPower.getDataDate());
        if(oldRealPower != null){
            realPower.setId(oldRealPower.getId());
        }
        realPowerRepository.save(realPower);
    }

    RealPower initRealPower(List<Map> powerList){
        RealPower realPower = new RealPower();
        powerList.stream().forEach(powerMap -> {
            String time = powerMap.get("time").toString().replace(":", "");
            time = "0000".equals(time)?"2400":time;
            BeanMethodUtil.set(realPower, "setVal" + time, powerMap.get("power") + "");
        });
        return realPower;
    }

    String addDay(String dateStr){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date date = dateFormat.parse(dateStr);
            date.setDate(date.getDate() + 1);
            return dateFormat.format(date);
        }catch (Exception e){
            return null;
        }
    }
}
