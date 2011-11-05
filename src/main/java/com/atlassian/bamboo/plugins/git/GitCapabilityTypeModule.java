package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.utils.i18n.I18nBeanFactory;
import com.atlassian.bamboo.v2.build.agent.capability.AbstractCapabilityTypeModule;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityDefaultsHelper;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.ww2.TextProviderAdapter;
import com.atlassian.core.i18n.I18nTextProvider;
import com.google.common.collect.Lists;
import com.opensymphony.xwork.ActionContext;
import com.opensymphony.xwork.LocaleProvider;
import com.opensymphony.xwork.TextProvider;
import com.opensymphony.xwork.TextProviderSupport;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GitCapabilityTypeModule extends AbstractCapabilityTypeModule implements CapabilityDefaultsHelper, I18nTextProvider, LocaleProvider
{
    private static final Logger log = Logger.getLogger(GitCapabilityTypeModule.class);

    // should match the names of the fields in ftl
    public static final String EXECUTABLE_KIND_FIELD = "gitExecutableKind";

    public static final String DEFAULT_SSH_COMMAND = "ssh -o StrictHostKeyChecking=no -o BatchMode=yes";

    public static final String GIT_CAPABILITY = "system.git.executable";
    public static final String GIT_SSH_CAPABILITY = "system.git.executable.ssh";

    private static final String AGENT_CAPABILITY_TYPE_PREFIX = "agent.capability.type.";
    private static final String AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE = "agent.capability.type.git.error.undefinedExecutable";
    private static final String AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE_KIND = "agent.capability.type.git.error.undefinedExecutableKind";

    private static final List<String> DEFAULT_WINDOWS_GIT_PATHS = Arrays.asList(
            "C:\\Program Files\\git",
            "C:\\Program Files (x86)\\git",
            "C:\\git"
    );

    private I18nBeanFactory i18nBeanFactory;
    private TextProvider textProvider;

    // ------------------------------------------------------------------------------------------------- Type Properties
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors
    // -------------------------------------------------------------------------------------------------- Action Methods
    // ----------------------------------------------------------------------------------------------- Interface Methods

    @NotNull
    public String getLabel(@NotNull String key)
    {
        return getText(AGENT_CAPABILITY_TYPE_PREFIX + key + ".type");
    }

    public Map<String, String> getExecutableTypes()
    {
        Map<String, String> executableTypes = new LinkedHashMap<String, String>();
        for (String key: Arrays.asList(GIT_CAPABILITY, GIT_SSH_CAPABILITY))
        {
            executableTypes.put(key, getLabel(key));
        }
        return executableTypes;
    }

    public String getExecutableDescription(String key)
    {
        return getText(AGENT_CAPABILITY_TYPE_PREFIX + key + ".description", new String[] {DEFAULT_SSH_COMMAND});
    }

    @Override
    public String getValueDescriptionKey(@NotNull String key, @Nullable String value)
    {
        return getExecutableDescription(key);
    }

    @NotNull
    public Map<String, String> validate(@NotNull Map<String, String[]> params)
    {
        Map<String, String> fieldErrors = new HashMap<String, String>();

        final String executableKind = getParamValue(params, EXECUTABLE_KIND_FIELD);
        if (StringUtils.isEmpty(executableKind))
        {
            fieldErrors.put(EXECUTABLE_KIND_FIELD, getText(AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE_KIND));
        }
        else if (GIT_CAPABILITY.equals(executableKind) && StringUtils.isEmpty(getParamValue(params, GIT_CAPABILITY)))
        {
             fieldErrors.put(GIT_CAPABILITY, getText(AGENT_CAPABILITY_TYPE_GIT_ERROR_UNDEFINED_EXECUTABLE));
        }

        return fieldErrors;
    }

    @Nullable
    public static String getParamValue(@NotNull Map<String, String[]> params, String field)
    {
        final String[] values = params.get(field);
        return ArrayUtils.isEmpty(values) ? null : values[0].trim();
    }

    @NotNull
    public Capability getCapability(@NotNull Map<String, String[]> params)
    {
        final String key = getParamValue(params, EXECUTABLE_KIND_FIELD);
        final String value = getParamValue(params, key);
        // validate() should protect us
        // noinspection ConstantConditions
        return new CapabilityImpl(key, value);
    }

    @NotNull
    public CapabilitySet addDefaultCapabilities(@NotNull CapabilitySet capabilitySet)
    {
        List<String> paths = Lists.newArrayList(StringUtils.split(SystemProperty.PATH.getValue(), File.pathSeparator));
        if (SystemUtils.IS_OS_WINDOWS)
        {
            paths.addAll(DEFAULT_WINDOWS_GIT_PATHS);
        }
        final String gitExecutableName = SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git";

        for (String path : paths)
        {
            File git = new File(path, gitExecutableName);
            if (git.exists())
            {
                log.debug("Detected " + GitCapabilityTypeModule.GIT_CAPABILITY + " at `" + git.getAbsolutePath() + "'");
                capabilitySet.addCapability(new CapabilityImpl(GitCapabilityTypeModule.GIT_CAPABILITY, git.getAbsolutePath()), false);
                return capabilitySet;
            }
        }

        return capabilitySet;
    }

    TextProvider getTextProvider()
    {
        if (textProvider == null)
        {
            textProvider = i18nBeanFactory == null ? new TextProviderSupport(getClass(), this)
                                                   : new TextProviderAdapter(i18nBeanFactory.getI18nBean(getLocale()));
        }
        return textProvider;
    }

    public String getText(final String key)
    {
        return getTextProvider().getText(key);
    }

    public String getText(final String s, final Object[] objects)
    {
        return getText(s, (String[]) objects);
    }

    public String getText(final String key, final String[] args)
    {
        return getTextProvider().getText(key, args);
    }

    public Locale getLocale() {
        return ActionContext.getContext().getLocale();
    }

    public final void setI18nBeanFactory(final I18nBeanFactory i18nBeanFactory)
    {
        this.i18nBeanFactory = i18nBeanFactory;
    }
}
