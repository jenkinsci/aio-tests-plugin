package com.navarambh.aiotests.utils;

import com.google.gson.JsonObject;
import hudson.model.Run;
import hudson.util.Secret;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONException;
import kong.unirest.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

public class AIOCloudClient {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON_UNICODE = "application/json;charset=UTF-8";
    private static final String AIO_TESTS_HOST = "https://tcms.aioreports.com/aio-tcms";
    private static final String API_VERSION = "/api/v1";
    private static final String SERVER_API_VERSION = "/rest/aio-tcms-api/1.0";
    private static final String AIO_SCHEME = "AioAuth";
    private static final String IMPORT_RESULTS_FILE = "/project/{jiraProjectId}/testcycle/{testCycleId}/import/results";
    private static final String CREATE_CYCLE = "/project/{jiraProjectId}/testcycle/detail";
    private String projectId;
    private Secret apiKey;
    private String jiraServerUrl;
    private Secret jiraPassword;
    private String jiraUsername;

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
                                              boolean hideDetails,
                                              List<File> resultFiles, Run<?, ?> run, PrintStream logger) {
        String cycleKey;
        logger.println("Result files " + resultFiles.size());
        if(createNewCycle) {
            logger.println("Creating new cycle with prefix " + testCycleId + " ....");
            HttpResponse<String> response = this.createCycle(testCycleId, run);
            JSONObject responseBody = this.validateResponse(response, "Cycle creation");
            cycleKey = responseBody.getString("key");
            logger.println("Cycle created successfully " + cycleKey);
        } else {
            cycleKey = testCycleId;
        }
        logger.println("Updating results for " + cycleKey);
        for (File resultFile : resultFiles) {
            logger.print(StringUtils.rightPad("", 5, "*"));
            logger.print("File Name: " + resultFile.getName());
            logger.println(StringUtils.rightPad("", 5, "*"));
            HttpResponse<String> response = this.importResults(cycleKey, frameworkType, addCase, createCase, bddForceUpdateCase, resultFile);
            JSONObject responseBody = this.validateResponse(response, "Import results");
            logResults(frameworkType, responseBody, hideDetails, logger);
        }
    }

    private void logResults(String frameworkType, JSONObject responseBody, boolean hideDetails, PrintStream logger) {
        final int keyColumnLength = 80;
        final int dividerLength = 100;
        if(responseBody != null) {
            logger.println(StringUtils.rightPad("Status:", 30) + responseBody.getString("status"));
            logger.println(StringUtils.rightPad("Total Runs:", 30) + responseBody.getString("requestCount"));
            logger.println(StringUtils.rightPad("Successfully updated:", 30) + responseBody.getString("successCount"));
            logger.println(StringUtils.rightPad("Errors:", 30) + responseBody.getString("errorCount"));
            if (!hideDetails) {
                logger.println(StringUtils.rightPad("", dividerLength, "-"));
                logger.println(StringUtils.rightPad("Key", keyColumnLength) + (frameworkType.toLowerCase().equals("cucumber")? "": "Run Status"));
                logger.println(StringUtils.rightPad("", dividerLength, "-"));
                if(!responseBody.getString("errorCount").equals("0")) {
                    JSONObject errors = responseBody.getJSONObject("errors");
                    Iterator<String> caseIterator = errors.keys();
                    while (caseIterator.hasNext()) {
                        String key = caseIterator.next();
                        logger.println(StringUtils.rightPad(StringUtils.abbreviate(key,keyColumnLength), keyColumnLength)
                                + errors.getJSONObject(key).getString("message"));
                    }
                }
                if(this.getKeyValue(responseBody, "processedData", logger) != null) {
                    JSONObject processedData = responseBody.getJSONObject("processedData");
                    Iterator<String> caseIterator = processedData.keys();
                    while (caseIterator.hasNext()) {
                        String key = caseIterator.next();
                        logger.println(StringUtils.rightPad(StringUtils.abbreviate(key,keyColumnLength), keyColumnLength)
                                + (frameworkType.toLowerCase().equals("cucumber") ? "" : processedData.getJSONObject(key).getString("status")));
                    }
                }
                logger.println(StringUtils.rightPad("", dividerLength, "-"));
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

    private HttpResponse<String> createCycle(String cyclePrefix, Run run) {
        String objective = "Created by automation run " + run.toString() ;
        String cycleTitle = cyclePrefix + " - " + run.getTime().toString();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("title",cycleTitle);
        jsonObject.addProperty("objective", objective);

        return this.getHttpRequest(CREATE_CYCLE, true)
                .routeParam("jiraProjectId", this.projectId)
                .body(jsonObject).asString();
    }

    private HttpResponse<String> importResults(String testCycleId, String frameworkType,
                                                 boolean addCase, boolean createCase, boolean bddForceUpdateCase, File f) {
        return this.getHttpRequest(IMPORT_RESULTS_FILE, false)
                .queryString("type",frameworkType)
                .routeParam("jiraProjectId", this.projectId)
                .routeParam("testCycleId", testCycleId)
                .field("file", f)
                .field("addCaseToCycle", Boolean.toString(addCase))
                .field("createCase", Boolean.toString(createCase))
                .field("bddForceUpdateCase", Boolean.toString(bddForceUpdateCase)).asString();
    }

    private HttpRequestWithBody getHttpRequest(String url, Boolean isJson) {
        HttpRequestWithBody requestWithBody = Unirest.post(getAIOEndpoint(url));
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
    private String getAIOEndpoint(String url) {
        String base = this.apiKey != null? AIO_TESTS_HOST + API_VERSION : this.jiraServerUrl + SERVER_API_VERSION ;
        return base + url;
    }

    private static String getAuthKey(String apiKey) {
        return AIO_SCHEME + " " + apiKey;
    }

}
