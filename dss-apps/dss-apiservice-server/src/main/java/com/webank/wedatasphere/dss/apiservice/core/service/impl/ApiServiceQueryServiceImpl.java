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
package com.webank.wedatasphere.dss.apiservice.core.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.webank.wedatasphere.dss.apiservice.core.bo.ApiServiceJob;
import com.webank.wedatasphere.dss.apiservice.core.bo.ApiServiceToken;
import com.webank.wedatasphere.dss.apiservice.core.bo.LinkisExecuteResult;
import com.webank.wedatasphere.dss.apiservice.core.config.ApiServiceConfiguration;
import com.webank.wedatasphere.dss.apiservice.core.constant.ParamType;
import com.webank.wedatasphere.dss.apiservice.core.constant.ParamTypeEnum;
import com.webank.wedatasphere.dss.apiservice.core.constant.RequireEnum;
import com.webank.wedatasphere.dss.apiservice.core.dao.*;
import com.webank.wedatasphere.dss.apiservice.core.exception.ApiExecuteException;
import com.webank.wedatasphere.dss.apiservice.core.exception.ApiServiceQueryException;
import com.webank.wedatasphere.dss.apiservice.core.execute.ApiServiceExecuteJob;
import com.webank.wedatasphere.dss.apiservice.core.execute.DefaultApiServiceJob;
import com.webank.wedatasphere.dss.apiservice.core.execute.ExecuteCodeHelper;
import com.webank.wedatasphere.dss.apiservice.core.execute.LinkisJobSubmit;
import com.webank.wedatasphere.dss.apiservice.core.jdbc.DatasourceService;
import com.webank.wedatasphere.dss.apiservice.core.service.ApiService;
import com.webank.wedatasphere.dss.apiservice.core.util.DateUtil;
import com.webank.wedatasphere.dss.apiservice.core.util.SQLCheckUtil;
import com.webank.wedatasphere.dss.apiservice.core.vo.*;
//import com.webank.wedatasphere.dss.oneservice.core.jdbc.JdbcUtil;
import com.webank.wedatasphere.dss.apiservice.core.exception.ApiServiceRuntimeException;
import com.webank.wedatasphere.dss.apiservice.core.service.ApiServiceQueryService;
import com.webank.wedatasphere.dss.apiservice.core.util.AssertUtil;
import com.webank.wedatasphere.dss.apiservice.core.util.ModelMapperUtil;
//import com.webank.wedatasphere.dss.oneservice.core.vo.*;
import com.webank.wedatasphere.dss.apiservice.core.vo.ApiServiceVo;
import com.webank.wedatasphere.linkis.bml.client.BmlClient;
import com.webank.wedatasphere.linkis.bml.client.BmlClientFactory;
import com.webank.wedatasphere.linkis.bml.protocol.BmlDownloadResponse;
import com.webank.wedatasphere.linkis.common.io.FsPath;
import com.webank.wedatasphere.linkis.storage.source.FileSource;
import com.webank.wedatasphere.linkis.storage.source.FileSource$;
import com.webank.wedatasphere.linkis.ujes.client.UJESClient;
import com.webank.wedatasphere.linkis.ujes.client.response.JobExecuteResult;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedCaseInsensitiveMap;
import scala.Tuple3;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;


@Service
public class ApiServiceQueryServiceImpl implements ApiServiceQueryService {
    private static final Logger LOG = LoggerFactory.getLogger(ApiServiceQueryServiceImpl.class);


    Map<String, ApiServiceJob> runJobs = new HashMap<>();

    /**
     * key:resourceId+version
     * value:bml
     */
    private static Cache<String, Pair<Object, ArrayList<String[]>>> bmlCache = CacheBuilder.newBuilder()
            .expireAfterWrite(6, TimeUnit.HOURS)
            .maximumSize(10000)
            .removalListener((notification) -> {
                if (notification.getCause() == RemovalCause.SIZE) {
                    LOG.warn("bml???????????????????????????key:" + notification.getKey());
                }
            })
            .build();

