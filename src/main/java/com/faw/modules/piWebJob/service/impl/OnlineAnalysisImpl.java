/**
 * Copyright (C), 2015-2020, XXX有限公司
 * FileName: OnlineAnalysis
 * Author:   JG
 * Date:     2020/5/22 10:53
 * Description: 在线数据分析
 * History:
 * <author>          <time>          <version>          <desc>
 * 作者姓名           修改时间           版本号              描述
 */
package com.faw.modules.piWebJob.service.impl;

import com.faw.base.annotation.TxtFild;
import com.faw.config.PdfClownKeyConfig;
import com.faw.modules.piWebJob.dao.OnlineDataDao;
import com.faw.modules.piWebJob.dao.PiwebCarInfoDao;
import com.faw.modules.piWebJob.entity.OnLineData;
import com.faw.modules.piWebJob.service.IOnlineAnalysis;
import com.faw.modules.stablePassRate.entity.PiwebStablePassrate;
import com.faw.modules.superAlmost.entity.PiwebSuperAlmost;
import com.faw.utils.io.FileUtils;
import com.faw.utils.pdfBox.PDFUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hadoop.util.hash.Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 〈一句话功能简述〉<br> 
 * 〈在线数据分析〉
 *
 * @author JG
 * @create 2020/5/22
 * @since 1.0.0
 */
@Component("OnlineAnalysis")
public class OnlineAnalysisImpl implements IOnlineAnalysis {

    @Autowired
    private OnlineDataDao onlineDataDao;

    @Autowired
    private PiwebCarInfoDao piwebCarInfoDao;

    @Autowired
    private PdfClownKeyConfig pdfClownKeyConfig;

    @Autowired
    private OnlineDataDao dao;


