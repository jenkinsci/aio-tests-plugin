package com.navarambh.aiotests.postbuildsteps;

import hudson.model.Run;
import jenkins.model.RunAction2;

public class LogReportAction implements RunAction2 {
    private transient Run run;

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    public Run getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/aio-tests/images/ic-app-icon.png";
    }

    @Override
    public String getDisplayName() {
        return "AIO Tests";
    }

    @Override
    public String getUrlName() {
        return "aio-tests";
    }
    private String name;

    public LogReportAction(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
