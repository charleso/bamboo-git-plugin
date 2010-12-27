[@ww.label labelKey='repository.git.repositoryUrl' value='${plan.buildDefinition.repository.repositoryUrl}' /]
[@ww.label labelKey='repository.git.branch' value='${plan.buildDefinition.repository.branch!}' hideOnNull=true /]
[@ww.label labelKey='repository.git.cacheDirectory' value='${plan.buildDefinition.repository.cacheDirectory}'/]
[#if fn.hasGlobalAdminPermission() && plan.buildDefinition.repository.cacheDirectory?? && plan.buildDefinition.repository.cacheDirectory.exists()]
    <div class="infoMessage">
        [@ww.text name='repository.git.cacheDirectory.cleanMessage'/]
        <a class="requireConfirmation"
           title="[@ww.text name='repository.git.cacheDirectory.cleanTitle' /]"
           href="[@ww.url action='deleteGitCacheDirectory' namespace='/build/admin' buildKey='${plan.key}'/]">[@ww.text name='global.buttons.delete' /]</a>
    </div>
[/#if]
