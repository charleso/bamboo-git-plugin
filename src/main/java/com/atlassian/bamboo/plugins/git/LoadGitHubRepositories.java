package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.rest.util.Get;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.ww2.actions.PlanActionSupport;
import com.atlassian.bamboo.ww2.aware.permissions.PlanEditSecurityAware;
import com.opensymphony.webwork.dispatcher.json.JSONArray;
import com.opensymphony.webwork.dispatcher.json.JSONException;
import com.opensymphony.webwork.dispatcher.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LoadGitHubRepositories extends PlanActionSupport implements PlanEditSecurityAware
{
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(LoadGitHubRepositories.class);

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String GITHUB_API_BASE_URL = new SystemProperty(false, "atlassian.bamboo.github.api.base.url",
            "ATLASSIAN_BAMBOO_GITHUB_API_BASE_URL").getValue("http://github.com/api/v2/json/");

    // ------------------------------------------------------------------------------------------------- Type Properties
    private String username;
    private String password;

    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors
    // -------------------------------------------------------------------------------------------------- Action Methods

    public String doLoad() throws Exception
    {
        return SUCCESS;
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @NotNull
    @Override
    public JSONObject getJsonObject() throws JSONException
    {
        Map<String, List<String>> gitHubRepositories = null;

        if (getPlan() != null && getPlan().getBuildDefinition().getRepository() instanceof GitHubRepository)
        {
            GitHubRepository ghRepository = Narrow.to(getPlan().getBuildDefinition().getRepository(), GitHubRepository.class);
            password = new StringEncrypter().decrypt(ghRepository.getPassword());
        }

        if (StringUtils.isBlank(username))
        {
            addFieldError("username", getText("repository.github.error.emptyUsername"));
        }
        checkFieldXssSafety("username", username);

        if (!hasErrors())
        {
            try
            {
                gitHubRepositories = getGitHubRepositores();
            }
            catch (FileNotFoundException e)
            {
                addFieldError("username", getText("repository.github.error.invalidUsername"));
            }
            catch (IllegalArgumentException e)
            {
                addFieldError("username", getText("repository.github.error.invalidUsername"));
            }
            catch (JSONException e)
            {
                addFieldError("username", getText("repository.github.error.invalidUsername"));
            }
            catch (Exception e)
            {
                addActionError(getText("repository.github.ajaxError") + e.toString());
                log.error("Could not load bitbucket repositories for " + username + ".", e);
            }
        }

        JSONObject jsonObject = super.getJsonObject();

        if (hasErrors())
        {
            return jsonObject;
        }

        List<JSONObject> data = new ArrayList<JSONObject>();
        for (Map.Entry<String, List<String>> entry : gitHubRepositories.entrySet())
        {
            String repository = entry.getKey();
            for (String branch : entry.getValue())
            {
                data.add(new JSONObject()
                        .put("value", branch)
                        .put("text", branch)
                        .put("supportedValues", new String[]{repository}));
            }
        }
        jsonObject.put("repositoryBranchFilter", new JSONObject().put("data", data));
        jsonObject.put("gitHubRepositories", gitHubRepositories);

        return jsonObject;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------------------- Private Helper

    private JSONObject getJSONResponseFromUrl(String url) throws Exception
    {
        Get call = new Get(url);
        call.setBasicCredentials(username, password);
        try
        {
            call.execute();
            return new JSONObject(IOUtils.toString(call.getResponseAsStream()));
        }
        finally
        {
            call.release();
        }
    }

    @NotNull
    private List<String> getRepositoryBranches(String repository) throws Exception
    {
        final List<String> repositoryBranches = new ArrayList<String>();
        final JSONObject json = getJSONResponseFromUrl(GITHUB_API_BASE_URL + "repos/show/" + repository + "/branches");
        final JSONObject branches = json.getJSONObject("branches");
        if (branches != null)
        {
            Iterator it = branches.keys();
            while (it.hasNext())
            {
                repositoryBranches.add((String)it.next());
            }
        }
        return repositoryBranches;
    }

    private void addRepositoriesFromJson(@NotNull final Map<String, List<String>> repositories, @NotNull final JSONObject json) throws Exception
    {
        final JSONArray jsonRepositories = json.getJSONArray("repositories");
        for (int index = 0; index < jsonRepositories.length(); index++)
        {
            final JSONObject jsonRepository = jsonRepositories.getJSONObject(index);
            final String owner = jsonRepository.getString("owner");
            final String name = jsonRepository.getString("name");
            final String repository = owner + "/" + name;
            repositories.put(repository, getRepositoryBranches(repository));
        }
    }

    @NotNull
    private Map<String, List<String>> getGitHubRepositores() throws Exception
    {
        final Map<String, List<String>> githubRepositories = new LinkedHashMap<String, List<String>>();

        if (StringUtils.isNotBlank(password))
        {
            final JSONObject json = getJSONResponseFromUrl(GITHUB_API_BASE_URL + "repos/pushable");
            if (json.has("error") && json.getString("error").equals("not authorized"))
            {
                if (getPlan() != null)
                {
                    addFieldError("username", getText("repository.github.error.notAuthorized"));
                }
                else
                {
                    addFieldError("temporary.password", getText("repository.github.error.notAuthorized"));
                }
                return githubRepositories;
            }
            addRepositoriesFromJson(githubRepositories, json);
        }

        final JSONObject json = getJSONResponseFromUrl(GITHUB_API_BASE_URL + "repos/show/" + username);
        addRepositoriesFromJson(githubRepositories, json);

        if (githubRepositories.isEmpty())
        {
            addFieldError("username", getText("repository.bitbucket.error.noRepositories", Arrays.asList(username)));
        }
        return githubRepositories;
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public void setUsername(String username)
    {
        this.username = username;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}