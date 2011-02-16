[#-- @ftlvariable name="plan" type="com.atlassian.bamboo.plan.Plan" --]
[#-- @ftlvariable name="plan.buildDefinition.repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]
[#assign repository=plan.buildDefinition.repository /]
[@ww.label labelKey='repository.git.repositoryUrl' value='${repository.repositoryUrl}' /]
[@ww.label labelKey='repository.git.branch' value='${repository.branch!}' hideOnNull=true /]
[@ww.label labelKey='repository.git.authenticationType' value='${repository.authTypeName}' /]
[@ww.label labelKey='repository.git.useShallowClones' value='${repository.useShallowClones?string}' hideOnNull=true /]
[@ww.label labelKey='repository.git.cacheDirectory' value='${repository.cacheDirectory}'/]
[#assign otherPlans=repository.getOtherPlansSharingCache(plan)/]
[#if !otherPlans.empty]
    [@ui.displayDescription]
    [@ww.text name='repository.git.cacheDirectory.usedBy'][@ww.param]${otherPlans.size()}[/@ww.param][/@ww.text]:
        [#list otherPlans as plan]
            [@ui.renderPlanConfigLink plan=plan /][#if plan_has_next],&emsp;[/#if]
        [/#list]
    [/@ui.displayDescription]
[/#if]
[#if fn.hasGlobalAdminPermission() && repository.cacheDirectory?? && repository.cacheDirectory.exists()]
    <div class="infoMessage">
        [@ww.text name='repository.git.cacheDirectory.cleanMessage'/]
        <a class="requireConfirmation"
           title="[@ww.text name='repository.git.cacheDirectory.cleanTitle' /]"
           href="[@ww.url action='deleteGitCacheDirectory' namespace='/build/admin' buildKey='${plan.key}'/]">[@ww.text name='global.buttons.delete' /]</a>
    </div>
[/#if]