    /**
     * key:resourceId+version
     * value:configParam
     */
    private static Cache<String, Map<String, String>> configParamCache = CacheBuilder.newBuilder()
            .expireAfterWrite(6, TimeUnit.HOURS)
            .maximumSize(10000)
            .removalListener((notification) -> {
                if (notification.getCause() == RemovalCause.SIZE) {
                    LOG.warn("configParamCache???????????????????????????key:" + notification.getKey());
                }
            })
            .build();

    /**
     * key:datasourceMap
     * value:jdbc????????????
     */
    private static Cache<Map<String, Object>, Tuple3> datasourceCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(2000)
            .removalListener((notification) -> {
                if (notification.getCause() == RemovalCause.SIZE) {
                    LOG.warn("datasource???????????????????????????key:" + notification.getKey());
                }
            })
            .build();

    @Autowired
    private ApiServiceDao apiServiceDao;

    @Autowired
    private ApiServiceParamDao apiServiceParamDao;

    @Autowired
    private DatasourceService datasourceService;

    @Autowired
    private ApiServiceVersionDao apiServiceVersionDao;

    @Autowired
    private ApiServiceTokenManagerDao apiServiceTokenManagerDao;


    @Autowired
    private ApiService apiService;


    @Autowired
    private  ApiServiceAccessDao apiServiceAccessDao;

    /**
     * Bml client
     */
    private BmlClient client;

    @PostConstruct
    public void init() {
        LOG.info("build client start ======");
        client = BmlClientFactory.createBmlClient();
        LOG.info("build client end =======");
    }