    //txt 格式文件分析
    public  void  txtAnalysisRule(File dirfile){
        String fileName = dirfile.getName();
        if(!fileName.endsWith("txt")){
            return;
        }
        List<HashMap<String,Object>> results = 	FileUtils.readDir(dirfile);
        //多个文件场景
        HashMap<String,Object> file = new HashMap<>();
        if( results.size() == 0){
            return;
        }
        for(HashMap fileMap: results) { //
            //获取指定的文件
            file = fileMap;

            //解析逻辑
            ArrayList<String> lineVue = (ArrayList<String>) file.get("lineValues");
            List<String[]> clowns = new ArrayList<>();// 全部row信息
            List<String[]> values = new ArrayList<>();// 全部value信息
            List<Set<String>> measurePoints = new ArrayList<>(); // 筛选测量点名

            for (int x = 0; x < lineVue.size(); x++) {
                if (x % 2 == 0) { //clown
                    String[] rowArray = lineVue.get(x).split("\t");
                    clowns.add(rowArray);
                } else {//value
                    String[] valueArray = lineVue.get(x).split("\t");
                    values.add(valueArray);
                }
            }
            //匹配某个对象的正则  筛选出那些对象
            String patterObj = "^(D|B|LS|US)+\\s+\\S*\\s+(X|Y|Z)+$";   // D 偏差 B 理论  LS 下公差  US 上公差
            Pattern p = Pattern.compile(patterObj);


            for (int x = 0; x < clowns.size(); x++) {
                String[] recodeArray = clowns.get(x);
                //循环所有的row信息 按空格分隔  不含空格的为 公共信息  含空格且长度为3第二项为车型
                Set<String> pointInfoSet = new HashSet<>();

                for (int y = 0; y < recodeArray.length; y++) {
                    Matcher matcher = p.matcher(recodeArray[y]);
                    if (matcher.find()) {//匹配到具体obj
                        String[] infoArr = recodeArray[y].split("\\s+"); //空格拆分
                        if (infoArr.length == 3) {
                            pointInfoSet.add(infoArr[1]);//车型
                        }
                    }
                }
                measurePoints.add(pointInfoSet);
            }

            //组合title和value map list
            List<Map<String, String>> dataMapList = new ArrayList<>();
            Map<String, String> dataMap = null;
            for (int x = 0; x < clowns.size(); x++) {
                String[] valueArr = values.get(x);
                String[] clownArr = clowns.get(x);

                dataMap = new HashMap();
                int size = clownArr.length < valueArr.length ? clownArr.length : valueArr.length;

                for (int y = 0; y < size; y++) {
                    dataMap.put(String.valueOf(clownArr[y]), String.valueOf(valueArr[y]));
                }
                dataMapList.add(dataMap);
            }

            //创建实例
            List<OnLineData> onLineDatas = new ArrayList<>();

            //1 根据测量点  循环clowns 获取指定的 clowns信息  在根据转向 分出要创建几个实例
            List<List<String>> objClowns = new ArrayList<>();

            for (Set<String> pointInfoSet : measurePoints) {//获取测量点set
                for (String clownStr : pointInfoSet) {
                    List objClownList = new ArrayList();
                    for (String[] clownArr : clowns) {//循环所有clown
                        for (String clown : clownArr) {
                            //匹配筛选出的测量点
                            if (clown.indexOf(clownStr) != -1) {
                                objClownList.add(clown);
                            }
                        }
                    }
                    objClowns.add(objClownList);
                }

            }

            //2 根据 objClowns 内的 数据 根据方向创建对象
            for (List<String> clownObjs : objClowns) {
                Set<String> directionSet = new HashSet<>();
                for (String clown : clownObjs) {//一个测量点
                    //根据空格拆分出方向
                    String[] objInfo = clown.split(" "); //测量点 A NRSVV1305_R_AA Y 拆分出 方向 和  x y z
                    if (objInfo.length > 0) {
                        String direction = objInfo[0];
                        directionSet.add(direction);
                    }
                }
                try {
                    //循环测量类别
                    for (String direction : directionSet) {//一个方向创建一个对象
                        OnLineData onLineData = new OnLineData();
                        Field[] fields = onLineData.getClass().getDeclaredFields();
                        for (Field field : fields) {
                            field.setAccessible(true);
                            if (field.isAnnotationPresent(TxtFild.class)) { //将指定的key的值设置到对象上
                                TxtFild txtFild = field.getAnnotation(TxtFild.class);
                                String key = txtFild.value();
                                for (Map<String, String> valMap : dataMapList) {//存值的maplist
                                    field.set(onLineData, valMap.get(key));
                                }
                            }
                        }
                        for (String clown : clownObjs) {//所有方向的key
                            //设置 测量 x y z 值
                            if (clown.split(" ")[0].indexOf(direction) != -1) {
                                //设置测量点  A NRSVV1305_R_AA Y   取中间部分
                                onLineData.setMeasurePoint(clown.split(" ")[1]);
                                if (clown.indexOf("X") != -1) { //取X轴信息
                                    for (Map<String, String> valMap : dataMapList) {//存值的maplist
                                        onLineData.setMeasureX(valMap.get(clown));
                                    }
                                }
                                if (clown.indexOf("Y") != -1) { //取Y轴信息
                                    for (Map<String, String> valMap : dataMapList) {//存值的maplist
                                        onLineData.setMeasureY(valMap.get(clown));
                                    }
                                }
                                if (clown.indexOf("Z") != -1) { //取Z轴信息
                                    for (Map<String, String> valMap : dataMapList) {//存值的maplist
                                        onLineData.setMeasureZ(valMap.get(clown));
                                    }
                                }
                            }
                        }
                        //包含该方向
                        onLineData.setMeasureCategory(direction);
                        //设置测量时间
                        onLineData.txtSetMeasureTime();
                        //设置车型
                        onLineData.setModel(onLineData.getUnitName().split("_")[0]);
                        //设置钢号
                        onLineData.setJustNo(onLineData.getPartId().substring(onLineData.getPartId().length()-8));

                        onLineDatas.add(onLineData);
                    }

                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            //查询车车型基础信息 适配工厂
            List<HashMap<String,Object>>  carInfos = piwebCarInfoDao.queryCarInfo();
            //持久化到数据库
            if (onLineDatas.size() > 0) {
                for(OnLineData onLineData:onLineDatas){
                    //设置更新时间
                    try {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        onLineData.setUpdateTime(simpleDateFormat.parse(onLineData.getMeasureTime()));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    for(HashMap<String,Object> carInfo : carInfos){
                        if(carInfo.get("CAR_ALIAS_NAME") !=null && carInfo.get("CAR_ALIAS_NAME") !=""){
                            String[] alisaNames = carInfo.get("CAR_ALIAS_NAME").toString().split(",");
                            if(ArrayUtils.contains(alisaNames, onLineData.getModel())){
                                onLineData.setFactory(carInfo.get("FACTORY").toString());
                            }
                        }

                    }
                }
                onlineDataDao.insertMyBatch(onLineDatas);
            }
        }
    }
    //demo 格式文件分析
    public  void  demoAnalysisRule(File dirfile){
        String fileName = dirfile.getName();
        if(!fileName.endsWith("dmo")){
            return;
        }
        List<HashMap<String,Object>> results = 	FileUtils.readDir(dirfile);
        //多个文件场景
        HashMap<String,Object> file = new HashMap<>();
        if( results.size() == 0){
            return;
        }
        for(HashMap fileMap: results){
                //获取指定的文件
            file = fileMap;

        ArrayList<String> lineValue = (ArrayList<String>) file.get("lineValues");
        Map<String,List<String>>  objectMap = new HashMap<>(); //存放对象
        String key = "";
        for(String str: lineValue){
            // 每个独立的信息 都是 OUTPUT/ 开头的

            if(str.indexOf("OUTPUT/")!=-1){
                //判断当前字符串是否包含, 包含拆分 取 第一个  在用空格拆分取第二个  再取()里信息 做key
                String newStr = str;
                if(str.indexOf(",")!=-1){
                    String[] leveOneArr = str.split(",");
                    newStr = leveOneArr[0];
                }
                if(newStr.indexOf(" ")!= -1){
                    String[] levetwoArr = str.split(" ");
                    newStr = levetwoArr[1];
                }

                Matcher matcher=Pattern.compile("\\(").matcher(newStr);
                int start = 0;
                if(matcher.find()){
                    start =  matcher.start();
                }

                key = newStr.substring(start+1,newStr.lastIndexOf(")"));
                if(!objectMap.containsKey(key)){
                      objectMap.put(key,new ArrayList<String>());
                }
                continue;
            }else if("".equals(key)){
                continue;
            }else if(str.indexOf("RECALL/DA")!=-1){
                key = "RECALL/DA";
                if(!objectMap.containsKey(key)){
                    objectMap.put(key,new ArrayList<String>());
                }
                continue;
            }
            ArrayList<String> objInfoList = (ArrayList<String>) objectMap.get(key);
            objInfoList.add(str);
            objectMap.put(key,objInfoList);
        }
        List<String> baseInfos = new ArrayList<>();

        if(objectMap.size()>0){
            //版本2
            if(objectMap.containsKey("R1")){//基础数据开头
                baseInfos.addAll(objectMap.get("R1"));
                objectMap.remove("R1");
            }
            if(objectMap.containsKey("DATE_TIME")){//整体时间
                baseInfos.addAll(objectMap.get("DATE_TIME"));
                objectMap.remove("DATE_TIME");
            }
        }

        //基础数据map处理
        Map<String,String> baseInfoMap = new HashMap<>();
        for(String baseInfo: baseInfos){
            baseInfo = baseInfo.replace(" ","");
            String[] basInfoArr = baseInfo.split("=");
            String value = basInfoArr[1].replaceAll("'","");
            baseInfoMap.put(basInfoArr[0],value);
        }
        //实例化数据
        List<OnLineData> dataList = new ArrayList<>();
        for(String mapKey : objectMap.keySet()){
            //末尾的数据统计 不处理  即 key 类似 R99
            String rex = "^R[0-9]{2,}$";
            Pattern p = Pattern.compile(rex);
            Matcher matcher=p.matcher(mapKey);
            if(matcher.find()){
               break;
            }
            List<String> mapList = objectMap.get(mapKey);
            String[] categorys = new String[]{"F","T","TA"};// F：理论值  T:公差 上存US 下存 LS    TA:偏差  存 D
            String[]  valeKeys = new String[]{"X","Y","Z","P"}; //
            String[]  directs = new String[]{"UP","DOWN"};// UP:上公差   DOWN:下公差
                for(int x = 0;x<categorys.length;x++){
                    if("T".equals(categorys[x])){
                        for (int y=0;y<directs.length;y++){
                            //创建对像
                            OnLineData onLineData = new OnLineData();
                                onLineData.setCategoryDirect(directs[y]);//设置方向
                                onLineData.setMeasurePoint(mapKey);//设置测量点
                                if("UP".equals(directs[y])){  // T - > US
                                    onLineData.setMeasureCategory("US");//设置测量点类型
                                }else{
                                    onLineData.setMeasureCategory("LS");//设置测量点类型
                                }


                                onLineData = demoSetBaseInfo(onLineData,baseInfoMap); //设置基础信息
                                     for(String valeKey : valeKeys ){
                                         String keyFiltStr = categorys[x]+"("+mapKey+valeKey+")";
                                         for(String info : mapList){
                                          if(info.indexOf(keyFiltStr)!=-1){
                                             String value = "";
                                             if("UP".equals(directs[y])){
                                                 value = info.split(",")[3];
                                                 if("P".equals(valeKey)){    //针对 P值截取数据的下标 为 2 或 1
                                                     value = info.split(",")[2];
                                                 }
                                             }else{
                                                 value = info.split(",")[2];
                                                 if("P".equals(valeKey)){    //针对 P值截取数据的下标 为 2 或 1
                                                     value = info.split(",")[1];
                                                 }
                                             }
                                             //设置值
                                             try {
                                                 Field classFile = onLineData.getClass().getDeclaredField("measure"+valeKey);
                                                 classFile.setAccessible(true);
                                                 classFile.set(onLineData,value);
                                             } catch (NoSuchFieldException e) {
                                                 e.printStackTrace();
                                             } catch (IllegalAccessException e) {
                                                 e.printStackTrace();
                                             }
                                         }
                                     }
                                 }
                                dataList.add(onLineData);
                           }
                      }

                    if("F".equals(categorys[x])){//理论值
                        //创建对像
                        OnLineData onLineData = new OnLineData();
                        onLineData.setMeasurePoint(mapKey);//设置测量点
                        // txt demo 理论值同一类型为 B
                        onLineData.setMeasureCategory("B");//设置测量点类型
                        onLineData = demoSetBaseInfo(onLineData,baseInfoMap); //设置基础信息

                        String keyF = categorys[x]+"("+mapKey+")";
                        for(String info : mapList){
                            String[] values = info.split(",");
                            if(info.indexOf(keyF)!=-1){
                                onLineData.setMeasureX(values[2]);
                                onLineData.setMeasureY(values[3]);
                                onLineData.setMeasureZ(values[4]);
                            }
                        }
                        dataList.add(onLineData);
                    }
                    if("TA".equals(categorys[x])) {//偏差   统一设置D
                        //创建对像
                        OnLineData onLineData = new OnLineData();
                        onLineData.setMeasurePoint(mapKey);//设置测量点
                        onLineData.setMeasureCategory("D");//设置测量点
                        onLineData = demoSetBaseInfo(onLineData,baseInfoMap); //设置基础信息

                            for (String valeKey : valeKeys) {
                                String keyFiltStr = categorys[x] + "(" + mapKey + valeKey + ")";
                                for(String info : mapList){//当前测量点的数据
                                 if (info.indexOf(keyFiltStr) != -1) {
                                    String value = "";
                                        value = info.split(",")[2];
                                        if ("P".equals(valeKey)) {    //针对 P值截取数据的下标 为 2 或 1
                                            value = info.split(",")[1];
                                        }
                                    //设置值
                                    try {
                                        Field classFile = onLineData.getClass().getDeclaredField("measure" + valeKey);
                                        classFile.setAccessible(true);
                                        classFile.set(onLineData, value);
                                    } catch (NoSuchFieldException e) {
                                        e.printStackTrace();
                                    } catch (IllegalAccessException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                       }
                        dataList.add(onLineData);
                    }
                }
        }
        //查出工厂适配表 将车型适配为
        List<HashMap<String,Object>>  carInfos = piwebCarInfoDao.queryCarInfo();

        if(dataList.size()>0){
            for(OnLineData onLineData:dataList){
                //设置更新时间
                try {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    onLineData.setUpdateTime(simpleDateFormat.parse(onLineData.getMeasureTime()));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                for(HashMap<String,Object> carInfo : carInfos){
                    if(carInfo.get("CAR_ALIAS_NAME") !=null && carInfo.get("CAR_ALIAS_NAME") !="") {
                        String[] alisaNames = carInfo.get("CAR_ALIAS_NAME").toString().split(",");
                        if (ArrayUtils.contains(alisaNames, onLineData.getModel())) {
                            onLineData.setFactory(carInfo.get("FACTORY").toString());
                        }
                    }
                }
            }
            onlineDataDao.insertMyBatch(dataList);
        }

        }
    }

    private  OnLineData demoSetBaseInfo(OnLineData onLineData,Map<String,String> baseInfoMap){
        String dateStr = ""; // 时间
        //版本1
        if(baseInfoMap.containsKey("DATE")){
            dateStr =  dateStr.concat(baseInfoMap.get("DATE").replace("/","-"));
        }
        if(baseInfoMap.containsKey("TIME")){
            dateStr=  dateStr.concat(" "+baseInfoMap.get("TIME"));
        }
        if(baseInfoMap.containsKey("MD(YMD1)")){
            onLineData.setUnitName(baseInfoMap.get("MD(YMD1)"));//设置零件名
        }
        //设置车型
        if(baseInfoMap.containsKey("DI(Messmittel)")){
            onLineData.setModel(baseInfoMap.get("DI(Messmittel)"));//设置车型
        }
        //设置钢号
        if(baseInfoMap.containsKey("PS(LFD_NR)")){
            onLineData.setJustNo(baseInfoMap.get("PS(LFD_NR)"));//钢号
        }


        onLineData.setMeasureTime(dateStr);//设置时间
        return onLineData;
    }

    //pdf -------------解析------------------
    @Override
    public void superAlmostExtractor(File file) {
        List<String> allRows = new ArrayList<>();
        List<List<String>> pageList = PDFUtils.redTable(file);
        List<PiwebSuperAlmost> results = new ArrayList<>();

        for(List<String> rows : pageList){
            for(int x=1;x<rows.size();x++){
                allRows.add(rows.get(x));
            }
        }
        for(String line : allRows ){
            String[] clowns =  line.split("\\|");
            Map<String,Integer> superAlmostMaoKey =  pdfClownKeyConfig.getSuperAlmost();
            //没行一个实体
            PiwebSuperAlmost superAlmost = new PiwebSuperAlmost();
            Class clazz = superAlmost.getClass();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");

            //循环配置文件给 实体的 参数赋值
            for(String key : superAlmostMaoKey.keySet()){
                try {
                    Field entityFile =  clazz.getDeclaredField(key);
                    entityFile.setAccessible(true);
                    if("createTime".equals(key)){ //时间单独处理
                        entityFile.set(superAlmost,sdf.parse(clowns[superAlmostMaoKey.get(key)]));
                    }
                    else{
                        entityFile.set(superAlmost,clowns[superAlmostMaoKey.get(key)].toString().replace("\\\"","").trim());
                    }
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            }
            //针对  字符串的 dataTime
            superAlmost.setDateTime(sdf2.format(superAlmost.getCreateTime()));
            results.add(superAlmost);
        }

        if(results.size()>0){
            dao.insertListSuper(results);
        }

    }

    @Override
    public void stablePassRateExtractor(File file) {
        List<List<String>> pageList = PDFUtils.redTable(file);
        List<String> allRows = new ArrayList<>();
        List<PiwebStablePassrate> results = new ArrayList<>();

        for(List<String> rows : pageList){
            for(int x=0;x<rows.size();x++){
                allRows.add(rows.get(x));
            }
        }

        for(String line : allRows ){
            String[] clowns =  line.split("\\|");

            Map<String,Integer> stablePassRateKey =  pdfClownKeyConfig.getStablePassRate();
            if(clowns.length<stablePassRateKey.size()){
                continue;
            }
            //没行一个实体
            PiwebStablePassrate stablePassrate = new PiwebStablePassrate();
            Class clazz = stablePassrate.getClass();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

            //循环配置文件给 实体的 参数赋值
            for(String key : stablePassRateKey.keySet()){
                try {
                    Field entityFile =  clazz.getDeclaredField(key);
                    entityFile.setAccessible(true);
                    if("createTime".equals(key)){ //时间单独处理
                        entityFile.set(stablePassrate,sdf.parse(clowns[stablePassRateKey.get(key)]));
                    }
                    else{
                        entityFile.set(stablePassrate,clowns[stablePassRateKey.get(key)].toString().replaceAll("\\\"","").replaceAll(" ",""));
                    }
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            }
            results.add(stablePassrate);
        }
        if(results.size()>0){
            dao.insertListStable(results);
        }

    }
}
