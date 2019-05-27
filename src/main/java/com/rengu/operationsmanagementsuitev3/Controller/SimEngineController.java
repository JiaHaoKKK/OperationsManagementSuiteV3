package com.rengu.operationsmanagementsuitev3.Controller;

import com.rengu.operationsmanagementsuitev3.Service.SimEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping
    public void getSimEngineCmd(@RequestParam(value = "simEngineCmd") String simEngineCmd) {
        try {
            simEngineService.getSimEngineCmd(simEngineCmd);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
