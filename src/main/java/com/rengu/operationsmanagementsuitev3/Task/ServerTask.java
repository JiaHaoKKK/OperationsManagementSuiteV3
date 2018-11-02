package com.rengu.operationsmanagementsuitev3.Task;

import com.rengu.operationsmanagementsuitev3.Entity.HeartbeatEntity;
import com.rengu.operationsmanagementsuitev3.Service.DeviceService;
import com.rengu.operationsmanagementsuitev3.Utils.IPUtils;
import com.rengu.operationsmanagementsuitev3.Utils.ServerCastUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

/**
 * @program: OperationsManagementSuiteV3
 * @author: hanchangming
 * @create: 2018-08-27 15:15
 **/

@Slf4j
@Component
public class ServerTask {

    // 周期发送心跳
    @Scheduled(fixedRate = 1000)
    public void serverCastTask() throws IOException {
        Enumeration<NetworkInterface> networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaceEnumeration.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaceEnumeration.nextElement();
            if (!networkInterface.isLoopback() && !networkInterface.isVirtual()) {
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    if (interfaceAddress.getAddress() != null && interfaceAddress.getBroadcast() != null) {
                        InetAddress inetAddress = interfaceAddress.getAddress();
                        if (!inetAddress.isLoopbackAddress() && IPUtils.isIPv4Address(inetAddress.getHostAddress())) {
                            ServerCastUtils.sendMessage(interfaceAddress);
                        }
                    }
                }
            }
        }
    }

    // 检车设备在线状况
    @Scheduled(fixedRate = 1000 * 2)
    public void onlineHostAdressCheck() {
        Iterator<Map.Entry<String, HeartbeatEntity>> entryIterator = DeviceService.ONLINE_HOST_ADRESS.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, HeartbeatEntity> entry = entryIterator.next();
            HeartbeatEntity heartbeatEntity = entry.getValue();
            if (heartbeatEntity.getCount() - 1 == 0) {
                entryIterator.remove();
                log.info(heartbeatEntity.getHostAddress() + "----->断开服务器连接。");
            } else {
                heartbeatEntity.setCount(heartbeatEntity.getCount() - 1);
            }
        }
    }
}
