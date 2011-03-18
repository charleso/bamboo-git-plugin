[#-- @ftlvariable name="repository" type="com.atlassian.bamboo.plugins.git.GitHubRepository" --]

[@ww.textfield labelKey='repository.github.username' name='repository.github.username' required='true' /]
[#if buildConfiguration.getString('repository.github.password')?has_content]
    [@ww.checkbox labelKey='repository.password.change' toggle='true' name='temporary.github.password.change' /]
    [@ui.bambooSection dependsOn='temporary.github.password.change' showOn='true']
        [@ww.password labelKey='repository.github.password' name='repository.github.temporary.password' /]
    [/@ui.bambooSection]
[#else]
    [@ww.hidden name='temporary.github.password.change' value='true' /]
    [@ww.password labelKey='repository.github.password' name='repository.github.temporary.password' /]
[/#if]

<div class="field-group" id="fieldArea_repository_github_repository">
    <label for="repository_github_repository" >[@ww.text name='repository.github.repository'/]</label>
    <p id="loadGitHubRepositoriesSpinner" class="hidden" >[@ui.icon type="loading" /] [@ww.text name='repository.github.loadingRepositories'/]</p>
    <select class="select" id="repository_github_repository" name="repository.github.repository" ></select>
    <button type="button" style="margin-top: 0px" id="loadGitHubRepositoriesButton" title="[@ww.text name='repository.github.loadRepositories'/]">
        [@ww.text name='repository.github.loadRepositories'/]
    </button>
    [#if fieldErrors?has_content && fieldErrors['repository.github.repository']?has_content]
        [#list fieldErrors['repository.github.repository'] as error]
            <div class="error">
                ${error?html}
            </div>[#t/]
        [/#list]
    [/#if]
    <div class="description" id="repository_github_repository_description" >[@ww.text name='repository.github.repository.description'/]</div>
</div>
<input type="hidden" name="selectFields" value="repository.github.repository" />

<div id="loadedGitHubRepositoriesDiv" class="hidden">
    [@ww.select labelKey='repository.github.branch' name='repository.github.branch' /]
    [@ww.checkbox labelKey='repository.github.useShallowClones' name='repository.github.useShallowClones' /]
</div>

<script type="text/javascript">
    var gh_repositoryKey = "com.atlassian.bamboo.plugins.atlassian-bamboo-plugin-git:gh",
        gh_baseActionUrl = BAMBOO.contextPath + "/ajax/loadGitHubRepositories.action",
        gh_actionUrl = gh_baseActionUrl[#if planKey?has_content] + "?planKey=${planKey}"[/#if],
        gh_repositoryBranchFilter,
        gh_selectedRepository,
        gh_selectedBranch;

    [#if buildConfiguration.getString('repository.github.repository')?has_content]
        gh_selectedRepository = "${buildConfiguration.getString('repository.github.repository')}";
    [/#if]
    [#if buildConfiguration.getString('repository.github.branch')?has_content]
        gh_selectedBranch = "${buildConfiguration.getString('repository.github.branch')}";
    [/#if]

    var $gh_username = AJS.$("input[name=repository.github.username]"),
        $gh_password = AJS.$("input[name=repository.github.temporary.password]"),
        $gh_repositories = AJS.$("#repository_github_repository").hide(),
        $gh_repositories_desc = AJS.$("#repository_github_repository_description").hide(),
        $gh_branches = AJS.$("select[name=repository.github.branch]"),
        $gh_loadGitHubRepositoriesButton = AJS.$("#loadGitHubRepositoriesButton"),
        $gh_loadGitHubRepositoriesSpinner = AJS.$("#loadGitHubRepositoriesSpinner"),
        $gh_loadedGitHubRepositoriesDiv = AJS.$("#loadedGitHubRepositoriesDiv"),
        $gh_form = $gh_username.closest("form"),
        $gh_selectedRepository = AJS.$("#selectedRepository");

    function gh_showActionError(errorMessage) {
        var $field = AJS.$("#fieldArea_repository_github_repository"),
            $description = $field.find('.description'),
            $error = AJS.$('<div class="error"/>').html(errorMessage);

        if ($description.length) {
            $description.before($error)
        } else {
            $field.append($error)
        }
    }

    function gh_loadGitHubRepositories(e) {
        gh_startFetching();
        AJS.$.ajax({
            type: "POST",
            url: gh_actionUrl,
            data: { username: $gh_username.val(), password: $gh_password.val() },
            success: function (json) {
                if (!gh_selectedRepository) {
                    $gh_form.find(".error:not(.aui-message)").remove();
                }
                if (json.status == "ERROR") {
                    if (json.fieldErrors) {
                        for (var fieldName in json.fieldErrors) {
                            var $field = AJS.$("#fieldArea_" + $gh_form.attr("id") + "_repository_github_" + fieldName.replace(".", "_")),
                                $description = $field.find('.description');

                            for (var i = 0, ii= json.fieldErrors[fieldName].length; i < ii; i++) {
                                var $error = AJS.$('<div class="error"/>').html(json.fieldErrors[fieldName][i]);

                                if ($description.length) {
                                    $description.before($error)
                                } else {
                                    $field.append($error)
                                }
                            }
                        }
                    }
                    if (json.errors) {
                        gh_showActionError(json.errors.join(" "));
                    }
                    gh_readyForFetching();
                } else if (json.status == "OK") {
                    $gh_loadedGitHubRepositoriesDiv.show();
                    var options = $gh_repositories.attr("options");
                    for (var repository in json.gitHubRepositories) {
                        options[options.length] = new Option(repository, repository);
                    }
                    if (gh_selectedRepository) {
                        $gh_repositories.val(gh_selectedRepository);
                        gh_selectedRepository = null;
                    }
                    gh_repositoryBranchFilter = json.repositoryBranchFilter;
                    $gh_repositories.show();
                    $gh_repositories_desc.show();
                    $gh_repositories.change();
                    if (gh_selectedBranch) {
                        $gh_branches.val(gh_selectedBranch);
                        gh_selectedBranch = null;
                    }
                    gh_readyForFetching();
                }
            },
            error : function (XMLHttpRequest) {
                gh_showActionError("[@ww.text name='repository.github.ajaxError'/] ["+XMLHttpRequest.status+" "+XMLHttpRequest.statusText+"]");
                gh_readyForFetching();
            },
            dataType: "json"
        });
    }

    function gh_startFetching() {
        gh_repositoryBranchFilter = null;
        $gh_repositories.empty().hide();
        $gh_repositories_desc.hide();
        $gh_username.attr("disabled", "disabled");
        $gh_password.attr("disabled", "disabled");
        $gh_loadGitHubRepositoriesButton.hide();
        $gh_loadGitHubRepositoriesSpinner.show();
        $gh_loadedGitHubRepositoriesDiv.hide();
    }

    function gh_readyForFetching() {
        $gh_username.removeAttr("disabled");
        $gh_password.removeAttr("disabled");
        $gh_loadGitHubRepositoriesButton.show();
        $gh_loadGitHubRepositoriesSpinner.hide();
    }

    AJS.$(function(){
        $gh_repositories.change(function() {
            mutateSelectListContent(AJS.$(this), $gh_branches, gh_repositoryBranchFilter);
        });
    });

    $gh_loadGitHubRepositoriesButton.click(gh_loadGitHubRepositories);

    [#if buildConfiguration.getString('repository.github.repository')?has_content]
        if ($gh_selectedRepository.val() == gh_repositoryKey) {
            $gh_loadGitHubRepositoriesButton.click();
            gh_actionUrl = gh_baseActionUrl;
        }
    [/#if]
</script>
