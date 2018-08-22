package com.rengu.operationsmanagementsuitev3.Service;

import com.rengu.operationsmanagementsuitev3.Entity.ProjectEntity;
import com.rengu.operationsmanagementsuitev3.Entity.UserEntity;
import com.rengu.operationsmanagementsuitev3.Repository.ProjectRepository;
import com.rengu.operationsmanagementsuitev3.Utils.ApplicationMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * @program: OperationsManagementSuiteV3
 * @author: hanchangming
 * @create: 2018-08-22 17:34
 **/


@Slf4j
@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Autowired
    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // 根据用户创建工程
    public ProjectEntity saveProjectByUser(ProjectEntity projectEntity, UserEntity userEntity) {
        if (StringUtils.isEmpty(projectEntity.getName())) {
            throw new RuntimeException(ApplicationMessages.PROJECT_NAME_ARGS_NOT_FOUND);
        }
        if (hasProjectByNameAndUser(projectEntity.getName(), userEntity)) {
            throw new RuntimeException(ApplicationMessages.PROJECT_NAME_EXISTED + projectEntity.getName());
        }
        projectEntity.setUserEntity(userEntity);
        return projectRepository.save(projectEntity);
    }

    // 根据Id删除工程
    @CacheEvict(value = "Project_Cache", allEntries = true)
    public ProjectEntity deleteProjectById(String projectId) {
        ProjectEntity projectEntity = getProjectById(projectId);
        projectRepository.delete(projectEntity);
        return projectEntity;
    }

    // 根据Id修改工程
    @CacheEvict(value = "Project_Cache", allEntries = true)
    public ProjectEntity updateProjectById(String projectId, ProjectEntity projectArgs) {
        ProjectEntity projectEntity = getProjectById(projectId);
        if (!StringUtils.isEmpty(projectArgs.getName()) && !projectEntity.getName().equals(projectArgs.getName())) {
            if (hasProjectByNameAndUser(projectArgs.getName(), projectEntity.getUserEntity())) {
                throw new RuntimeException(ApplicationMessages.PROJECT_NAME_EXISTED + projectArgs.getName());
            }
            projectEntity.setName(projectArgs.getName());
        }
        if (projectArgs.getDescription() != null && !projectEntity.getDescription().equals(projectArgs.getDescription())) {
            projectEntity.setDescription(projectArgs.getDescription());
        }
        return projectRepository.save(projectEntity);
    }


    // 根据名称和用户判断工程是否存在
    public boolean hasProjectByNameAndUser(String name, UserEntity userEntity) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        return projectRepository.existsByNameAndUserEntity(name, userEntity);
    }

    // 根据id判断工程是否存在
    public boolean hasProjectById(String projectId) {
        if (StringUtils.isEmpty(projectId)) {
            return false;
        }
        return projectRepository.existsById(projectId);
    }

    // 根据Id查询工程
    @Cacheable(value = "Project_Cache", key = "#projectId")
    public ProjectEntity getProjectById(String projectId) {
        if (!hasProjectById(projectId)) {
            throw new RuntimeException(ApplicationMessages.PROJECT_ID_NOT_FOUND + projectId);
        }
        return projectRepository.findById(projectId).get();
    }

    // 根据用户查询工程
    public Page<ProjectEntity> getProjectsByUser(Pageable pageable, UserEntity userEntity) {
        return projectRepository.findByUserEntity(pageable, userEntity);
    }

    // 查询所有工程
    public Page<ProjectEntity> getProjects(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }
}
