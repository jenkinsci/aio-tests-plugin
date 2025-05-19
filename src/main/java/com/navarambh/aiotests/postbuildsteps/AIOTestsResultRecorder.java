package com.navarambh.aiotests.postbuildsteps;

import com.google.common.collect.ImmutableList;
import com.navarambh.aiotests.utils.AIOCloudClient;
import com.navarambh.aiotests.utils.FileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;

public class AIOTestsResultRecorder extends Recorder implements SimpleBuildStep {

    private String frameworkType;
    private String resultsFilePath;
    private String projectKey;
    private String jiraInstanceType = "cloud";
    private String jiraServerUrl;
    private String jiraUsername;
    private Secret jiraPassword;
    private Boolean failBuildOnFailure = false;
    private Boolean hideDetails = false;
    private Boolean addCaseToCycle;
    private Boolean createCase;
    private Boolean bddForceUpdateCase;
    private Boolean createNewRun;
    private Secret apiKey;
    private Entry entry;
    private String defaultFolder;
    private Boolean forceUpdateCase = false;
    private Boolean updateOnlyRunStatus = false;
    private Boolean ignoreClassInAutoKey = false;
    private Boolean isBatch = false;
    private Boolean createLogReport = false;

    @DataBoundConstructor
    public AIOTestsResultRecorder(String projectKey, String frameworkType, String resultsFilePath, Boolean addCaseToCycle,
                                  Boolean createCase, Boolean bddForceUpdateCase,Boolean forceUpdateCase,Boolean isBatch,Boolean createNewRun,
                                  Boolean createLogReport, Boolean updateOnlyRunStatus, Boolean ignoreClassInAutoKey, String defaultFolder, Secret apiKey ) {
        this.frameworkType =frameworkType;
        this.projectKey = projectKey;
        this.resultsFilePath = resultsFilePath;
        this.addCaseToCycle = addCaseToCycle;
        this.createCase = createCase;
        this.bddForceUpdateCase = bddForceUpdateCase;
        this.createNewRun = createNewRun;
        this.apiKey = apiKey;
        this.defaultFolder = defaultFolder;
        this.forceUpdateCase = forceUpdateCase;
        this.updateOnlyRunStatus = updateOnlyRunStatus;
        this.ignoreClassInAutoKey = ignoreClassInAutoKey;
        this.isBatch = isBatch;
        this.createLogReport = createLogReport;
    }

    public String getProjectKey() { return projectKey; }
    public String getFrameworkType() { return frameworkType; }
    public Secret getApiKey() { return apiKey; }
    public String getResultsFilePath() { return resultsFilePath; }
    public Boolean getAddCaseToCycle() { return addCaseToCycle; }
    public Boolean getCreateCase() { return createCase; }
    public Boolean getBddForceUpdateCase() { return bddForceUpdateCase; }
    public Boolean getForceUpdateCase() { return forceUpdateCase; }
    public Boolean getIsBatch() { return isBatch; }
    public Boolean isCreateNewRun() {
        if(createNewRun == null) {
            this.createNewRun = true;
            return true;
        }
        return createNewRun;
    }

    public Boolean getFailBuildOnFailure() { return failBuildOnFailure; }

    public String getJiraUsername() { return jiraUsername; }
    @DataBoundSetter
    public void setJiraUsername(String jiraUsername) { this.jiraUsername = jiraUsername; }

    public String getJiraServerUrl() { return jiraServerUrl; }
    @DataBoundSetter
    public void setJiraServerUrl(String jiraServerUrl) { this.jiraServerUrl = jiraServerUrl; }

    public Secret getJiraPassword() { return jiraPassword; }
    @DataBoundSetter
    public void setJiraPassword(Secret jiraPassword) { this.jiraPassword = jiraPassword; }

    @DataBoundSetter
    public void setFrameworkType(String frameworkType) { this.frameworkType = frameworkType; }

    public String getJiraInstanceType() { return jiraInstanceType; }

    @DataBoundSetter
    public void setJiraInstanceType(String jiraInstanceType) {
        if(this.jiraInstanceType != null) {
            this.jiraInstanceType = jiraInstanceType;
        }
    }

    @DataBoundSetter
    public void setFailBuildOnFailure(boolean failBuildOnFailure) {
        this.failBuildOnFailure = failBuildOnFailure;
    }

    public Boolean getHideDetails() { return hideDetails; }
    @DataBoundSetter
    public void setHideDetails(boolean hideDetails) { this.hideDetails = hideDetails; }

    public Boolean getCreateLogReport() {
        return createLogReport;
    }
    @DataBoundSetter
    public void setCreateLogReport(Boolean createLogReport) {
        this.createLogReport = createLogReport;
    }

