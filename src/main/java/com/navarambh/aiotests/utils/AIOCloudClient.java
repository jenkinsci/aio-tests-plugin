package com.navarambh.aiotests.utils;

import com.google.gson.JsonObject;
import hudson.model.Run;
import hudson.util.Secret;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;

public class AIOCloudClient {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON_UNICODE = "application/json;charset=UTF-8";
    private static final String AIO_TESTS_HOST = "https://tcms.aioreports.com/aio-tcms";
    private static final String API_VERSION = "/api/v1";
    private static final String AIO_SCHEME = "AioAuth";
    private static final String IMPORT_RESULTS_FILE = "/project/{jiraProjectId}/testcycle/{testCycleId}/import/results";
    private static final String CREATE_CYCLE = "/project/{jiraProjectId}/testcycle/detail";
    private String projectId;
    private Secret apiKey;

    public AIOCloudClient(String projectId, Secret apiKey) {
        this.projectId = projectId;
        this.apiKey = apiKey;
    }

    public HttpResponse<String> importResults(String frameworkType, boolean createNewCycle, String testCycleId,
                                                 boolean addCase, boolean createCase,
                                                boolean hideDetails,
                                                File f, Run<?, ?> run, PrintStream logger) {
        String cycleKey;
        if(createNewCycle) {
            logger.println("Create new cycle with prefix " + testCycleId);
            HttpResponse<String> response = this.createCycle(testCycleId, run);
            JSONObject responseBody = this.validateResponse(response, "Cycle creation");
            cycleKey = responseBody.getString("key");
            logger.println("Cycle created successfully " + cycleKey);
        } else {
            cycleKey = testCycleId;
        }
        logger.println("Updating results for " + cycleKey);
        HttpResponse<String> response = this.importResults(cycleKey, frameworkType, addCase, createCase, f);
        JSONObject responseBody = this.validateResponse(response, "Import results");
        logResults(responseBody, hideDetails, logger);
        return response;
    }

    private void logResults(JSONObject responseBody, boolean hideDetails, PrintStream logger) {
        final int keyColumnLength = 80;
        final int dividerLength = 100;
        if(responseBody != null) {
            logger.println(StringUtils.rightPad("Status:", 30) + responseBody.getString("status"));
            logger.println(StringUtils.rightPad("Total Runs:", 30) + responseBody.getString("requestCount"));
            logger.println(StringUtils.rightPad("Successfully updated:", 30) + responseBody.getString("successCount"));
            logger.println(StringUtils.rightPad("Errors:", 30) + responseBody.getString("errorCount"));
            if (!hideDetails) {
                logger.println(StringUtils.rightPad("", dividerLength, "-"));
                logger.println(StringUtils.rightPad("Key", keyColumnLength) + "Run Status");
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
                if(responseBody.get("processedData") != null) {
                    JSONObject processedData = responseBody.getJSONObject("processedData");
                    Iterator<String> caseIterator = processedData.keys();
                    while (caseIterator.hasNext()) {
                        String key = caseIterator.next();
                        logger.println(StringUtils.rightPad(StringUtils.abbreviate(key,keyColumnLength), keyColumnLength)
                                + processedData.getJSONObject(key).getString("status"));
                    }
                }
                logger.println(StringUtils.rightPad("", dividerLength, "-"));
            }
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

        return Unirest.post(getAIOEndpoint(CREATE_CYCLE))
                .header(CONTENT_TYPE_HEADER, APPLICATION_JSON_UNICODE)
                .header("Authorization", getAuthKey(this.apiKey.getPlainText()))
                .routeParam("jiraProjectId", this.projectId)
                .body(jsonObject).asString();
    }

    private HttpResponse<String> importResults(String testCycleId, String frameworkType,
                                                 boolean addCase, boolean createCase,File f) {
        String uploadEndpoint = getAIOEndpoint(IMPORT_RESULTS_FILE);
        return Unirest.post(uploadEndpoint)
                .header("Authorization", getAuthKey(this.apiKey.getPlainText()))
                .queryString("type",frameworkType)
                .routeParam("jiraProjectId", this.projectId)
                .routeParam("testCycleId", testCycleId)
                .field("file", f)
                .field("addCaseToCycle", Boolean.toString(addCase))
                .field("createCase", Boolean.toString(createCase)).asString();
    }

    private static String getAIOEndpoint(String url) {
        return AIO_TESTS_HOST + API_VERSION + url;
    }

    private static String getAuthKey(String apiKey) {
        return AIO_SCHEME + " " + apiKey;
    }

}
