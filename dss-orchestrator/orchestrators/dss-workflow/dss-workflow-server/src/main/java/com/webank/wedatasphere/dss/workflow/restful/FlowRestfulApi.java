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

package com.webank.wedatasphere.dss.workflow.restful;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.webank.wedatasphere.dss.common.exception.DSSErrorException;
import com.webank.wedatasphere.dss.common.label.DSSLabel;
import com.webank.wedatasphere.dss.common.label.EnvDSSLabel;
import com.webank.wedatasphere.dss.common.label.LabelKeyConvertor;
import com.webank.wedatasphere.dss.common.utils.DSSCommonUtils;
import com.webank.wedatasphere.dss.contextservice.service.ContextService;
import com.webank.wedatasphere.dss.contextservice.service.impl.ContextServiceImpl;
import com.webank.wedatasphere.dss.orchestrator.common.protocol.ResponseConvertOrchestrator;
import com.webank.wedatasphere.dss.standard.app.sso.Workspace;
import com.webank.wedatasphere.dss.standard.sso.utils.SSOHelper;
import com.webank.wedatasphere.dss.workflow.WorkFlowManager;
import com.webank.wedatasphere.dss.workflow.common.entity.DSSFlow;
import com.webank.wedatasphere.dss.workflow.constant.DSSWorkFlowConstant;
import com.webank.wedatasphere.dss.workflow.service.DSSFlowService;
import com.webank.wedatasphere.dss.workflow.service.PublishService;
import com.webank.wedatasphere.linkis.server.Message;
import com.webank.wedatasphere.linkis.server.security.SecurityFilter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Path("/dss/workflow")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlowRestfulApi {

    @Autowired
    private DSSFlowService flowService;


    private ContextService contextService = ContextServiceImpl.getInstance();

    @Autowired
    private PublishService publishService;

    @Autowired
    private WorkFlowManager workFlowManager;

    ObjectMapper mapper = new ObjectMapper();


    private static final Logger LOGGER = LoggerFactory.getLogger(FlowRestfulApi.class);

    @POST
    @Path("/addFlow")
//    @ProjectPrivChecker
    public Response addFlow(@Context HttpServletRequest req, JsonNode json) throws DSSErrorException, JsonProcessingException {
        //??????????????????????????????????????????????????????????????????
        String userName = SecurityFilter.getLoginUsername(req);
        // TODO: 2019/5/23 flowName????????????????????????
        String name = json.get("name").getTextValue();
        String workspaceName = json.get("workspaceName").getTextValue();
        String projectName = json.get("projectName").getTextValue();
        String version = json.get("version").getTextValue();
        String description = json.get("description") == null ? null : json.get("description").getTextValue();
        Long parentFlowID = json.get("parentFlowID") == null ? null : json.get("parentFlowID").getLongValue();
        String uses = json.get("uses") == null ? null : json.get("uses").getTextValue();
        JsonNode dssLabelsJsonNode = json.get(DSSCommonUtils.DSS_LABELS_KEY);
        List<DSSLabel> dssLabelList = new ArrayList<>();
        if (dssLabelsJsonNode != null && dssLabelsJsonNode.getElements().hasNext()) {
            Iterator<JsonNode> nodeList = dssLabelsJsonNode.getElements();
            while (nodeList.hasNext()) {
                JsonNode objNode =nodeList.next();
                EnvDSSLabel dssLabel = new EnvDSSLabel(objNode.getTextValue());
                dssLabelList.add(dssLabel);
            }
        }
        String contextId = contextService.createContextID(workspaceName, projectName, name, version, userName);

        DSSFlow dssFlow = workFlowManager.createWorkflow(userName,name,contextId,description,parentFlowID,uses,null,dssLabelList);

        // TODO: 2019/5/16 ??????????????????????????????
        return Message.messageToResponse(Message.ok().data("flow", dssFlow));
    }


    @POST
    @Path("/publishWorkflow")
    public Response publishWorkflow(@Context HttpServletRequest request, JsonNode jsonNode) {
        Long workflowId = jsonNode.get("workflowId").getLongValue();
//        Map<String, Object> labels = StreamSupport.stream(Spliterators.spliteratorUnknownSize(dssLabel.getFields(),
//            Spliterator.ORDERED), false).collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().getTextValue()));
        //todo modify by front label
        JsonNode labelJsonNode = jsonNode.get(DSSCommonUtils.DSS_LABELS_KEY);
        String dssLabel = labelJsonNode.get(LabelKeyConvertor.ROUTE_LABEL_KEY).getTextValue();
        Map<String, Object> labels=new HashMap<>();
        labels.put(EnvDSSLabel.DSS_ENV_LABEL_KEY,dssLabel);
        String comment = jsonNode.get("comment").getTextValue();
        Workspace workspace = SSOHelper.getWorkspace(request);
        String publishUser = SecurityFilter.getLoginUsername(request);
        Message message;
        try{
            String taskId = publishService.submitPublish(publishUser, workflowId, labels, workspace, comment);
            LOGGER.info("submit publish task ok ,taskId is {}.", taskId);
            if (StringUtils.isNotEmpty(taskId)){
                message = Message.ok("?????????????????????????????????").data("releaseTaskId", taskId);
            } else{
                LOGGER.error("taskId {} is error.", taskId);
                message = Message.error("?????????????????????");
            }
        }catch(final Throwable t){
            LOGGER.error("failed to submit publish task for workflow id {}.", workflowId, t);
            message = Message.error("?????????????????????");
        }
        return Message.messageToResponse(message);
    }


    /**
     * ????????????????????????
     * @param request
     * @param releaseTaskId
     * @return
     */

    @GET
    @Path("/getReleaseStatus")
    public Response getReleaseStatus(@Context HttpServletRequest request,
                                     @NotNull(message = "???????????????id????????????") @QueryParam("releaseTaskId") Long releaseTaskId) {
        String username = SecurityFilter.getLoginUsername(request);
        Message message;
        try {
            ResponseConvertOrchestrator response = publishService.getStatus(username, releaseTaskId.toString());
            if (null != response.getResponse()) {
                String status = response.getResponse().getJobStatus().toString();
                status = StringUtils.isNotBlank(status) ? status.toLowerCase() : status;
                //????????????????????????????????????
                if ("failed".equalsIgnoreCase(status)) {
                    message = Message.error("????????????:" + response.getResponse().getMessage()).data("status", status);
                } else if (StringUtils.isNotBlank(status)) {
                    message = Message.ok("??????????????????").data("status", status);
                } else {
                    LOGGER.error("status is null or empty, failed to get status");
                    message = Message.error("??????????????????");
                }
            } else {
                LOGGER.error("status is null or empty, failed to get status");
                message = Message.error("??????????????????");
            }
        } catch (final Throwable t) {
            LOGGER.error("Failed to get release status for {}", releaseTaskId, t);
            message = Message.error("????????????:" + t.getMessage());
        }
        return Message.messageToResponse(message);
    }


    /**
     * ????????????????????????????????????????????????Json,BML?????????
     * @param req
     * @param json
     * @return
     * @throws DSSErrorException
     */

    @POST
    @Path("/updateFlowBaseInfo")
//    @ProjectPrivChecker
    public Response updateFlowBaseInfo(@Context HttpServletRequest req, JsonNode json) throws DSSErrorException {
        Long flowID = json.get("id").getLongValue();
        String name = json.get("name") == null ? null : json.get("name").getTextValue();
        String description = json.get("description") == null ? null : json.get("description").getTextValue();
        String uses = json.get("uses") == null ? null : json.get("uses").getTextValue();
//        ioManager.checkeIsExecuting(projectVersionID);
        // TODO: 2019/6/13  projectVersionID???????????????
        //????????????????????????
        DSSFlow dssFlow = new DSSFlow();
        dssFlow.setId(flowID);
        dssFlow.setName(name);
        dssFlow.setDescription(description);
        dssFlow.setUses(uses);
        flowService.updateFlowBaseInfo(dssFlow);
        return Message.messageToResponse(Message.ok());
    }

    /**
     * ??????????????????Json??????????????????????????????
     * @param req
     * @param flowID
     * @return
     * @throws DSSErrorException
     */

    @GET
    @Path("/get")
    public Response get(@Context HttpServletRequest req, @QueryParam("flowId") Long flowID
    ) throws DSSErrorException {
        // TODO: 2019/5/23 id????????????
        String username = SecurityFilter.getLoginUsername(req);
        DSSFlow DSSFlow;
        DSSFlow = flowService.getLatestVersionFlow(flowID);
//        if (!username.equals(DSSFlow.getCreator())) {
//            return Message.messageToResponse(Message.ok("??????????????????????????????"));
//        }
        return Message.messageToResponse(Message.ok().data("flow", DSSFlow));
    }


//    @GET
//    @Path("/product/get")
//    public Response productGet(@Context HttpServletRequest req, @QueryParam("id") Long flowID) throws ErrorException {
//        DSSFlow DSSFlow;
//        DSSFlow = flowService.getLatestVersionFlow(flowID);
////        dwsFlow=flowService.genBusinessTagForNode(dwsFlow);
//        return Message.messageToResponse(Message.ok().data("flow", DSSFlow));
//    }


    @POST
    @Path("/deleteFlow")
//    @ProjectPrivChecker
    public Response deleteFlow(@Context HttpServletRequest req, JsonNode json) throws DSSErrorException {
        Long flowID = json.get("id").getLongValue();
        boolean sure = json.get("sure") != null && json.get("sure").getBooleanValue();
        // TODO: 2019/6/13  projectVersionID???????????????
        //state???true?????????????????????
        if (flowService.getFlowByID(flowID).getState() && !sure) {
            return Message.messageToResponse(Message.ok().data("warmMsg", "???????????????????????????????????????????????????????????????????????????????????????????????????"));
        }
//        ioManager.checkeIsExecuting(projectVersionID);
        flowService.batchDeleteFlow(Arrays.asList(flowID));
        return Message.messageToResponse(Message.ok());
    }

    /**
     * ????????????????????????????????????Json???????????????????????????????????????Json??????
     * @param req
     * @param json
     * @return
     * @throws DSSErrorException
     * @throws IOException
     */

    @POST
    @Path("/saveFlow")
//    @ProjectPrivChecker
    public Response saveFlow(@Context HttpServletRequest req, JsonNode json) throws DSSErrorException, IOException {
        Long flowID = json.get("id").getLongValue();
        String jsonFlow = json.get("json").getTextValue();
        String workspaceName = json.get("workspaceName").getTextValue();
        String projectName = json.get("projectName").getTextValue();
        String userName = SecurityFilter.getLoginUsername(req);
        //String comment = json.get("comment") == null?"????????????":json.get("comment").getTextValue();
        //????????????comment????????????,????????????comment
        String comment = null;
//        ioManager.checkeIsExecuting(projectVersionID);
        // TODO: 2020/6/9 ??????cs???bml?????????
        String version = null;
        String newFlowEditLock = null;
        synchronized (DSSWorkFlowConstant.saveFlowLock.intern(flowID)) {
            version = flowService.saveFlow(flowID, jsonFlow, comment, userName, workspaceName, projectName);
        }
        return Message.messageToResponse(Message.ok().data("flowVersion", version).data("flowEditLock", newFlowEditLock));
    }

}
