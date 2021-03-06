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

package com.webank.wedatasphere.dss.workflow.common.entity;

import java.util.Date;


public class DWSFlowVersion implements FlowVersion, Comparable<DWSFlowVersion> {
    private Long id;
    private Long flowID;
    private String source;
    private String jsonPath;
    private String comment;
    private Date updateTime;
    private String version;
    private String bmlVersion;
    private DWSFlowPublishHistory publishHistory;
    private String json;
    private String updator;
    private Boolean isNotPublished;  //true 未发过版，false已经过版
    private Long projectVersionID;
    private Long oldFlowID;
    /**
     * 工作流是否被锁
     */
    private Boolean isFlowEditLockExist;

    public Boolean getFlowEditLockExist() {
        return isFlowEditLockExist;
    }

    public void setFlowEditLockExist(Boolean flowEditLockExist) {
        isFlowEditLockExist = flowEditLockExist;
    }

    /**
     * 工作流编辑锁，打开自动获取，超时自动释放
     */
    private String flowEditLock;

    public String getFlowEditLock() {
        return flowEditLock;
    }

    public void setFlowEditLock(String flowEditLock) {
        this.flowEditLock = flowEditLock;
    }

    public Long getOldFlowID() {
        return oldFlowID;
    }

    public void setOldFlowID(Long oldFlowID) {
        this.oldFlowID = oldFlowID;
    }

    public Long getProjectVersionID() {
        return projectVersionID;
    }

    public void setProjectVersionID(Long projectVersionID) {
        this.projectVersionID = projectVersionID;
    }

    public Boolean getNotPublished() {
        return isNotPublished;
    }

    public void setNotPublished(Boolean notPublished) {
        isNotPublished = notPublished;
    }

    public String getUpdator() {
        return updator;
    }

    public void setUpdator(String updator) {
        this.updator = updator;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFlowID() {
        return flowID;
    }

    public void setFlowID(Long flowID) {
        this.flowID = flowID;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    @Override
    public String getComment() {
        return comment;
    }

    @Override
    public void setComment(String comment) {
        this.comment = comment;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public DWSFlowPublishHistory getPublishHistory() {
        return publishHistory;
    }

    public void setPublishHistory(DWSFlowPublishHistory publishHistory) {
        this.publishHistory = publishHistory;
    }

    public String getBmlVersion() {
        return bmlVersion;
    }

    public void setBmlVersion(String bmlVersion) {
        this.bmlVersion = bmlVersion;
    }

    @Override
    public int compareTo(DWSFlowVersion o) {
        Integer v1 = Integer.valueOf(this.version.substring(1, version.length()));
        Integer v2 = Integer.valueOf(o.version.substring(1, o.version.length()));
        if (v1.equals(v2) && this.bmlVersion != null && o.bmlVersion != null) {
            Integer bV1 = Integer.valueOf(this.bmlVersion.substring(1, bmlVersion.length()));
            Integer bV2 = Integer.valueOf(o.bmlVersion.substring(1, bmlVersion.length()));
            return bV2 - bV1;
        } else {
            return v2 - v1;
        }
    }
}
