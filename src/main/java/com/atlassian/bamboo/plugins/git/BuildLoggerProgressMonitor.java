package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import org.eclipse.jgit.lib.ProgressMonitor;

public class BuildLoggerProgressMonitor implements ProgressMonitor
{
    private final BuildLogger buildLogger;

    public BuildLoggerProgressMonitor(BuildLogger buildLogger)
    {
        this.buildLogger = buildLogger;
    }

    public void start(int totalTasks)
    {
    }

    public void beginTask(String title, int totalWork)
    {
        buildLogger.addBuildLogEntry("Git: " + title + " (" + totalWork + ")");
    }

    public void update(int completed)
    {
    }

    public void endTask()
    {
    }

    public boolean isCancelled()
    {
        return false;
    }
}
