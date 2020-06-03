package com.faw.modules.piWebJob.watcher;

import com.faw.modules.piWebJob.service.IOnlineAnalysis;
import com.faw.utils.io.FileUtils;
import com.faw.utils.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.List;

@Component
@Order(value = 1)
public class StartGlobalKeyboard implements CommandLineRunner {
    //txt数据文件夹
    @Value("${abf.onlineData.dirPathTxt}")
    private  String txtDir;

    //demo数据文件夹
    @Value("${abf.onlineData.dirPathDemo}")
    private  String demoDir;

    //首次启动标识
    @Value("${abf.onlineData.firstStart}")
    private  String firstStart;

    @Autowired
    private IOnlineAnalysis onlineAnalysis;

    @Override
    public void run(String... args) throws Exception {
        //你的要执行的代码
        final File txtFile = new File(txtDir);

        final File demoFile = new File(demoDir);


        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    read(txtFile,"txt");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    read(demoFile,"demo");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void  read (File file,String bustype) throws Exception {

        File fistStart = new File(firstStart);
        if(fistStart.exists()){
            List<String> lines = FileUtils.resolveFile(fistStart);
            for(String s : lines){
                if("first=1".equals(s)){
                    //已经首次启动过了
                    return;
                }
            }
        }else{
            if(fistStart.createNewFile()){
                FileOutputStream fileOutputStream= new FileOutputStream(fistStart);
                IOUtils.write("first=1".getBytes(),fileOutputStream);
                fileOutputStream.close();
            }
        }
        File f = new File(file.getAbsolutePath());
        if("txt".equals(bustype)){
            onlineAnalysis.txtAnalysisRule(f);
        }else if("demo".equals(bustype)){
            onlineAnalysis.demoAnalysisRule(f);
        }

    }

    public void copy(File f,File f1) throws IOException { //复制文件的方法！
        if(!f1.exists()){
            f1.mkdir();
        }
        if(f.isDirectory()){//路径e79fa5e98193e59b9ee7ad9431333332616430判断，是路径还是单个的文件
            File[] cf = f.listFiles();
            System.out.println("length" + cf.length);
            System.out.println("length2" + f.length());
            for(File fn : cf){
                if(fn.isFile()){
                    FileInputStream fis = new FileInputStream(fn);
                    FileOutputStream fos = new FileOutputStream(f1 + "\\" +fn.getName());
                    byte[] b = new byte[1024];
                    int i = fis.read(b);
                    while(i != -1){
                        fos.write(b, 0, i);
                        i = fis.read(b);
                    }
                    fis.close();
                    fos.close();
                }else{
                    File fb = new File(f1 + "\\" + fn.getName());
                    fb.mkdir();
                    if(fn.listFiles() != null){//如果有子目录递归复制子目录！
                        copy(fn,fb);
                    }
                }
            }
        }else{
            FileInputStream fis = new FileInputStream(f);
            FileOutputStream fos = new FileOutputStream(f1 + "\\" +f.getName());
            byte[] b = new byte[1024];
            int i = fis.read(b);
            while(i != -1){
                fos.write(b, 0, i);
                i = fis.read(b);
            }
            fis.close();
            fos.close();
        }
    }
}