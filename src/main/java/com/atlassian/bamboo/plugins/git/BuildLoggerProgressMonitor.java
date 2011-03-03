package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.text.MessageFormat;

public class BuildLoggerProgressMonitor implements ProgressMonitor
{
    private static final long MESSAGE_INTERVAL = 10000;

    private final BuildLogger buildLogger;
    private long lastMessageTS;
    private int lastTotalWork;
    private int lastWork;

    public BuildLoggerProgressMonitor(BuildLogger buildLogger)
    {
        this.buildLogger = buildLogger;
    }

    public void start(int totalTasks)
    {
    }

    public void beginTask(String title, int totalWork)
    {
        lastTotalWork = totalWork;
        lastWork = 0;
        String message = MessageFormat.format("Git: {0}{1,choice,0#|1# ({1})}", title, totalWork);

        buildLogger.addBuildLogEntry(message);
        lastMessageTS = System.currentTimeMillis();
    }

    public void update(int completed)
    {
        lastWork += completed;
        long now = System.currentTimeMillis();
        if (now > lastMessageTS + MESSAGE_INTERVAL)
        {
            String message = lastTotalWork > 0 ?
                    MessageFormat.format("Git: {0,number,percent} ({1}/{2})", (double)lastWork/lastTotalWork, lastWork, lastTotalWork) :
                    MessageFormat.format("Git: ({0})", lastWork);

            buildLogger.addBuildLogEntry(message);
            lastMessageTS = now;
        }
    }

    public void endTask()
    {
        lastTotalWork = 0;
    }

    public boolean isCancelled()
    {
        return false;
    }
}
