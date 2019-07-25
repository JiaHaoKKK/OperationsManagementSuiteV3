package com.rengu.operationsmanagementsuitev3.Controller;

import com.rengu.operationsmanagementsuitev3.Entity.ResultEntity;
import com.rengu.operationsmanagementsuitev3.Service.SimEngineService;
import com.rengu.operationsmanagementsuitev3.Utils.ResultUtils;
import javafx.scene.chart.ValueAxis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * @Author YJH
 * @Date 2019/3/20 10:12
 */
@RestController
@RequestMapping("/SimEngine")
public class SimEngineController {
    private final SimEngineService simEngineService;


    @Autowired
    public SimEngineController(SimEngineService simEngineService) {
        this.simEngineService = simEngineService;
    }

    @PostMapping("/{deviceID}")
    public ResultEntity getSimEngineCmd(@PathVariable(value = "deviceID")String deviceID, @RequestParam(value = "simEngineCmd") String simEngineCmd) {
        try {
            simEngineService.getSimEngineCmd(simEngineCmd,deviceID);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return  ResultUtils.build(simEngineCmd+" 指令发送成功");
    }
}
