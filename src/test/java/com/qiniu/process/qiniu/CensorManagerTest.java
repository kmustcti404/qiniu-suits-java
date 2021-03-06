package com.qiniu.process.qiniu;

import com.qiniu.config.PropertiesFile;
import com.qiniu.process.qiniu.CensorManager;
import com.qiniu.process.qiniu.Scenes;
import com.qiniu.util.Auth;
import com.qiniu.util.JsonUtils;
import org.junit.Test;

import java.io.IOException;

public class CensorManagerTest {

    @Test
    public void testCensor() throws IOException {

        PropertiesFile propertiesFile = new PropertiesFile("resources/.application.properties");
        String accessKey = propertiesFile.getValue("ak");
        String secretKey = propertiesFile.getValue("sk");
        Auth auth = Auth.create(accessKey, secretKey);
        CensorManager censorManager = new CensorManager(auth);
        String result = censorManager.doVideoCensor("http://xx.com/-YVzTgC_I8zlDYIm8eCcPnA76pU=/ltSP7XPbPGviBNjXiZEHX7mpdm6o",
                new String[]{"pulp"}, 10, "temp", "test-censor", "http://xx.com");
        System.out.println(result);
//        String
                result = censorManager.doImageCensor("http://7xlv47.com1.z0.glb.clouddn.com/pulpsexy.jpg", new String[]{"pulp"});
        System.out.println(result);
        System.out.println(JsonUtils.toJson(censorManager.censorResult("5d480ea3244bbb000818d0f4")));
        System.out.println(Scenes.valueOf("pulp"));
        System.out.println(Scenes.pulp.ordinal());
    }

}