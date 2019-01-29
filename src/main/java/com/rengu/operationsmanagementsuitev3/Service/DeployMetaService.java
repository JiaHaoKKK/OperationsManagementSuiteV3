package com.rengu.operationsmanagementsuitev3.Service;

import com.rengu.operationsmanagementsuitev3.Entity.*;
import com.rengu.operationsmanagementsuitev3.Utils.ApplicationConfig;
import com.rengu.operationsmanagementsuitev3.Utils.ApplicationMessages;
import com.rengu.operationsmanagementsuitev3.Utils.FormatUtils;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @program: OperationsManagementSuiteV3
 * @author: hanchangming
 * @create: 2018-09-05 12:44
 **/

@Slf4j
@Service
public class DeployMetaService {

    public static final Map<String, DeviceEntity> DEPLOYING_DEVICE = new ConcurrentHashMap<>();

    // 部署状态报告信息
    public static final int DEPLOYING_ERROR = 0;
    public static final int DEPLOYING_SUCCEED = 1;
    public static final int DEPLOY_FINISHED = 2;
    public static final int DEPLOYING = 3;

    private final ComponentFileHistoryService componentFileHistoryService;
    private final DeployLogService deployLogService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ComponentFileService componentFileService;

    @Autowired
    public DeployMetaService(ComponentFileHistoryService componentFileHistoryService, DeployLogService deployLogService, SimpMessagingTemplate simpMessagingTemplate, ComponentFileService componentFileService) {
        this.componentFileHistoryService = componentFileHistoryService;
        this.deployLogService = deployLogService;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.componentFileService = componentFileService;
    }

    // 根据部署设计详情创建部署信息
    public List<DeployMetaEntity> createDeployMeta(DeploymentDesignDetailEntity... deploymentDesignDetailEntities) {
        List<DeployMetaEntity> deployMetaEntityList = new ArrayList<>();
        for (DeploymentDesignDetailEntity deploymentDesignDetailEntity : deploymentDesignDetailEntities) {
            DeviceEntity deviceEntity = deploymentDesignDetailEntity.getDeploymentDesignNodeEntity().getDeviceEntity();
            if (deploymentDesignDetailEntity.isKeepLatest()) {
                for (ComponentFileEntity componentFileEntity : componentFileService.getComponentFilesByComponent(deploymentDesignDetailEntity.getComponentEntity())) {
                    if (!componentFileEntity.isFolder()) {
                        DeployMetaEntity deployMetaEntity = new DeployMetaEntity();
                        deployMetaEntity.setDeviceEntity(deviceEntity);
                        deployMetaEntity.setTargetPath(FormatUtils.formatPath(deviceEntity.getDeployPath() + deploymentDesignDetailEntity.getComponentEntity().getRelativePath() + FormatUtils.getComponentFileRelativePath(componentFileEntity, "")));
                        deployMetaEntity.setFileEntity(componentFileEntity.getFileEntity());
                        deployMetaEntityList.add(deployMetaEntity);
                    }
                }
            } else {
                for (ComponentFileHistoryEntity componentFileHistoryEntity : componentFileHistoryService.getComponentFileHistorysByComponentHistory(deploymentDesignDetailEntity.getComponentHistoryEntity())) {
                    if (!componentFileHistoryEntity.isFolder()) {
                        DeployMetaEntity deployMetaEntity = new DeployMetaEntity();
                        deployMetaEntity.setDeviceEntity(deviceEntity);
                        deployMetaEntity.setTargetPath(FormatUtils.formatPath(deviceEntity.getDeployPath() + deploymentDesignDetailEntity.getComponentHistoryEntity().getRelativePath() + FormatUtils.getComponentFileHistoryRelativePath(componentFileHistoryEntity, "")));
                        deployMetaEntity.setFileEntity(componentFileHistoryEntity.getFileEntity());
                        deployMetaEntityList.add(deployMetaEntity);
                    }
                }
            }
        }
        return deployMetaEntityList;
    }