    @DataBoundSetter
    public void setEntry(Entry entry) { this.entry = entry;}
    public Entry getEntry() {
        return entry;
    }

    public String getDefaultFolder() { return defaultFolder; }

    @DataBoundSetter
    public void setDefaultFolder(String defaultFolder) { this.defaultFolder = defaultFolder; }

    public Boolean getIgnoreClassInAutoKey() {return ignoreClassInAutoKey;}

    @DataBoundSetter
    public void setIgnoreClassInAutoKey(Boolean ignoreClassInAutoKey) {this.ignoreClassInAutoKey = ignoreClassInAutoKey;}

    public Boolean getUpdateOnlyRunStatus() {return updateOnlyRunStatus;}

    @DataBoundSetter
    public void setUpdateOnlyRunStatus(Boolean updateOnlyRunStatus) {this.updateOnlyRunStatus = updateOnlyRunStatus;}

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String isServer() {
        return this.jiraInstanceType !=null && this.jiraInstanceType.equalsIgnoreCase("server") ? "true" : "false";
    }

    public String isFramework(String fmwk) {
        return this.frameworkType != null && this.frameworkType.trim().toLowerCase().equals(fmwk)? "true" : "false";
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath filePath, @NonNull Launcher launcher,
                        @NonNull TaskListener taskListener) throws InterruptedException, IOException {
        StringBuilder reportText = null;
        setNewValuesForOldJobs();
        if(this.createLogReport){
            reportText=  new StringBuilder();
        }

        logStartEnd(true, taskListener, reportText);
        if(this.createNewRun == null) {
            this.createNewRun = true;
        }
        if(StringUtils.isEmpty(this.frameworkType) || StringUtils.isEmpty(this.projectKey) || this.entry == null || StringUtils.isEmpty(this.resultsFilePath)) {
            aioLogger("Publishing results failed : " +
                    "Mandatory data (framework type/ project key / cycle preference / results file path ) is missing.  Please check configuration",taskListener.getLogger(), reportText);
            this.setResultStatus(run, taskListener, reportText);
            logStartEnd(false, taskListener, reportText);
            return;
        }
        if ((Boolean.parseBoolean(this.isServer()) && (StringUtils.isEmpty(this.jiraServerUrl) || StringUtils.isEmpty(this.jiraUsername) || this.jiraPassword == null))
                || (!Boolean.parseBoolean(this.isServer()) && (this.apiKey == null || this.apiKey.getPlainText().isEmpty()))) {
            aioLogger("Publishing results failed : " +
                    "Mandatory data ( " + (Boolean.parseBoolean(this.isServer()) ? "Jira URL/Username/Password" : "API Token") + " ) is missing.  Please validate authorization information.", taskListener.getLogger() ,reportText);
            this.setResultStatus(run, taskListener, reportText);
            logStartEnd(false, taskListener, reportText);
            return;
        }
        aioLogger("Jira Hosting : " + this.jiraInstanceType + (Boolean.parseBoolean(this.isServer())? " - " + this.jiraServerUrl : ""),taskListener.getLogger() ,reportText);
        aioLogger("Framework Type: " + this.frameworkType,taskListener.getLogger() ,reportText);
        EnvVars buildEnvVars = this.setupEnvVars(run, taskListener, reportText);
        this.resultsFilePath = (String)this.getParameterizedDataIfAny(buildEnvVars, this.resultsFilePath);
        aioLogger("File Path: " + this.resultsFilePath,taskListener.getLogger(), reportText);

        List<File> f = FileUtils.getFiles(filePath, this.resultsFilePath, run, this.frameworkType.toLowerCase(), taskListener.getLogger(), reportText);
        if(f.size() == 0) {
            aioLogger("File not found @ " + this.resultsFilePath,taskListener.getLogger(),reportText);
            this.setResultStatus(run, taskListener, reportText);
            logStartEnd(false, taskListener, reportText);
            return;
        }

        //Set cycle preferences
        boolean createNewCycle = false;
        boolean createIfAbsent = false;
        String cycleData = "";

        NewCycle newCycle = null;
        ExistingCycle existingCycle = null;
        CreateIfCycleAbsent createIfCycleAbsent = null;

        if(this.entry instanceof ExistingCycle) {
            existingCycle = (ExistingCycle)this.entry;
            cycleData = existingCycle.getCycleKey();
        }else if (this.entry instanceof NewCycle) {
            createNewCycle = true;
            newCycle = (NewCycle)this.entry;
            cycleData = newCycle.getCyclePrefix();
        }else{
            createIfAbsent = true;
            createIfCycleAbsent = (CreateIfCycleAbsent)this.entry;
            cycleData = createIfCycleAbsent.getCycleName();
        }

        //Get parametrized data

        if(Boolean.parseBoolean(this.isServer())) { this.checkIfPasswordIsParametrized(buildEnvVars); }
        else { this.checkIfAPIKeyIsParametrized(buildEnvVars); }

        cycleData = ((String)this.getParameterizedDataIfAny(buildEnvVars, cycleData)).trim();

        this.projectKey = ((String)this.getParameterizedDataIfAny(buildEnvVars, this.projectKey)).trim();
        try {
            AIOCloudClient aioClient = Boolean.parseBoolean(this.isServer())?
                    new AIOCloudClient(this.projectKey, this.jiraServerUrl,this.jiraUsername, this.jiraPassword) : new AIOCloudClient(this.projectKey, this.apiKey);
            aioClient.importResults( this.frameworkType, createNewCycle, cycleData , this.addCaseToCycle, this.createCase, this.bddForceUpdateCase, this.createNewRun, this.forceUpdateCase,this.isBatch,
                    this.hideDetails, f, run, newCycle,taskListener.getLogger(), createIfAbsent, reportText, this.ignoreClassInAutoKey, this.updateOnlyRunStatus , this.defaultFolder);
            if(filePath.isRemote()) {
                FileUtils.deleteFile(f, taskListener.getLogger(), reportText);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            aioLogger("Publishing results failed : " + e.getMessage(),taskListener.getLogger(),reportText);
            for (int i = 0; (i < e.getStackTrace().length && i < 6) ; i++) {
                aioLogger(e.getStackTrace()[i].toString(),taskListener.getLogger(),reportText);
            }
            this.setResultStatus(run, taskListener,reportText);
        }
        logStartEnd(false, taskListener, reportText);
        if(this.createLogReport){
            run.addAction(new LogReportAction(reportText.toString()));
            taskListener.getLogger().println("AIO Tests logs has been generated in the AIO Tests file.");
        }

    }

    /**
     * When new fields are introduced, old jobs that are already saved, the values come as null.
     * No matter what the default value is set, it is not picked up by Jenkins.
     * Explicitly setting default status to avoid NPEs and correct values.
     */
    private void setNewValuesForOldJobs() {
        if(this.createLogReport == null) {
            this.createLogReport = false;
        }
        if(this.forceUpdateCase == null) {
            this.forceUpdateCase = false;
        }
        if(this.isBatch == null) {
            this.isBatch = false;
        }
        if(this.ignoreClassInAutoKey == null){
            this.ignoreClassInAutoKey = false;
        }
        if(this.updateOnlyRunStatus == null){
            this.updateOnlyRunStatus = false;
        }
    }

    private  void setResultStatus(Run run, TaskListener taskListener, StringBuilder reportText) {
        if(failBuildOnFailure) {
            aioLogger("Publish results to AIO Tests : Marking build as failed.",taskListener.getLogger(), reportText);
            run.setResult(Result.FAILURE);
        }
    }

    private static void logStartEnd(boolean start, TaskListener taskListener, StringBuilder reportText) {
        String startLog = "AIO Tests : Publishing results";
        aioLogger(StringUtils.leftPad("",110,"*"),taskListener.getLogger(), reportText);
        aioLogger(StringUtils.leftPad(start? startLog : startLog + " end.",start? 70 : 74),taskListener.getLogger(), reportText);
        aioLogger(StringUtils.leftPad("",110,"*"),taskListener.getLogger(), reportText);
    }

    public static void aioLogger(String message, PrintStream logger, StringBuilder reportText) {
        if (reportText != null)  {
            reportText.append(message).append("\n");
        } else {
            logger.println(message);
        }
    }

    public static abstract class Entry extends AbstractDescribableImpl<Entry> {}

    private void checkIfPasswordIsParametrized(EnvVars buildEnvVars) {
        String passwordValue = this.getJiraPassword().getPlainText();
        String parametrizedValue = (String) this.getParameterizedDataIfAny(buildEnvVars, passwordValue);
        if(!passwordValue.equals(parametrizedValue)) {
            this.jiraPassword = Secret.fromString(parametrizedValue);
        }
    }

    private void checkIfAPIKeyIsParametrized(EnvVars buildEnvVars) {
        String apiKeyValue = this.getApiKey().getPlainText();
        String parametrizedValue = (String) this.getParameterizedDataIfAny(buildEnvVars, apiKeyValue);
        if(!apiKeyValue.equals(parametrizedValue)) {
            this.apiKey = Secret.fromString(parametrizedValue);
        }
    }

    private EnvVars setupEnvVars(Run run, TaskListener taskListener, StringBuilder reportText) {
        EnvVars buildEnvVars;
        try {
            buildEnvVars = run.getEnvironment(taskListener);
            return buildEnvVars;
        }      catch(Exception e) {
            aioLogger("Error retrieving environment variables: " + e.getMessage(), taskListener.getLogger(), reportText);
        }
        return null;
    }

    private Object getParameterizedDataIfAny(EnvVars buildEnvVars, String key ) {
        if(buildEnvVars != null) {
            return buildEnvVars.expand(key);
        }
        return key;
    }

    @Symbol("aioImport")
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(AIOTestsResultRecorder.class);
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return "Publish results to AIO Tests - Jira";
        }