    @Override
    public LinkisExecuteResult query(String path,
                                     Map<String, Object> reqParams,
                                     String moduleName,
                                     String httpMethod,
                                     ApiServiceToken tokenDetail,
                                     String loginUser) {
        // ??????path??????resourceId???version

        // ??????metadata

        // ????????????
        //path ????????????
        ApiServiceVo apiServiceVo = apiServiceDao.queryByPath(path);
        if(null == apiServiceVo){
            throw new ApiServiceRuntimeException("?????????????????????????????????????????????");
        }
        if(!apiService.checkUserWorkspace(loginUser,apiServiceVo.getWorkspaceId().intValue())){
            throw new ApiServiceRuntimeException("????????????????????????????????????");
        }
        if(!apiServiceVo.getId().equals(tokenDetail.getApiServiceId())){
            throw new ApiServiceRuntimeException("??????token?????????ID????????????");
        }

        ApiVersionVo maxApiVersionVo =apiService.getMaxVersion(apiServiceVo.getId());

        AssertUtil.notNull(apiServiceVo, "??????????????????path=" + path);
        AssertUtil.isTrue(StringUtils.equals(httpMethod, apiServiceVo.getMethod().toUpperCase()),
                "??????????????????" + httpMethod + "???????????????" + apiServiceVo.getMethod() + "??????");
        AssertUtil.isTrue(1 == apiServiceVo.getStatus(), "???????????????");
        AssertUtil.notNull(maxApiVersionVo, "???????????????????????????path=" + path);

        try {
            Pair<Object, ArrayList<String[]>> collect = queryBml(apiServiceVo.getCreator(), maxApiVersionVo.getBmlResourceId(),
                    maxApiVersionVo.getBmlVersion(), apiServiceVo.getScriptPath());
            String executeCode = collect.getSecond().get(0)[0];

            Map<String, Object> variable = (Map) ((Map) collect.getFirst()).get("variable");

            //???????????????????????????????????????
            Map<String, String> paramTypes = queryConfigParam(apiServiceVo.getId(), maxApiVersionVo.getVersion());
            if (variable != null) {
                variable.forEach((k, v) -> {
                    if (!reqParams.containsKey(k)) {
                        if (ParamType.number.equals(paramTypes.get(k))) {
                            reqParams.put(k, Integer.valueOf(v.toString()));
                        } else {
                            reqParams.put(k, v);
                        }
                    }
                });
            }

            // ?????????????????????????????????????????????token
            for(String k: reqParams.keySet()){
                if(!k.equals(ApiServiceConfiguration.API_SERVICE_TOKEN_KEY.getValue())
                   && SQLCheckUtil.doParamInjectionCheck(reqParams.get(k).toString())) {
                    // ????????????????????????null
                    LOG.warn("????????????????????????????????????{}", reqParams.get(k).toString());
                    return null;
                }
            }

            //??????????????????????????????????????????????????????
            reqParams.forEach((k,v) ->{
                if(ParamType.array.equals(paramTypes.get(k))){
                    String sourceStr = v.toString();
                    String targetStr =sourceStr;
                    sourceStr= sourceStr.replaceAll("(\n\r|\r\n|\r|\n)", ",");
                    sourceStr= sourceStr.replaceAll(",,", ",");

                    if(!sourceStr.contains("\'")){
                        targetStr= Arrays.stream(sourceStr.split(",")).map(s -> "\'" + s + "\'").collect(Collectors.joining(","));
                        reqParams.put(k, targetStr);
                    }else {
                        reqParams.put(k, sourceStr);
                    }
                }
            });


//            AssertUtil.isTrue(MapUtils.isNotEmpty((Map) collect.getKey()), "?????????????????????");


            ApiServiceExecuteJob job = new DefaultApiServiceJob();
            //sql???????????????scala??????
            job.setCode(ExecuteCodeHelper.packageCodeToExecute(executeCode, maxApiVersionVo.getMetadataInfo()));
            job.setEngineType(apiServiceVo.getType());
            job.setRunType("scala");
            //???????????????????????????????????????????????????????????????????????????????????????
            //???????????????????????????????????????????????????
            job.setUser(loginUser);

            job.setParams(null);
            job.setRuntimeParams(reqParams);
            job.setScriptePath(apiServiceVo.getScriptPath());
            UJESClient ujesClient = LinkisJobSubmit.getClient(paramTypes);

            //????????????Api???????????????
            ApiAccessVo apiAccessVo = new ApiAccessVo();
            apiAccessVo.setUser(loginUser);
            apiAccessVo.setApiPublisher(apiServiceVo.getCreator());
            apiAccessVo.setApiServiceName(apiServiceVo.getName());
            apiAccessVo.setApiServiceId(apiServiceVo.getId());
            apiAccessVo.setApiServiceVersionId(maxApiVersionVo.getId());
            apiAccessVo.setProxyUser(job.getUser());
            apiAccessVo.setAccessTime(DateUtil.getNow());
            apiServiceAccessDao.addAccessRecord(apiAccessVo);


            JobExecuteResult jobExecuteResult = LinkisJobSubmit.execute(job,ujesClient);

            //????????????????????????????????????????????????????????????????????????????????????????????????
            ApiServiceJob apiServiceJob = new ApiServiceJob();
            apiServiceJob.setSubmitUser(loginUser);
            apiServiceJob.setProxyUser(job.getUser());
            apiServiceJob.setJobExecuteResult(jobExecuteResult);
            runJobs.put(jobExecuteResult.getTaskID(),apiServiceJob);


            LinkisExecuteResult linkisExecuteResult = new LinkisExecuteResult(jobExecuteResult.getTaskID(), jobExecuteResult.getExecID());
            return linkisExecuteResult;
        } catch (IOException e) {
            throw new ApiServiceRuntimeException(e.getMessage(), e);
        }
    }




    @Override
    public ApiServiceVo queryByVersionId(String userName,Long versionId) throws ApiServiceQueryException {
        ApiVersionVo apiVersionVo =   apiServiceVersionDao.queryApiVersionByVersionId(versionId);
        ApiServiceVo apiServiceVo = apiServiceDao.queryById(apiVersionVo.getApiId());
        //??????????????????????????????
        List<TokenManagerVo> userTokenManagerVos = apiServiceTokenManagerDao.queryByApplyUserAndVersionId(userName,versionId);
        if(userTokenManagerVos.size()>0) {
            try {
                Pair<Object, ArrayList<String[]>> collect = queryBml(apiServiceVo.getCreator(), apiVersionVo.getBmlResourceId(),
                        apiVersionVo.getBmlVersion(), apiServiceVo.getScriptPath());
                String executeCode = collect.getSecond().get(0)[0];
                apiServiceVo.setContent(executeCode);

            } catch (IOException e) {
                throw new ApiServiceQueryException(800002, "??????????????????API????????????");
            }
            apiServiceVo.setScriptPath(apiVersionVo.getSource());
            return apiServiceVo;
        }else {

            throw new ApiServiceQueryException(800003, "??????????????????????????????API???????????????????????????");
        }
    }

