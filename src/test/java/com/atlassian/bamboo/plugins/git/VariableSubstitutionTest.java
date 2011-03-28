package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.variable.CustomVariableContextImpl;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.google.common.collect.Maps;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

public class VariableSubstitutionTest extends GitAbstractTest
{

    @Test
    public void testIfCachesAreEqual() throws Exception
    {
        File buildDir = createTempDirectory();
        GitRepository a = createGitRepository();
        a.setWorkingDir(buildDir);
        setRepositoryProperties(a, "value");

        GitRepository b = createGitRepository();
        b.setWorkingDir(buildDir);
        setRepositoryProperties(b, "${bamboo.variable}");

        CustomVariableContext customVariableContext = new CustomVariableContextImpl();
        customVariableContext.setVariables(Maps.<String, VariableDefinitionContext>newHashMap());
        customVariableContext.addCustomData("variable", "value");
        a.setCustomVariableContext(customVariableContext);
        b.setCustomVariableContext(customVariableContext);

        Assert.assertEquals(a.getCacheDirectory(), b.getCacheDirectory());
    }

    @DataProvider(parallel = true)
    Object[][] validationData()
    {
        return new String[][] {
                {"http://something-with-not-strange-chars", "http://${bamboo.variable}", "something-with-not-strange-chars"},
        };
    }

    @Test
    public void testIfValidationWithVariablesIsTheSameAsPlain(final String plainUrl, final String variableUrl, final String variableValue) throws Exception
    {
        GitRepository repository = createGitRepository();

        CustomVariableContext customVariableContext = new CustomVariableContextImpl();
        customVariableContext.setVariables(Maps.<String, VariableDefinitionContext>newHashMap());
        customVariableContext.addCustomData("variable", variableValue);
        repository.setCustomVariableContext(customVariableContext);

        BuildConfiguration buildConfiguration = new BuildConfiguration();
        buildConfiguration.setProperty("repository.git.repositoryUrl", plainUrl);
        ErrorCollection plainErrorCollection = repository.validate(buildConfiguration);

        buildConfiguration.setProperty("repository.git.repositoryUrl", variableUrl);
        ErrorCollection variableErrorCollection = repository.validate(buildConfiguration);

        Assert.assertEquals(variableErrorCollection.getTotalErrors(), plainErrorCollection.getTotalErrors());
        Assert.assertEquals(variableErrorCollection.getErrors(), plainErrorCollection.getErrors());
    }
}
