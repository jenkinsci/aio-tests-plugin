package com.navarambh.aiotests.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.navarambh.aiotests.postbuildsteps.AIOTestsResultRecorder;
import hudson.model.Run;
import hudson.util.Secret;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONException;
import kong.unirest.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

public class AIOCloudClient {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON_UNICODE = "application/json;charset=UTF-8";
    private static final String AIO_TESTS_HOST = "https://tcms.aiojiraapps.com/aio-tcms";
    private static final String API_VERSION = "/api/v1";
    private static final String SERVER_API_VERSION = "/rest/aio-tcms-api/1.0";
    private static final String AIO_SCHEME = "AioAuth";
    private static final String IMPORT_RESULTS_FILE = "/project/{jiraProjectId}/testcycle/{testCycleId}/import/results";
    private static final String IMPORT_RESULTS_FILE_BATCH = "/project/{jiraProjectId}/testcycle/{testCycleId}/import/results/batch";
    private static final String CREATE_CYCLE = "/project/{jiraProjectId}/testcycle/detail";
    private static final String SEARCH_CYCLE = "/project/{jiraProjectId}/testcycle/search";
    private static final String GET_OR_CREATE_CYCLE_FOLDER = "/project/{jiraProjectId}/testcycle/folder/hierarchy";
    private String projectId;
    private Secret apiKey;
    private String jiraServerUrl;
    private Secret jiraPassword;
    private String jiraUsername;

    static {
        Unirest.config().socketTimeout(150*1000);
    }

    public AIOCloudClient(String projectId, Secret apiKey) {
        this.projectId = projectId;
        this.apiKey = apiKey;
    }

    public AIOCloudClient(String projectId, String jiraServerUrl, String username, Secret jiraPassword) {
        this.projectId = projectId;
        this.jiraServerUrl = jiraServerUrl;
        if(this.jiraServerUrl.endsWith("/")){
            this.jiraServerUrl = this.jiraServerUrl.substring(0, jiraServerUrl.length() - 1);
        }
        this.jiraUsername = username;
        this.jiraPassword = jiraPassword;
    }

    public void importResults(String frameworkType, boolean createNewCycle, String testCycleId,
                              boolean addCase, boolean createCase, boolean bddForceUpdateCase,
                              boolean createNewRun, boolean forceUpdateCase, boolean isBatch, boolean hideDetails,
                              List<File> resultFiles, Run<?, ?> run, AIOTestsResultRecorder.NewCycle newCycleInfo, PrintStream logger, boolean createIfAbsent) {
        String cycleKey;
        logger.println("Result files " + resultFiles.size());
        if (createNewCycle) {
            logger.println("Creating new cycle with prefix " + testCycleId);
            if (newCycleInfo != null) {
                logger.println((StringUtils.isNotBlank(newCycleInfo.getCycleFolder()) ? " in folder " + newCycleInfo.getCycleFolder() : "") + " ....");
            }
            Number folderId = this.createOrGetFolder(newCycleInfo);
            HttpResponse<String> response = this.createCycle(testCycleId, run, newCycleInfo == null ? null : newCycleInfo.getCycleTasks(), folderId, true);
            try {
                JSONObject responseBody = this.validateResponse(response, "Cycle creation");
                cycleKey = responseBody.getString("key");
                logger.println("Cycle created successfully " + cycleKey);
            } catch (Exception e) {
                logger.println("Error in cycle creation " + e.getMessage());
                return;
            }
        } else if (createIfAbsent) {
            logger.println("Looking for cycle with title :  " + testCycleId);
            HttpResponse<String> response = this.findCycleFromName(testCycleId);
            if (response != null && response.isSuccess()) {
                JSONObject responseBody = this.validateResponse(response, "Fetching cycle");
                JSONArray items = responseBody.getJSONArray("items");
                if (items != null && items.length() > 0) {
                    cycleKey = items.getJSONObject(0).getString("key");
                } else {
                    logger.println("Cycle " + testCycleId + " not found");
                    logger.println("Creating new cycle with prefix " + testCycleId);
                    HttpResponse<String> createCycleResponse = this.createCycle(testCycleId, run, null, null, false);
                    try {
                        JSONObject createCycleResponseBody = this.validateResponse(createCycleResponse, "Cycle creation");
                        cycleKey = createCycleResponseBody.getString("key");
                        logger.println("Cycle created successfully " + cycleKey);
                    } catch (Exception e) {
                        logger.println("Error in cycle creation " + e.getMessage());
                        return;
                    }

                }
            } else {
                logger.println("Error in cycle search " + response);
                return;
            }

        } else {
            cycleKey = testCycleId;
        }
        logger.println("Updating results for " + cycleKey);
        if (isBatch) {
            logger.println("Batch results can be viewed in Batches tab of " + cycleKey);
        }
        for (File resultFile : resultFiles) {
            logger.print(StringUtils.rightPad("", 5, "*"));
            logger.print("File Name: " + resultFile.getName());
            logger.println(StringUtils.rightPad("", 5, "*"));
            HttpResponse<String> response = this.importResults(cycleKey, frameworkType, addCase, createCase, bddForceUpdateCase, createNewRun, resultFile, forceUpdateCase, isBatch);
            JSONObject responseBody = this.validateResponse(response, "Import results");
            logResults(frameworkType, responseBody, hideDetails, logger, isBatch);
        }
    }