    @Override
    public List<QueryParamVo> queryParamList(String scriptPath, Long versionId) {
        ApiVersionVo targetApiVersionVo = apiServiceVersionDao.queryApiVersionByVersionId(versionId);

        ApiServiceVo apiServiceVo=apiServiceDao.queryById(targetApiVersionVo.getApiId());

        AssertUtil.notNull(apiServiceVo, "??????????????????path=" + scriptPath);

        AssertUtil.notNull(targetApiVersionVo, "??????????????????????????????path=" + scriptPath+",version:"+versionId);

        // todo~???
        List<ParamVo> paramVoList = apiServiceParamDao.queryByVersionId(targetApiVersionVo.getId());


        List<QueryParamVo> queryParamVoList = new ArrayList<>();

        Map<String, ParamVo> paramMap = paramVoList.stream()
                .collect(Collectors.toMap(ParamVo::getName, k -> k, (k, v) -> k));
        Map<String, Object> variableMap = getVariable(apiServiceVo,versionId);
        paramMap.keySet()
                .forEach(keyItem -> {
                    ParamVo paramVo = paramMap.get(keyItem);
                    QueryParamVo queryParamVo = ModelMapperUtil.strictMap(paramVo, QueryParamVo.class);
                    queryParamVo.setTestValue(variableMap.containsKey(keyItem) ? variableMap.get(keyItem).toString() : "");
                    queryParamVo.setRequireStr(RequireEnum.getEnum(paramVo.getRequired()).getName());
                    queryParamVo.setType(paramVo.getType());

                    queryParamVoList.add(queryParamVo);
                });

        return queryParamVoList;
    }

    @Override
    public List<ApiVersionVo> queryApiVersionById(Long serviceId) {
        List<ApiVersionVo> apiVersionVoList = apiServiceVersionDao.queryApiVersionByApiServiceId(serviceId);
        return apiVersionVoList;
    }

    private Map<String, Object> getVariable(ApiServiceVo apiServiceVo,Long versionId) {
        Map<String, Object> variableMap = null;
        ApiVersionVo apiVersionVo = apiServiceVersionDao.queryApiVersionByVersionId(versionId);
        if(null != apiServiceVo) {
            try {
                Pair<Object, ArrayList<String[]>> collect = queryBml(apiServiceVo.getCreator(), apiVersionVo.getBmlResourceId(),
                        apiVersionVo.getBmlVersion(), apiServiceVo.getScriptPath());

                variableMap = (Map) ((Map) collect.getFirst()).get("variable");
            } catch (IOException e) {
                throw new ApiServiceRuntimeException(e.getMessage(), e);
            }
        }
        return null == variableMap ? Collections.EMPTY_MAP : variableMap;
    }

    private Pair<Object, ArrayList<String[]>> queryBml(String userName, String resourceId, String version,
                                                       String scriptPath) throws IOException {
        String key = String.join("-", resourceId, version);
        Pair<Object, ArrayList<String[]>> collect = bmlCache.getIfPresent(key);

        if (collect == null) {
            synchronized (this) {
                collect = bmlCache.getIfPresent(key);
                if (collect == null) {
                    BmlDownloadResponse resource;
                    if (version == null) {
                        resource = client.downloadResource(userName, resourceId, null);
                    } else {
                        resource = client.downloadResource(userName, resourceId, version);
                    }

                    AssertUtil.isTrue(resource.isSuccess(), "??????bml??????");

                    InputStream inputStream = resource.inputStream();

                    try (FileSource fileSource = FileSource$.MODULE$.create(new FsPath(scriptPath), inputStream)) {
                        //todo   ?????????????????????
                        collect = fileSource.collect()[0];
                        bmlCache.put(key, collect);
                    }
                }
            }
        }


        return collect;
    }

