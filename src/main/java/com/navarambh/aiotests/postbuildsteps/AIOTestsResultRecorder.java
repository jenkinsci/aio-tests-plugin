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
import java.util.List;

public class AIOTestsResultRecorder extends Recorder implements SimpleBuildStep {

    private String frameworkType;
    private String resultsFilePath;
    private String projectKey;
    private Boolean failBuildOnFailure = false;
    private Boolean hideDetails = false;
    private Boolean addCaseToCycle;
    private Boolean createCase;
    private Boolean bddForceUpdateCase;
    private Secret apiKey;
    private Entry entry;

    @DataBoundConstructor
    public AIOTestsResultRecorder(String projectKey, String frameworkType, String resultsFilePath, Boolean addCaseToCycle, Boolean createCase, Boolean bddForceUpdateCase, Secret apiKey ) {
        this.frameworkType =frameworkType;
        this.projectKey = projectKey;
        this.resultsFilePath = resultsFilePath;
        this.addCaseToCycle = addCaseToCycle;
        this.createCase = createCase;
        this.bddForceUpdateCase = bddForceUpdateCase;
        this.apiKey = apiKey;
    }

    public String getProjectKey() { return projectKey; }
    public String getFrameworkType() { return frameworkType; }
    public Secret getApiKey() { return apiKey; }
    public String getResultsFilePath() { return resultsFilePath; }
    public Boolean getAddCaseToCycle() { return addCaseToCycle; }
    public Boolean getCreateCase() { return createCase; }

    public Boolean getBddForceUpdateCase() {
        return bddForceUpdateCase;
    }

    public Boolean getFailBuildOnFailure() {
        return failBuildOnFailure;
    }
    @DataBoundSetter
    public void setFailBuildOnFailure(boolean failBuildOnFailure) {
        this.failBuildOnFailure = failBuildOnFailure;
    }

    public Boolean getHideDetails() { return hideDetails; }
    @DataBoundSetter
    public void setHideDetails(boolean hideDetails) { this.hideDetails = hideDetails; }

    @DataBoundSetter
    public void setEntry(Entry entry) { this.entry = entry;}
    public Entry getEntry() {
        return entry;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath filePath, @NonNull Launcher launcher,
                        @NonNull TaskListener taskListener) throws InterruptedException, IOException {

        logStartEnd(true, taskListener);
        if(StringUtils.isEmpty(this.frameworkType) || StringUtils.isEmpty(this.projectKey) || this.entry == null || StringUtils.isEmpty(this.resultsFilePath)
                || this.apiKey == null) {
            taskListener.getLogger().println("Publishing results failed : " +
                    "Mandatory data (frameworkType/ project key / cycle preference / results file path / API Token ) is missing.  Please check configuration");
            this.setResultStatus(run, taskListener);
            logStartEnd(false, taskListener);
            return;
        }
        taskListener.getLogger().println("Framework Type: " + this.frameworkType);
        EnvVars buildEnvVars = this.setupEnvVars(run, taskListener);
        this.resultsFilePath = (String)this.getParameterizedDataIfAny(buildEnvVars, this.resultsFilePath);
        taskListener.getLogger().println("File Path: " + this.resultsFilePath);
        //Fetch file
        File f = FileUtils.getFile(filePath.getRemote(), this.resultsFilePath);
        if(f == null) {
            taskListener.getLogger().println("File not found @ " + this.resultsFilePath);
            this.setResultStatus(run, taskListener);
            logStartEnd(false, taskListener);
            return;
        }

        //Set cycle preferences
        boolean createNewCycle = this.entry instanceof NewCycle;
        String cycleData = this.entry instanceof NewCycle? ((NewCycle) this.entry).getCyclePrefix() : ((ExistingCycle) this.entry).getCycleKey();

        //Get parametrized data

        this.checkIfAPIKeyIsParametrized(buildEnvVars);
        cycleData = (String)this.getParameterizedDataIfAny(buildEnvVars, cycleData);
        this.projectKey = (String)this.getParameterizedDataIfAny(buildEnvVars, this.projectKey);
        try {
            AIOCloudClient aioClient = new AIOCloudClient(this.projectKey, this.apiKey);
            aioClient.importResults( this.frameworkType, createNewCycle, cycleData, this.addCaseToCycle, this.createCase, this.bddForceUpdateCase,
                    this.hideDetails, f, run, taskListener.getLogger());
        } catch (Throwable e) {
            e.printStackTrace();
            taskListener.getLogger().println("Publishing results failed : " + e.getMessage());
            for (int i = 0; (i < e.getStackTrace().length && i < 6) ; i++) {
                taskListener.getLogger().println(e.getStackTrace()[i]);
            }
            this.setResultStatus(run, taskListener);
        }
        logStartEnd(false, taskListener);

    }

    private  void setResultStatus(Run run, TaskListener taskListener) {
        if(failBuildOnFailure) {
            taskListener.getLogger().println("Publish results to AIO Tests : Marking build as failed.");
            run.setResult(Result.FAILURE);
        }
    }

    private static void logStartEnd(boolean start, TaskListener taskListener) {
        String startLog = "AIO Tests : Publishing results";
        taskListener.getLogger().println(StringUtils.leftPad("",110,"*"));
        taskListener.getLogger().println(StringUtils.leftPad(start? startLog : startLog + " end.",start? 70 : 74));
        taskListener.getLogger().println(StringUtils.leftPad("",110,"*"));
    }

    public static abstract class Entry extends AbstractDescribableImpl<Entry> {}

    private void checkIfAPIKeyIsParametrized(EnvVars buildEnvVars) {
        String apiKeyValue = this.getApiKey().getPlainText();
        String parametrizedValue = (String) this.getParameterizedDataIfAny(buildEnvVars, apiKeyValue);
        if(!apiKeyValue.equals(parametrizedValue)) {
            this.apiKey = Secret.fromString(parametrizedValue);
        }
    }

    public EnvVars setupEnvVars(Run run, TaskListener taskListener) {
        EnvVars buildEnvVars;
        try {
            buildEnvVars = run.getEnvironment(taskListener);
            return buildEnvVars;
        }      catch(Exception e) {
            taskListener.getLogger().println("Error retrieving environment variables: " + e.getMessage());
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
        public String getDisplayName() {
            return "Publish results to AIO Tests - Jira";
        }


        public List<Descriptor> getEntryDescriptors() {
            Jenkins jenkins = Jenkins.get();
            try {
                return ImmutableList.of(jenkins.getDescriptor(NewCycle.class), jenkins.getDescriptor(ExistingCycle.class));
            } catch (Exception e){
                throw new NullPointerException("Error initializing entry options");
            }
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

            @Override public String getDisplayName() {
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

        @DataBoundConstructor public NewCycle(String cyclePrefix) {
            this.cyclePrefix = cyclePrefix;
        }

        public String getCyclePrefix() {
            return cyclePrefix;
        }

        @Extension public static class DescriptorImpl extends Descriptor<Entry> {
            @Override public String getDisplayName() {
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

}