    public void deployMeta(DeploymentDesignEntity deploymentDesignEntity, DeviceEntity deviceEntity, List<DeployMetaEntity> deployMetaEntityList) {
        // 初始化发送进度信息
        long totalSendSize = 0;
        double sendProgress = 0.0;
        double sendSpeed = 0.0;
        // 初始化日志记录
        DeployLogEntity deployLogEntity = new DeployLogEntity();
        deployLogEntity.setTotalFileSize(getTotalFileSize(deployMetaEntityList));
        deployLogEntity.setProjectEntity(deploymentDesignEntity.getProjectEntity());
        // 设备是否在部署的检查
        if (DEPLOYING_DEVICE.containsKey(deviceEntity.getHostAddress())) {
            simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING_ERROR, ApplicationMessages.DEVICE_IS_DEPOLOYING + deviceEntity.getHostAddress()));
            deployLogService.saveDeployLog(deployLogEntity, false, ApplicationMessages.DEVICE_IS_DEPOLOYING + deviceEntity.getHostAddress(), totalSendSize);
            log.info(deviceEntity.getHostAddress() + "设备正在部署，程序退出");
            return;
        }
        DEPLOYING_DEVICE.put(deviceEntity.getHostAddress(), deviceEntity);
        try {
            // 初始化Socket、输入输出流
            @Cleanup Socket socket = new Socket(deviceEntity.getHostAddress(), ApplicationConfig.TCP_DEPLOY_PORT);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(500);
            @Cleanup InputStream inputStream = socket.getInputStream();
            @Cleanup OutputStream outputStream = socket.getOutputStream();
            for (DeployMetaEntity deployMetaEntity : deployMetaEntityList) {
                // 检测设备是否在线
                if (!DeviceService.ONLINE_HOST_ADRESS.containsKey(deployMetaEntity.getDeviceEntity().getHostAddress())) {
                    simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING_ERROR, ApplicationMessages.DEVICE_IS_OFFLINE + deployMetaEntity.getDeviceEntity().getHostAddress()));
                    deployLogService.saveDeployLog(deployLogEntity, false, ApplicationMessages.DEVICE_IS_OFFLINE + deployMetaEntity.getDeviceEntity().getHostAddress(), totalSendSize);
                    log.info(deviceEntity.getHostAddress() + "设备不在线，程序退出");
                    return;
                }
                // 1、发送文件开始标志
                outputStream.write("fileRecvStart".getBytes());
                // 2、发送部署路径
                outputStream.write(FormatUtils.getString(deployMetaEntity.getTargetPath(), 255).getBytes());
                // 3、接受路径回复确认
                long replyTime = System.currentTimeMillis();
                while (true) {
                    try {
                        if (inputStream.read() == 114) {
                            break;
                        }
                    } catch (IOException exception) {
                        if (System.currentTimeMillis() - replyTime >= ApplicationConfig.REPLY_TIME_OUT) {
                            String deployMessage = deviceEntity.getHostAddress() + ":" + deployMetaEntity.getTargetPath() + ",部署失败，接收路径回复超时。当前进度：" + sendProgress + "%";
                            simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING_ERROR, FilenameUtils.getName(deployMetaEntity.getTargetPath()) + "-部署失败"));
                            deployLogService.saveDeployLog(deployLogEntity, false, deployMessage, totalSendSize);
                            log.info(deployMessage + ",程序退出");
                            return;
                        }
                    }
                }
                // 4、发送实体文件(判断文件还是文件夹)
                File file = new File(deployMetaEntity.getFileEntity().getLocalPath());
                @Cleanup RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                long fileSendSize = 0;
                while (fileSendSize < file.length()) {
                    long fileSendStart = System.currentTimeMillis();
                    // 设置读取缓冲区域大小
                    byte[] fileReadBuffer = new byte[ApplicationConfig.FILE_READ_BUFFER_SIZE];
                    int readSize = randomAccessFile.read(fileReadBuffer);
                    if (readSize != -1) {
                        // 更新发送大小
                        fileSendSize = fileSendSize + readSize;
                        byte[] sendBuffer = new byte[readSize];
                        System.arraycopy(fileReadBuffer, 0, sendBuffer, 0, readSize);
                        outputStream.write(sendBuffer);
                        totalSendSize = totalSendSize + readSize;
                        // 发送时间单位:秒
                        double sendTime = ((System.currentTimeMillis() - fileSendStart) + 1) / 1000.0;
                        // 发送大小单位:kb
                        double sendSize = readSize / 1024.0;
                        sendSpeed = sendSize / sendTime;
                        sendProgress = ((double) totalSendSize / deployLogEntity.getTotalFileSize()) * 100;
//                        log.info("开始时间：" + fileSendStart + "，发送大小：" + sendSize + "，发送时间：" + sendTime + ",发送速度：" + sendSpeed + ",发送进度：" + sendProgress);
                        if (file.length() > FileUtils.ONE_MB * 10 && fileSendSize % (ApplicationConfig.FILE_READ_BUFFER_SIZE * 10) == 0) {
                            simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING, FilenameUtils.getName(deployMetaEntity.getTargetPath()) + "-部署中"));
                        }
                    } else {
                        String deployMessage = deviceEntity.getHostAddress() + ":" + deployMetaEntity.getTargetPath() + ",部署失败，文件读取异常。当前进度：" + sendProgress + "%";
                        simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING_ERROR, FilenameUtils.getName(deployMetaEntity.getTargetPath()) + "-部署失败"));
                        deployLogService.saveDeployLog(deployLogEntity, false, deployMessage, totalSendSize);
                        log.info(deployMessage + ",程序退出");
                        return;
                    }
                }
                // 5、结束标志确认
                replyTime = System.currentTimeMillis();
                while (true) {
                    try {
                        if (inputStream.read() == 102) {
                            break;
                        }
                    } catch (IOException exception) {
                        outputStream.write("fileRecvEnd".getBytes());
                        if (System.currentTimeMillis() - replyTime >= ApplicationConfig.REPLY_TIME_OUT) {
                            String deployMessage = deviceEntity.getHostAddress() + ":" + deployMetaEntity.getTargetPath() + ",部署失败，接收文件结束标志回复超时。当前进度：" + sendProgress + "%";
                            simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING_ERROR, FilenameUtils.getName(deployMetaEntity.getTargetPath()) + "-部署失败"));
                            deployLogService.saveDeployLog(deployLogEntity, false, deployMessage, totalSendSize);
                            log.info(deployMessage + ",程序退出");
                            return;
                        }
                    }
                }
                simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING_SUCCEED, FilenameUtils.getName(deployMetaEntity.getTargetPath()) + "-部署成功"));
                log.info(deviceEntity.getHostAddress() + ":" + deployMetaEntity.getTargetPath() + ",部署成功，当前进度：" + sendProgress + "%,当前速度：" + sendSpeed + "kb/s");
            }
            // 6. 发送部署结束标志
            outputStream.write("DeployEnd".getBytes());
            outputStream.flush();
            simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), 0, 100, DEPLOY_FINISHED, "部署结束"));
            deployLogService.saveDeployLog(deployLogEntity, true, "部署成功", totalSendSize);
        } catch (IOException e) {
            e.printStackTrace();
            simpMessagingTemplate.convertAndSend("/deployProgress/" + deploymentDesignEntity.getId(), new DeployProgressEntity(deviceEntity.getHostAddress(), sendSpeed, sendProgress, DEPLOYING_ERROR, e.getMessage()));
            deployLogService.saveDeployLog(deployLogEntity, false, "部署中断", totalSendSize);
        } finally {
            DEPLOYING_DEVICE.remove(deviceEntity.getHostAddress());
            log.info("从正在部署的设备中移除：" + deviceEntity.getHostAddress());
        }
    }

    private long getTotalFileSize(List<DeployMetaEntity> deployMetaEntityList) {
        long totalFileSize = 1;
        for (DeployMetaEntity deployMetaEntity : deployMetaEntityList) {
            totalFileSize = totalFileSize + deployMetaEntity.getFileEntity().getSize();
        }
        return totalFileSize;
    }
}
