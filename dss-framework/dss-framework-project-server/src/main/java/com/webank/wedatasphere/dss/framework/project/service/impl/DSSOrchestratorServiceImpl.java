/*
 * Copyright 2019 WeBank
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.webank.wedatasphere.dss.framework.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.webank.wedatasphere.dss.framework.common.exception.DSSFrameworkErrorException;
import com.webank.wedatasphere.dss.framework.project.contant.OrchestratorTypeEnum;
import com.webank.wedatasphere.dss.framework.project.contant.ProjectServerResponse;
import com.webank.wedatasphere.dss.framework.project.dao.DSSOrchestratorMapper;
import com.webank.wedatasphere.dss.framework.project.dao.DSSProjectMapper;
import com.webank.wedatasphere.dss.framework.project.entity.DSSOrchestrator;
import com.webank.wedatasphere.dss.framework.project.entity.DSSProjectDO;
import com.webank.wedatasphere.dss.framework.project.entity.DSSProjectUser;
import com.webank.wedatasphere.dss.framework.project.entity.request.OrchestratorCreateRequest;
import com.webank.wedatasphere.dss.framework.project.entity.request.OrchestratorDeleteRequest;
import com.webank.wedatasphere.dss.framework.project.entity.request.OrchestratorModifyRequest;
import com.webank.wedatasphere.dss.framework.project.entity.request.OrchestratorRequest;
import com.webank.wedatasphere.dss.framework.project.entity.vo.OrchestratorBaseInfo;
import com.webank.wedatasphere.dss.framework.project.exception.DSSProjectErrorException;
import com.webank.wedatasphere.dss.framework.project.service.DSSOrchestratorService;
import com.webank.wedatasphere.dss.framework.project.service.DSSProjectUserService;
import com.webank.wedatasphere.dss.framework.project.utils.ProjectStringUtils;
import com.webank.wedatasphere.dss.orchestrator.common.protocol.RequestProjectImportOrchestrator;
import com.webank.wedatasphere.dss.orchestrator.common.ref.OrchestratorCreateResponseRef;
import com.webank.wedatasphere.dss.sender.service.DSSSenderServiceFactory;
import com.webank.wedatasphere.linkis.rpc.Sender;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class DSSOrchestratorServiceImpl extends ServiceImpl<DSSOrchestratorMapper, DSSOrchestrator> implements DSSOrchestratorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DSSOrchestratorServiceImpl.class);
    public static final String MODE_SPLIT = ",";
    @Autowired
    private DSSOrchestratorMapper orchestratorMapper;
    @Autowired
    private DSSProjectUserService projectUserService;
    @Autowired
    private DSSProjectMapper dssProjectMapper;

    private final Sender orcSender = DSSSenderServiceFactory.getOrCreateServiceInstance().getScheduleOrcSender();

    /**
     * ??????????????????
     *
     * @param orchestratorCreateRequest
     * @param responseRef
     * @param username
     * @throws DSSFrameworkErrorException
     * @throws DSSProjectErrorException
     */
    @Override
    public void saveOrchestrator(OrchestratorCreateRequest orchestratorCreateRequest, OrchestratorCreateResponseRef responseRef, String username) throws DSSFrameworkErrorException, DSSProjectErrorException {
        //todo ???????????????????????????????????? ????????????????????????????????????????????????
        projectUserService.isEditProjectAuth(orchestratorCreateRequest.getProjectId(), username);
        DSSOrchestrator orchestrator = new DSSOrchestrator();
        orchestrator.setWorkspaceId(orchestratorCreateRequest.getWorkspaceId());
        orchestrator.setProjectId(orchestratorCreateRequest.getProjectId());
        //????????????id????????????,??????orchestrator???????????????orchestratorId???
        orchestrator.setOrchestratorId(responseRef.getOrcId());
        //??????????????????id????????????,??????orchestrator???????????????orchestratorVersionId???
        orchestrator.setOrchestratorVersionId(responseRef.getOrchestratorVersionId());
        //????????????-??????
        orchestrator.setOrchestratorName(orchestratorCreateRequest.getOrchestratorName());
        //????????????-??????
        orchestrator.setOrchestratorMode(orchestratorCreateRequest.getOrchestratorMode());
        //????????????-??????
        orchestrator.setOrchestratorWay(ProjectStringUtils.getModeStr(orchestratorCreateRequest.getOrchestratorWays()));
        //????????????-??????
        orchestrator.setUses(orchestratorCreateRequest.getUses());
        //????????????-??????
        orchestrator.setDescription(orchestratorCreateRequest.getDescription());
        //????????????-?????????
        orchestrator.setCreateUser(username);
        orchestrator.setCreateTime(new Date());
        orchestrator.setUpdateTime(new Date());
        this.save(orchestrator);
    }

    /**
     * ??????????????????
     *
     * @param orchestratorModifRequest
     * @param username
     * @throws DSSFrameworkErrorException
     * @throws DSSProjectErrorException
     */
    @Override
    public void updateOrchestrator(OrchestratorModifyRequest orchestratorModifRequest, String username) throws DSSFrameworkErrorException, DSSProjectErrorException {
        //todo ???????????????????????????????????? ????????????????????????????????????????????????
        projectUserService.isEditProjectAuth(orchestratorModifRequest.getProjectId(), username);

        DSSOrchestrator orchestrator = new DSSOrchestrator();
        orchestrator.setId(orchestratorModifRequest.getId());
        //????????????-??????
        orchestrator.setOrchestratorName(orchestratorModifRequest.getOrchestratorName());
        //????????????
        orchestrator.setOrchestratorMode(orchestratorModifRequest.getOrchestratorMode());
        //????????????-??????
        orchestrator.setOrchestratorWay(ProjectStringUtils.getModeStr(orchestratorModifRequest.getOrchestratorWays()));
        //????????????-??????
        orchestrator.setUses(orchestratorModifRequest.getUses());
        //????????????-??????
        orchestrator.setDescription(orchestratorModifRequest.getDescription());
        //????????????-?????????
        orchestrator.setUpdateUser(username);
        //????????????-????????????
        orchestrator.setUpdateTime(new Date());
        this.updateById(orchestrator);
    }

    //??????????????????????????????????????????
    @Override
    public void isExistSameNameBeforeCreate(Long workspaceId, Long projectId, String arrangeName) throws DSSFrameworkErrorException {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("workspace_id", workspaceId);
        queryWrapper.eq("project_id", projectId);
        queryWrapper.eq("orchestrator_name", arrangeName);
        List<DSSOrchestrator> list = this.list(queryWrapper);
        if (!CollectionUtils.isEmpty(list)) {
            DSSFrameworkErrorException.dealErrorException(60000, "????????????????????????");
        }
    }

    //?????????????????????????????????,???????????????????????????????????????????????????id
    @Override
    public Long isExistSameNameBeforeUpdate(OrchestratorModifyRequest orchestratorModifRequest) throws DSSFrameworkErrorException {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("workspace_id", orchestratorModifRequest.getWorkspaceId());
        queryWrapper.eq("project_id", orchestratorModifRequest.getProjectId());
        queryWrapper.eq("id", orchestratorModifRequest.getId());
        List<DSSOrchestrator> list = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(list)) {
            DSSFrameworkErrorException.dealErrorException(60000, "????????????ID=" + orchestratorModifRequest.getId() + "?????????");
        }
        //?????????????????????????????????
        if (!orchestratorModifRequest.getOrchestratorName().equals(list.get(0).getOrchestratorName())) {
            isExistSameNameBeforeCreate(orchestratorModifRequest.getWorkspaceId(), orchestratorModifRequest.getProjectId(), orchestratorModifRequest.getOrchestratorName());
        }
        return list.get(0).getOrchestratorId();
    }


    /**
     * ??????????????????
     *
     * @param orchestratorRequest
     * @param username
     * @return
     */
    @Override
    public List<OrchestratorBaseInfo> getListByPage(OrchestratorRequest orchestratorRequest, String username) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("workspace_id", orchestratorRequest.getWorkspaceId());
        queryWrapper.eq("project_id", orchestratorRequest.getProjectId());
        if (StringUtils.isNotBlank(orchestratorRequest.getOrchestratorMode())) {
            queryWrapper.eq("orchestrator_mode", orchestratorRequest.getOrchestratorMode());
        }
        List<DSSOrchestrator> list = this.list(queryWrapper);
        List<OrchestratorBaseInfo> retList = new ArrayList<OrchestratorBaseInfo>(list.size());
        if (!CollectionUtils.isEmpty(list)) {
            //???????????????????????????
            List<DSSProjectUser> projectUserList = projectUserService.getEditProjectList(orchestratorRequest.getProjectId(), username);
            //??????????????????

            Integer priv = CollectionUtils.isEmpty(projectUserList) ? null : projectUserList.stream().mapToInt(DSSProjectUser::getPriv).max().getAsInt();
            OrchestratorBaseInfo orchestratorBaseInfo = null;
            for (DSSOrchestrator orchestrator : list) {
                orchestratorBaseInfo = new OrchestratorBaseInfo();
                BeanUtils.copyProperties(orchestrator, orchestratorBaseInfo);
                orchestratorBaseInfo.setOrchestratorWays(ProjectStringUtils.convertList(orchestrator.getOrchestratorWay()));
                orchestratorBaseInfo.setPriv(priv);
                retList.add(orchestratorBaseInfo);
            }
        }
        return retList;
    }

    /**
     * ??????????????????
     *
     * @param orchestratorDeleteRequest
     * @param username
     * @return
     * @throws DSSProjectErrorException
     */
    @Override
    @SuppressWarnings("all")
    public boolean deleteOrchestrator(OrchestratorDeleteRequest orchestratorDeleteRequest, String username) throws DSSProjectErrorException {
        //todo ???????????????????????????????????? ????????????????????????????????????????????????
        projectUserService.isEditProjectAuth(orchestratorDeleteRequest.getProjectId(), username);

        UpdateWrapper updateWrapper = new UpdateWrapper<DSSOrchestrator>();
        updateWrapper.eq("workspace_id", orchestratorDeleteRequest.getWorkspaceId());
        updateWrapper.eq("project_id", orchestratorDeleteRequest.getProjectId());
        updateWrapper.eq("id", orchestratorDeleteRequest.getId());
        return this.remove(updateWrapper);
    }

    @Override
    public DSSOrchestrator getOrchestratorById(Long id) {
        return this.getById(id);
    }

    @Override
    public Long importOrchestrator(RequestProjectImportOrchestrator orchestratorInfo) throws Exception {
        DSSProjectDO projectDO = dssProjectMapper.selectById(orchestratorInfo.getProjectId());
        if (projectDO == null) {
            DSSFrameworkErrorException.dealErrorException(ProjectServerResponse.PROJECT_NOT_EXIST.getCode(),
                    ProjectServerResponse.PROJECT_NOT_EXIST.getMsg());
        }
        //????????????
        String type = orchestratorInfo.getType();
        if (StringUtils.isEmpty(type)) {
            DSSFrameworkErrorException.dealErrorException(60000, "????????????????????????????????????");
        }
        Long orcId = orchestratorInfo.getId();
        String orchestratorMode = OrchestratorTypeEnum.getKeyByOrcType(type);
        String orchestratorWay = orchestratorInfo.getSecondaryType();
        if (StringUtils.isNotBlank(orchestratorWay)) {
            orchestratorWay = orchestratorWay.replace("[", ",").replace("]", ",");
        }
        //???????????????????????????????????????
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("orchestrator_id", orcId);
        queryWrapper.eq("project_id", projectDO.getId());
        List<DSSOrchestrator> list = this.list(queryWrapper);
        //??????????????????????????????
        if (!CollectionUtils.isEmpty(list)) {
            //????????????
            return updateOrcByImport(list.get(0), orchestratorMode,orchestratorWay,orchestratorInfo);
        } else {
            //????????????
            return insertOrcByImport(projectDO,orchestratorMode,orchestratorWay,orchestratorInfo);
        }
    }

    //????????????
    public Long updateOrcByImport(DSSOrchestrator dbEntity, String orchestratorMode, String orchestratorWay, RequestProjectImportOrchestrator orchestratorInfo) {
        DSSOrchestrator updateEntity = new DSSOrchestrator();
        BeanUtils.copyProperties(dbEntity, updateEntity);
        //????????????
        updateEntity.setOrchestratorName(orchestratorInfo.getName());
        updateEntity.setOrchestratorMode(orchestratorMode);
        updateEntity.setOrchestratorWay(orchestratorWay);
        updateEntity.setDescription(orchestratorInfo.getDesc());
        updateEntity.setUpdateUser(orchestratorInfo.getCreator());
        updateEntity.setUpdateTime(new Date());
        //???????????????
        this.updateById(updateEntity);
        return 0L;
    }

    //????????????
    public Long insertOrcByImport(DSSProjectDO projectDO, String orchestratorMode, String orchestratorWay, RequestProjectImportOrchestrator orchestratorInfo)
            throws DSSFrameworkErrorException {
        //????????????????????????????????????????????????
       /* QueryWrapper queryNameWrapper = new QueryWrapper();
        queryNameWrapper.eq("workspace_id", projectDO.getWorkspaceId());
        queryNameWrapper.eq("project_id", orchestratorInfo.getProjectId());
        queryNameWrapper.eq("orchestrator_name", orchestratorInfo.getName());
        List<DSSOrchestrator> sameNameList = this.list(queryNameWrapper);
        if (!CollectionUtils.isEmpty(sameNameList)) {
            DSSFrameworkErrorException.dealErrorException(60000, "????????????????????????");
        }*/
        DSSOrchestrator orchestrator = new DSSOrchestrator();
        orchestrator.setProjectId(projectDO.getId());
        orchestrator.setOrchestratorId(orchestratorInfo.getId());
        orchestrator.setOrchestratorVersionId(orchestratorInfo.getVersionId());
        orchestrator.setOrchestratorName(orchestratorInfo.getName());
        orchestrator.setOrchestratorMode(orchestratorMode);
        orchestrator.setOrchestratorWay(orchestratorWay);
        orchestrator.setDescription(orchestratorInfo.getDesc());
        orchestrator.setCreateUser(orchestratorInfo.getCreator());
        orchestrator.setCreateTime(new Date());
        orchestrator.setUpdateTime(new Date());
        orchestrator.setWorkspaceId(projectDO.getWorkspaceId());
        this.save(orchestrator);
        return orchestrator.getId();
    }
}
