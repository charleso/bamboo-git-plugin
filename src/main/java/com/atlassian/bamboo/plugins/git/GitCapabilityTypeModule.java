package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.v2.build.agent.capability.AbstractExecutableCapabilityTypeModule;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.List;

public class GitCapabilityTypeModule extends AbstractExecutableCapabilityTypeModule
{
    private static final Logger log = Logger.getLogger(GitCapabilityTypeModule.class);

    public static final String GIT_CAPABILITY = "system.git.executable";
    public static final String GIT_EXECUTABLE = "gitExecutable";

    private static final String AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE = "agent.capability.type.git.error.undefinedExecutable";

    // ------------------------------------------------------------------------------------------------- Type Properties
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors
    // -------------------------------------------------------------------------------------------------- Action Methods
    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    public String getCapabilityKey()
    {
        return GIT_CAPABILITY;
    }

    @Override
    public String getExecutableKey()
    {
        return null;
    }

    @Override
    public String getCapabilityUndefinedKey()
    {
        return AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE;
    }

    @Override
    public List<String> getDefaultWindowPaths()
    {
        return Arrays.asList(
            "C:\\Program Files\\git",
            "C:\\Program Files (x86)\\git",
            "C:\\git"
        );
    }

    @Override
    public String getExecutableFilename()
    {
        return "git";
    }
}