    private Map<String, String> queryConfigParam(long apiId, String version) {
        String key = String.join("-", apiId + "", version);
        Map<String, String> collect = configParamCache.getIfPresent(key);

        if (collect == null) {
            synchronized (this) {
                collect = configParamCache.getIfPresent(key);
                if (collect == null) {
                    List<ApiVersionVo> apiVersionVoList = apiServiceVersionDao.queryApiVersionByApiServiceId(apiId);
                    ApiVersionVo apiVersionVo = apiVersionVoList.stream().filter(apiVersionVoTmp -> apiVersionVoTmp.getVersion().equals(version)).findFirst().orElse(null);

                    collect = apiServiceParamDao.queryByVersionId(apiVersionVo.getId())
                            .stream()
                            .collect(toMap(ParamVo::getName, ParamVo::getType));
                    configParamCache.put(key, collect);
                }
            }
        }

        return collect;
    }




//    private Tuple3 getDatasourceInfo(final Map<String, Object> datasourceMap) {
//        Tuple3 tuple3 = datasourceCache.getIfPresent(datasourceMap);
//
//        if (tuple3 == null) {
//            synchronized (this) {
//                tuple3 = datasourceCache.getIfPresent(datasourceMap);
//                if (tuple3 == null) {
//                    tuple3 = JdbcUtil.getDatasourceInfo(datasourceMap);
//                    datasourceCache.put(datasourceMap, tuple3);
//                }
//            }
//        }
//
//        return tuple3;
//    }

//    private List<Map<String, Object>> executeJob(String executeCode,
//                                                 Object datasourceMap, Map<String, Object> params) {
//
////        Tuple3 tuple3 = getDatasourceInfo((Map<String, Object>) datasourceMap);
////        final String jdbcUrl = tuple3._1().toString();
////        final String username = tuple3._2().toString();
////        final String password = tuple3._3().toString();
//
////        NamedParameterJdbcTemplate namedParameterJdbcTemplate = datasourceService.getNamedParameterJdbcTemplate(jdbcUrl, username, password);
//
//        String namedSql = genNamedSql(executeCode, params);
//
////        return namedParameterJdbcTemplate.query(namedSql, new MapSqlParameterSource(params), new ColumnAliasMapRowMapper());
//
//    }

    private static String genNamedSql(String executeCode, Map<String, Object> params) {
        // ???????????????????????????namedSql
        if (MapUtils.isEmpty(params)) {
            return executeCode;
        }

        for (String paramName : params.keySet()) {
            for (String $name : new String[]{"'${" + paramName + "}'", "${" + paramName + "}", "\"${" + paramName + "}\""}) {
                if (executeCode.contains($name)) {
                    executeCode = StringUtils.replace(executeCode, $name, ":" + paramName);
                    break;
                }
            }
        }

        return executeCode;
    }


    public static class ColumnAliasMapRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            Map<String, Object> mapOfColValues = createColumnMap(columnCount);
            Map<String, Integer> mapOfColSuffix = new LinkedCaseInsensitiveMap<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                String key = getColumnKey(JdbcUtils.lookupColumnName(rsmd, i));
                if (mapOfColValues.containsKey(key)) {
                    if (!mapOfColSuffix.containsKey(key)) {
                        mapOfColSuffix.put(key, 1);
                    } else {
                        mapOfColSuffix.put(key, mapOfColSuffix.get(key) + 1);
                    }

                    key = key + "_" + mapOfColSuffix.get(key);
                }

                Object obj = getColumnValue(rs, i);
                mapOfColValues.put(key, obj);
            }
            return mapOfColValues;
        }

        protected Map<String, Object> createColumnMap(int columnCount) {
            return new LinkedCaseInsensitiveMap<>(columnCount);
        }

        protected String getColumnKey(String columnName) {
            return columnName;
        }

        protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
            return JdbcUtils.getResultSetValue(rs, index);
        }

    }

    @Override
    public ApiServiceJob getJobByTaskId(String taskId){
        ApiServiceJob apiServiceJob=runJobs.get(taskId);
        return apiServiceJob;
    }


    private static String getRunTypeFromScriptsPath(String scriptsPath) {

        String res = "sql";
        String fileFlag = scriptsPath.substring(scriptsPath.lastIndexOf(".") + 1);
        switch (fileFlag) {
            case "sh":
                res = "shell";
                break;
            case "py":
                res= "pyspark";
                break;
            default:
                res = fileFlag;
                break;
        }
        return res;

    }
}