    private Number createOrGetFolder(AIOTestsResultRecorder.NewCycle newCycleInfo) {
        if(newCycleInfo != null && StringUtils.isNotBlank(newCycleInfo.getCycleFolder())) {
            JsonObject jsonObject = new JsonObject();
            JsonArray j = new JsonArray();
            String[] folderNames = newCycleInfo.getCycleFolder().split(",");
            for (String folderName : folderNames) {
                if (StringUtils.isNotBlank(folderName)) {
                    j.add(folderName.trim());
                }
            }
            jsonObject.add("folderHierarchy", j);

            HttpResponse response = this.putHttpRequest(GET_OR_CREATE_CYCLE_FOLDER)
                    .routeParam("jiraProjectId", this.projectId)
                    .body(jsonObject).asString();
            if(response != null && response.isSuccess()) {
                JSONObject responseBody = this.validateResponse(response, "Fetching or creating folder");
                return responseBody.getNumber("ID");
            }
        }
        return null;
    }

    private void logResults(String frameworkType, JSONObject responseBody, boolean hideDetails, PrintStream logger, boolean isBatch) {
        final int keyColumnLength = 80;
        final int dividerLength = 100;
        if(responseBody != null) {
            if(isBatch){
                logger.println(StringUtils.rightPad("Batch Id:", 30) + responseBody.getString("batchId"));
                String fileSize = responseBody.getString("size");
                if(StringUtils.isNotBlank(fileSize)) {
                    BigDecimal a = new BigDecimal(fileSize);
                    BigDecimal roundOff = a.divide(new BigDecimal(1000)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
                    logger.println(StringUtils.rightPad("File Size:", 30) + roundOff + " kb");
                } else {
                    logger.println(StringUtils.rightPad("File Size:", 30) + "0");
                }
                if(responseBody.get("errorMsg") != null){
                    logger.println(StringUtils.rightPad("Error Msg:", 30) + responseBody.get("errorMsg"));
                }
            }
            else {
                logger.println(StringUtils.rightPad("Status:", 30) + responseBody.getString("status"));
                logger.println(StringUtils.rightPad("Total Runs:", 30) + responseBody.getString("requestCount"));
                logger.println(StringUtils.rightPad("Successfully updated:", 30) + responseBody.getString("successCount"));
                logger.println(StringUtils.rightPad("Errors:", 30) + responseBody.getString("errorCount"));
                if (!hideDetails) {
                    logger.println(StringUtils.rightPad("", dividerLength, "-"));
                    logger.println(StringUtils.rightPad("Key", keyColumnLength) + (frameworkType.equalsIgnoreCase("cucumber") ? "" : "Run Status"));
                    logger.println(StringUtils.rightPad("", dividerLength, "-"));
                    if (!responseBody.getString("errorCount").equals("0")) {
                        JSONObject errors = responseBody.getJSONObject("errors");
                        Iterator<String> caseIterator = errors.keys();
                        while (caseIterator.hasNext()) {
                            String key = caseIterator.next();
                            logger.println(StringUtils.rightPad(StringUtils.abbreviate(key, keyColumnLength), keyColumnLength)
                                    + errors.getJSONObject(key).getString("message"));
                        }
                    }
                    if (this.getKeyValue(responseBody, "processedData", logger) != null) {
                        JSONObject processedData = responseBody.getJSONObject("processedData");
                        Iterator<String> caseIterator = processedData.keys();
                        while (caseIterator.hasNext()) {
                            String key = caseIterator.next();
                            logger.println(StringUtils.rightPad(StringUtils.abbreviate(key, keyColumnLength), keyColumnLength)
                                    + (frameworkType.equalsIgnoreCase("cucumber") || frameworkType.equalsIgnoreCase("newman") ?
                                    "" : processedData.getJSONObject(key).getString("status")));
                        }
                    }
                    logger.println(StringUtils.rightPad("", dividerLength, "-"));
                }
            }
        }
    }

    private Object getKeyValue(JSONObject responseBody, String key, PrintStream logger){
        try {
            return responseBody.get(key);
        } catch (JSONException e) {
            logger.println("Property not found in response " + key);
            return null;
        }
    }

    private JSONObject validateResponse(HttpResponse<String> response, String task) {
        if(response.getStatus() == 401) {
            throw new AIOTestsAuthorizationException();
        } else {
            if(response.getStatus() == 200) {
                return new JsonNode(response.getBody()).getObject();
            } else {
                throw new AIOTestsException(task + " failed with error - \"" + response.getBody() + "\"");
            }
        }
    }

    private HttpResponse<String> findCycleFromName(String cycleName){
        JsonObject jsonObject = new JsonObject();
        JsonObject titleObject = new JsonObject();
        titleObject.addProperty("value", cycleName);
        titleObject.addProperty("comparisonType", "EXACT_MATCH");
        jsonObject.add("title", titleObject);
        return this.getHttpRequest(SEARCH_CYCLE, true)
                .routeParam("jiraProjectId", this.projectId)
                .body(jsonObject).asString();
    }

    private HttpResponse<String> createCycle(String cyclePrefix, Run run, String cycleTasks, Number folderID, boolean useTime) {
        String objective = "Created by automation run " + run.toString() ;
        String cycleTitle = cyclePrefix;
        if(useTime){
            cycleTitle += " - " + run.getTime();
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("title",cycleTitle);
        jsonObject.addProperty("objective", objective);
        if(folderID != null) {
            JsonObject folderObject = new JsonObject();
            folderObject.addProperty("ID",folderID);
            jsonObject.add("folder", folderObject);
        }
        if(StringUtils.isNotBlank(cycleTasks)) {
            JsonArray j = new JsonArray();
            String[] taskIds = cycleTasks.split(",");
            for (String taskId : taskIds) {
                if (StringUtils.isNotBlank(taskId)) {
                    j.add(taskId.trim());
                }
            }
            jsonObject.add("jiraTaskIDs", j);
        }

        return this.getHttpRequest(CREATE_CYCLE, true)
                .routeParam("jiraProjectId", this.projectId)
                .body(jsonObject).asString();
    }

    private HttpResponse<String> importResults(String testCycleId, String frameworkType,
                                               boolean addCase, boolean createCase, boolean bddForceUpdateCase, boolean createNewRun, File f, boolean forceUpdateCase, boolean isBatch) {
        return this.getHttpRequest(isBatch ? IMPORT_RESULTS_FILE_BATCH : IMPORT_RESULTS_FILE, false)
                .queryString("type",frameworkType)
                .routeParam("jiraProjectId", this.projectId)
                .routeParam("testCycleId", testCycleId)
                .field("file", f)
                .field("addCaseToCycle", Boolean.toString(addCase))
                .field("createCase", Boolean.toString(createCase))
                .field("createNewRun", Boolean.toString(createNewRun))
                .field("bddForceUpdateCase", Boolean.toString(bddForceUpdateCase))
                .field("forceUpdateCase", Boolean.toString(forceUpdateCase))
                .asString();
    }

    private HttpRequestWithBody putHttpRequest(String url) {
        HttpRequestWithBody requestWithBody = Unirest.put(getAIOEndpoint(url));
        return getHttpRequestWithBody(true, requestWithBody);
    }

    private HttpRequestWithBody getHttpRequestWithBody(Boolean isJson, HttpRequestWithBody requestWithBody) {
        if(isJson) {   requestWithBody = requestWithBody.header(CONTENT_TYPE_HEADER, APPLICATION_JSON_UNICODE); }

        if(this.apiKey != null){
            return requestWithBody
                    .header("Authorization", getAuthKey(this.apiKey.getPlainText()));
        }
        if(this.jiraServerUrl != null){
            return requestWithBody.basicAuth(this.jiraUsername, this.jiraPassword.getPlainText());
        }
        throw new AIOTestsAuthorizationException();
    }

    private HttpRequestWithBody getHttpRequest(String url, Boolean isJson) {
        HttpRequestWithBody requestWithBody = Unirest.post(getAIOEndpoint(url));
        return getHttpRequestWithBody(isJson, requestWithBody);
    }

    private String getAIOEndpoint(String url) {
        String base = this.apiKey != null? AIO_TESTS_HOST + API_VERSION : this.jiraServerUrl + SERVER_API_VERSION ;
        return base + url;
    }

    private static String getAuthKey(String apiKey) {
        return AIO_SCHEME + " " + apiKey;
    }

}