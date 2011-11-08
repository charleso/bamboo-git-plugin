[#-- @ftlvariable name="plan" type="com.atlassian.bamboo.plan.Plan" --]
[#-- @ftlvariable name="plan.buildDefinition.repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]
[#assign repository=plan.buildDefinition.repository /]
[@ww.label labelKey='repository.git.repositoryUrl' value=repository.repositoryUrl /]
[@ww.label labelKey='repository.git.branch' value=repository.branch hideOnNull=true /]
[@ww.label labelKey='repository.git.authenticationType' value=repository.authTypeName /]
[@ww.label labelKey='repository.git.useShallowClones' value=repository.useShallowClones?string hideOnNull=true /]
[@ww.label labelKey='repository.git.commandTimeout' value=repository.commandTimeout! hideOnNull=true /]
[@ww.label labelKey='repository.git.verbose.logs' value=repository.verboseLogs?string hideOnNull=true /]
[@ww.label labelKey='repository.git.cacheDirectory' value=repository.cacheDirectory/]

[#if fn.hasGlobalAdminPermission() && repository.cacheDirectory?? && repository.cacheDirectory.exists()]
    <div class="infoMessage">
        [@ww.text name='repository.git.cacheDirectory.cleanMessage'/]
        <a class="requireConfirmation"
           title="[@ww.text name='repository.git.cacheDirectory.cleanTitle' /]"
           href="[@ww.url action='deleteGitCacheDirectory' namespace='/build/admin' buildKey=plan.key/]">[@ww.text name='global.buttons.delete' /]</a>
    </div>
[/#if]
