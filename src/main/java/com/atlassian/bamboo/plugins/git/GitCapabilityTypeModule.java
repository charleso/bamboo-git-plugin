package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.v2.build.agent.capability.AbstractExecutableCapabilityTypeModule;
import com.atlassian.bamboo.v2.build.agent.capability.AbstractMultipleExecutableCapabilityTypeModule;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

public class GitCapabilityTypeModule extends AbstractMultipleExecutableCapabilityTypeModule
{
    public static final String GIT_CAPABILITY = "system.git.executable";
    public static final String SSH_CAPABILITY = "system.git.executable.ssh";

    private static final String AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE = "agent.capability.type.git.error.undefinedExecutable";
    private static final String AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE_KIND = "agent.capability.type.git.error.undefinedExecutableKind";

    private static final String DEFAULT_SSH_CAPABILITY = "/usr/bin/ssh";
    // ------------------------------------------------------------------------------------------------- Type Properties
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors
    // -------------------------------------------------------------------------------------------------- Action Methods
    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    public String getExecutableKindKey()
    {
        return "gitExecutableKind";
    }

    @Override
    public String getCapabilityUndefinedKey()
    {
        return AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE;
    }

    @Override
    public String getCapabilityKindUndefinedKey()
    {
        return AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE_KIND;
    }

    @Override
    public String getMandatoryCapabilityKey()
    {
        return GIT_CAPABILITY;
    }

    @Override
    public List<String> getAdditionalCapabilityKeys()
    {
        return Lists.newArrayList(SSH_CAPABILITY);
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

    @Override
    public String getExecutableDescription(String key)
    {
        return getText(AGENT_CAPABILITY_TYPE_PREFIX + key + ".description", new String[] {DEFAULT_SSH_CAPABILITY});
    }
}
