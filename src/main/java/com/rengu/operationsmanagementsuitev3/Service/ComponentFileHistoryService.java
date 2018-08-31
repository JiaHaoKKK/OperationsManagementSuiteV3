package com.rengu.operationsmanagementsuitev3.Service;

import com.rengu.operationsmanagementsuitev3.Entity.ComponentEntity;
import com.rengu.operationsmanagementsuitev3.Entity.ComponentFileEntity;
import com.rengu.operationsmanagementsuitev3.Entity.ComponentFileHistoryEntity;
import com.rengu.operationsmanagementsuitev3.Entity.ComponentHistoryEntity;
import com.rengu.operationsmanagementsuitev3.Repository.ComponentFileHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @program: OperationsManagementSuiteV3
 * @author: hanchangming
 * @create: 2018-08-29 14:20
 **/

@Slf4j
@Service
@Transactional
public class ComponentFileHistoryService {

    private final ComponentFileHistoryRepository componentFileHistoryRepository;
    private final ComponentFileService componentFileService;

    @Autowired
    public ComponentFileHistoryService(ComponentFileHistoryRepository componentFileHistoryRepository, ComponentFileService componentFileService) {
        this.componentFileHistoryRepository = componentFileHistoryRepository;
        this.componentFileService = componentFileService;
    }

    // 根据组件文件跟节点保存组件文件历史
    public void saveComponentFileHistorysByComponent(ComponentEntity sourceComponent, ComponentHistoryEntity componentHistoryEntity) {
        for (ComponentFileEntity componentFileEntity : componentFileService.getComponentFilesByParentNodeAndComponent(null, sourceComponent)) {
            copyComponentFileHistorys(componentFileEntity, sourceComponent, null, componentHistoryEntity);
        }
    }

    // 从组件文件生成组件文件历史
    public void copyComponentFileHistorys(ComponentFileEntity sourceNode, ComponentEntity sourceComponent, ComponentFileHistoryEntity targetNode, ComponentHistoryEntity targetComponent) {
        ComponentFileHistoryEntity copyNode = new ComponentFileHistoryEntity();
        BeanUtils.copyProperties(sourceNode, copyNode, "id", "createTime", "parentNode", "componentEntity");
        copyNode.setParentNode(targetNode);
        copyNode.setComponentHistoryEntity(targetComponent);
        componentFileHistoryRepository.save(copyNode);
        // 递归遍历子节点进行复制
        for (ComponentFileEntity tempComponentFile : componentFileService.getComponentFilesByParentNodeAndComponent(sourceNode.getId(), sourceComponent)) {
            copyComponentFileHistorys(tempComponentFile, sourceComponent, copyNode, targetComponent);
        }
    }
}
