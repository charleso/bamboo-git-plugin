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
    <input type="button" class="button loadButton" id="loadGitHubRepositoriesButton" value="[@ww.text name='repository.github.loadRepositories'/]" />
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

BAMBOO.LoadGitHubRepositoriesAsynchronously = function() {
    var repositoryKey = "${repository.key}",
        baseActionUrl = BAMBOO.contextPath + "/ajax/loadGitHubRepositories.action",
        actionUrl = baseActionUrl[#if planKey?has_content] + "?planKey=${planKey}"[/#if],
        repositoryBranchFilter,
        selectedRepository,
        selectedBranch;

    [#if buildConfiguration.getString('repository.github.repository')?has_content]
        selectedRepository = "${buildConfiguration.getString('repository.github.repository')}";
    [/#if]
    [#if buildConfiguration.getString('repository.github.branch')?has_content]
        selectedBranch = "${buildConfiguration.getString('repository.github.branch')}";
    [/#if]

    var $username = AJS.$("input[name='repository.github.username']"),
        $password = AJS.$("input[name='repository.github.temporary.password']"),
        $repositories = AJS.$("#repository_github_repository").hide(),
        $repositories_desc = AJS.$("#repository_github_repository_description").hide(),
        $branches = AJS.$("select[name='repository.github.branch']"),
        $loadGitHubRepositoriesButton = AJS.$("#loadGitHubRepositoriesButton"),
        $loadGitHubRepositoriesSpinner = AJS.$("#loadGitHubRepositoriesSpinner"),
        $loadedGitHubRepositoriesDiv = AJS.$("#loadedGitHubRepositoriesDiv"),
        $form = $username.closest("form"),
        $selectedRepository = AJS.$("#selectedRepository");

    function showActionError(errorMessage) {
        var $field = AJS.$("#fieldArea_repository_github_repository"),
            $description = $field.find('.description'),
            $error = AJS.$('<div class="error"/>').html(errorMessage);

        if ($description.length) {
            $description.before($error)
        } else {
            $field.append($error)
        }
    }

    function loadGitHubRepositories(e) {
        startFetching();
        AJS.$.ajax({
            type: "POST",
            url: actionUrl,
            data: { username: $username.val(), password: $password.val() },
            success: function (json) {
                if (!selectedRepository) {
                    $form.find(".error:not(.aui-message)").remove();
                }
                if (json.status == "ERROR") {
                    if (json.fieldErrors) {
                        for (var fieldName in json.fieldErrors) {
                            var $field = AJS.$("#fieldArea_" + $form.attr("id") + "_repository_github_" + fieldName.replace(".", "_")),
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
                        showActionError(json.errors.join(" "));
                    }
                    readyForFetching();
                } else if (json.status == "OK") {
                    $loadedGitHubRepositoriesDiv.show();
                    var options = $repositories.attr("options");
                    for (var repository in json.gitHubRepositories) {
                        options[options.length] = new Option(repository, repository);
                    }
                    if (selectedRepository) {
                        $repositories.val(selectedRepository);
                        selectedRepository = null;
                    }
                    repositoryBranchFilter = json.repositoryBranchFilter;
                    $repositories.show();
                    $repositories_desc.show();
                    $repositories.change();
                    if (selectedBranch) {
                        $branches.val(selectedBranch);
                        selectedBranch = null;
                    }
                    readyForFetching();
                }
            },
            error : function (XMLHttpRequest) {
                showActionError("[@ww.text name='repository.github.ajaxError'/] ["+XMLHttpRequest.status+" "+XMLHttpRequest.statusText+"]");
                readyForFetching();
            },
            dataType: "json"
        });
    }

    function startFetching() {
        repositoryBranchFilter = null;
        $repositories.empty().hide();
        $repositories_desc.hide();
        $username.attr("disabled", "disabled");
        $password.attr("disabled", "disabled");
        $loadGitHubRepositoriesButton.hide();
        $loadGitHubRepositoriesSpinner.show();
        $loadedGitHubRepositoriesDiv.hide();
    }

    function readyForFetching() {
        $username.removeAttr("disabled");
        $password.removeAttr("disabled");
        $loadGitHubRepositoriesButton.show();
        $loadGitHubRepositoriesSpinner.hide();
    }

    AJS.$(function(){
        $repositories.change(function() {
            mutateSelectListContent(AJS.$(this), $branches, repositoryBranchFilter);
        });
    });

    $loadGitHubRepositoriesButton.click(loadGitHubRepositories);

    [#if buildConfiguration.getString('repository.github.repository')?has_content]
        if ($selectedRepository.val() == repositoryKey) {
            $loadGitHubRepositoriesButton.click();
            actionUrl = baseActionUrl;
        }
    [/#if]

}()

</script>
