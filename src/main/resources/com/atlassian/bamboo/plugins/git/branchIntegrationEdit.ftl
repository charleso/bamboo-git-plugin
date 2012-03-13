[#-- @ftlvariable name="plan" type="com.atlassian.bamboo.plan.Plan" --]
[#-- @ftlvariable name="repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]

[#if repository.useShallowClones ]
    [@ui.messageBox type='info']
        [@ww.text name='repository.git.messages.branchIntegration.shallowClonesWillBeDisabled'/]
    [/@ui.messageBox]
[/#if]
