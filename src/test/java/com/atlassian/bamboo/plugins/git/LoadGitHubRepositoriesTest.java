package com.atlassian.bamboo.plugins.git;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.opensymphony.webwork.dispatcher.json.JSONException;
import com.opensymphony.webwork.dispatcher.json.JSONObject;

public class LoadGitHubRepositoriesTest extends GitAbstractTest
{
    @Test
    public void testMaster() throws Exception
    {
        getRepositoryBranches("{'branches':{'first':'sha','master':'sha'}}", "[master, first]");
    }

    @Test
    public void testMissingMaster() throws Exception
    {
        getRepositoryBranches("{'branches':{'first':'sha','second':'sha'}}", "[first, second]");
    }

    private void getRepositoryBranches(String json, String expected)
            throws JSONException, Exception
    {
        JSONObject jsonObj = new JSONObject(json);
        LoadGitHubRepositories github = new LoadGitHubRepositories();
        List<String> branches = github.getRepositoryBranches(jsonObj);
        Assert.assertEquals(expected, branches.toString());
    }

}
