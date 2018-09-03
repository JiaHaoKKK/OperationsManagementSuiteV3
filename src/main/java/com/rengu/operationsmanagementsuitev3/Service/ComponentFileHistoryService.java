package com.rengu.operationsmanagementsuitev3.Service;

import com.rengu.operationsmanagementsuitev3.Entity.ComponentEntity;
import com.rengu.operationsmanagementsuitev3.Entity.ComponentFileEntity;
import com.rengu.operationsmanagementsuitev3.Entity.ComponentFileHistoryEntity;
import com.rengu.operationsmanagementsuitev3.Entity.ComponentHistoryEntity;
import com.rengu.operationsmanagementsuitev3.Repository.ComponentFileHistoryRepository;
import com.rengu.operationsmanagementsuitev3.Repository.ComponentFileRepository;
import com.rengu.operationsmanagementsuitev3.Utils.ApplicationMessages;
import com.rengu.operationsmanagementsuitev3.Utils.CompressUtils;
import com.rengu.operationsmanagementsuitev3.Utils.FormatUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

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
    private final ComponentFileRepository componentFileRepository;

    @Autowired
    public ComponentFileHistoryService(ComponentFileHistoryRepository componentFileHistoryRepository, ComponentFileRepository componentFileRepository) {
        this.componentFileHistoryRepository = componentFileHistoryRepository;
        this.componentFileRepository = componentFileRepository;
    }

    // 根据组件文件跟节点保存组件文件历史
    public void saveComponentFileHistorysByComponent(ComponentEntity sourceComponent, ComponentHistoryEntity componentHistoryEntity) {
        for (ComponentFileEntity componentFileEntity : componentFileRepository.findByParentNodeAndComponentEntity(null, sourceComponent)) {
            saveComponentFileHistorysByComponentFile(componentFileEntity, sourceComponent, null, componentHistoryEntity);
        }
    }

    // 从组件文件生成组件文件历史
    public void saveComponentFileHistorysByComponentFile(ComponentFileEntity sourceNode, ComponentEntity sourceComponent, ComponentFileHistoryEntity targetNode, ComponentHistoryEntity targetComponent) {
        ComponentFileHistoryEntity copyNode = new ComponentFileHistoryEntity();
        BeanUtils.copyProperties(sourceNode, copyNode, "id", "createTime", "parentNode", "componentEntity");
        copyNode.setParentNode(targetNode);
        copyNode.setComponentHistoryEntity(targetComponent);
        componentFileHistoryRepository.save(copyNode);
        // 递归遍历子节点进行复制
        for (ComponentFileEntity tempComponentFile : componentFileRepository.findByParentNodeAndComponentEntity(sourceNode, sourceComponent)) {
            saveComponentFileHistorysByComponentFile(tempComponentFile, sourceComponent, copyNode, targetComponent);
        }
    }

    // 根据Id判断组件历史文件是否存在
    public boolean hasComponentFileHistorysById(String componentFileHistoryId) {
        if (StringUtils.isEmpty(componentFileHistoryId)) {
            return false;
        }
        return componentFileHistoryRepository.existsById(componentFileHistoryId);
    }

    // 根据id查询组件历史文件
    public ComponentFileHistoryEntity getComponentFileHistorysById(String componentFileHistoryId) {
        if (!hasComponentFileHistorysById(componentFileHistoryId)) {
            throw new RuntimeException(ApplicationMessages.COMPONENT_FILE_HISTORY_ID_NOT_FOUND + componentFileHistoryId);
        }
        return componentFileHistoryRepository.findById(componentFileHistoryId).get();
    }

    // 根据父节点查询组件文件历史
    public List<ComponentFileHistoryEntity> getComponentFileHistorysByParentNodeAndComponentHistory(String parentNodeId, ComponentHistoryEntity componentHistoryEntity) {
        ComponentFileHistoryEntity parentNode = hasComponentFileHistorysById(parentNodeId) ? getComponentFileHistorysById(parentNodeId) : null;
        return componentFileHistoryRepository.findAllByParentNodeAndComponentHistoryEntity(parentNode, componentHistoryEntity);
    }

    // 根据Id导出组件历史文件
    public File exportComponentFileHistoryById(String componentFileHistoryId) throws IOException {
        ComponentFileHistoryEntity componentFileHistoryEntity = getComponentFileHistorysById(componentFileHistoryId);
        if (componentFileHistoryEntity.isFolder()) {
            // 初始化导出目录
            File exportDir = new File(FileUtils.getTempDirectoryPath() + File.separator + UUID.randomUUID().toString());
            exportDir.mkdirs();
            exportComponentFileHistory(componentFileHistoryEntity, exportDir);
            return CompressUtils.compress(exportDir, new File(FileUtils.getTempDirectoryPath() + File.separator + System.currentTimeMillis() + ".zip"));
        } else {
            File exportFile = new File(FileUtils.getTempDirectoryPath() + File.separator + componentFileHistoryEntity.getName() + "." + componentFileHistoryEntity.getFileEntity().getType());
            FileUtils.copyFile(new File(componentFileHistoryEntity.getFileEntity().getLocalPath()), exportFile);
            return exportFile;
        }
    }

    // 导出组件历史文件
    public File exportComponentFileHistory(ComponentFileHistoryEntity componentFileHistoryEntity, File exportDir) throws IOException {
        // 检查是否为文件夹
        if (componentFileHistoryEntity.isFolder()) {
            for (ComponentFileHistoryEntity tempComponentFileHistory : getComponentFileHistorysByParentNodeAndComponentHistory(componentFileHistoryEntity.getId(), componentFileHistoryEntity.getComponentHistoryEntity())) {
                exportComponentFileHistory(tempComponentFileHistory, exportDir);
            }
        } else {
            File file = new File(exportDir.getAbsolutePath() + File.separator + FormatUtils.getComponentFileHistoryRelativePath(componentFileHistoryEntity, ""));
            FileUtils.copyFile(new File(componentFileHistoryEntity.getFileEntity().getLocalPath()), file);
        }
        return exportDir;
    }
}