        public List<Descriptor> getEntryDescriptors() {
            Jenkins jenkins = Jenkins.get();
            try {
                return ImmutableList.of(jenkins.getDescriptor(NewCycle.class), jenkins.getDescriptor(ExistingCycle.class), jenkins.getDescriptor(CreateIfCycleAbsent.class));
            } catch (Exception e){
                throw new RuntimeException("Error initializing entry options");
            }
        }

        public FormValidation doCheckJiraServerUrl(@QueryParameter String jiraServerUrl)  {
            if (StringUtils.isEmpty(jiraServerUrl)) {
                return FormValidation.error("*Required");
            }
            try {
                URL u = new URL(jiraServerUrl); // this would check for the protocol
                u.toURI();
            } catch (Exception e) {
                return FormValidation.error("Please specify a valid Jira server URL");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckProjectKey(@QueryParameter String projectKey)  {
            if (StringUtils.isEmpty(projectKey)) {
                return FormValidation.error("Please specify a project key.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckFilePath(@QueryParameter String filePath) {
            if (StringUtils.isEmpty(filePath)) {
                return FormValidation.error("Please specify the file path of results file ");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckApiKey(@QueryParameter Secret apiKey)  {
            if (StringUtils.isEmpty(apiKey.getPlainText())) {
                return FormValidation.error("Api Token cannot be empty.");
            }
            return FormValidation.ok();
        }

    }

    public static final class ExistingCycle extends Entry {

        private final String cycleKey;

        @DataBoundConstructor public ExistingCycle(String cycleKey) {
            this.cycleKey = cycleKey;
        }

        public String getCycleKey() {
            return cycleKey;
        }

        @Extension public static class DescriptorImpl extends Descriptor<Entry> {

            @Override
            @NonNull
            public String getDisplayName() {
                return "Use an existing cycle";
            }

            public FormValidation doCheckCycleKey(@QueryParameter String cycleKey)  {
                if (StringUtils.isEmpty(cycleKey)) {
                    return FormValidation.error("Cycle Key cannot be empty.");
                }
                return FormValidation.ok();
            }
        }
    }

    public static final class NewCycle extends Entry {

        private final String cyclePrefix;
        private final String cycleFolder;
        private final String cycleTasks;

        @DataBoundConstructor public NewCycle(String cyclePrefix, String cycleFolder, String cycleTasks) {
            this.cyclePrefix = cyclePrefix;
            this.cycleFolder = cycleFolder;
            this.cycleTasks = cycleTasks;
        }

        public String getCyclePrefix() {
            return cyclePrefix;
        }
        public String getCycleFolder() {return cycleFolder;}
        public String getCycleTasks() {return cycleTasks;}

        @Extension public static class DescriptorImpl extends Descriptor<Entry> {
            @Override
            @NonNull
            public String getDisplayName() {
                return "Create new cycle for each job run";
            }

            public FormValidation doCheckCyclePrefix(@QueryParameter String cyclePrefix)  {
                if (StringUtils.isEmpty(cyclePrefix)) {
                    return FormValidation.error("Cycle Prefix cannot be empty.");
                }
                return FormValidation.ok();
            }
        }
    }

    public static final class CreateIfCycleAbsent extends Entry {

        private final String cycleName;

        public String getCycleName() {return cycleName;}

        @DataBoundConstructor
        public CreateIfCycleAbsent(String cycleName) {
            this.cycleName = cycleName;
        }

        @Extension public static class DescriptorImpl extends Descriptor<Entry> {

            @Override
            @NonNull
            public String getDisplayName() {
                return "Create cycle if absent.";
            }

            public FormValidation doCheckCycleName(@QueryParameter String cycleName)  {
                if (StringUtils.isEmpty(cycleName)) {
                    return FormValidation.error("*Cycle Name cannot be empty.");
                }
                return FormValidation.ok();
            }

        }

    }

}
