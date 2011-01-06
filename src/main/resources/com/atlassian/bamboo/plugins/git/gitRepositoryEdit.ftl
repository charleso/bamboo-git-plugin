[#-- @ftlvariable name="repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]
[@ui.bambooSection]
    [@ww.textfield labelKey='repository.git.repositoryUrl' name='repository.git.repositoryUrl' required='true' helpKey='git.fields' /]

    [@ww.textfield labelKey='repository.git.branch' name='repository.git.branch' /]

    [@ww.select
        labelKey='repository.git.authenticationType'
        name='repository.git.authenticationType'
        toggle='true'
        list=repository.authenticationTypes
        listKey='name'
        listValue='label']
    [/@ww.select]

    [@ui.bambooSection dependsOn='repository.git.authenticationType' showOn='PASSWORD']
        [@ww.textfield labelKey='repository.git.username' name='repository.git.username' /]

        [#if buildConfiguration.getString('repository.git.password')?has_content]
            [@ww.checkbox labelKey='repository.password.change' toggle='true' name='temporary.git.password.change' /]
            [@ui.bambooSection dependsOn='temporary.git.password.change' showOn='true']
                [@ww.password labelKey='repository.git.password' name='temporary.git.password' required='false' /]
            [/@ui.bambooSection]
        [#else]
            [@ww.hidden name='temporary.git.password.change' value='true' /]
            [@ww.password labelKey='repository.git.password' name='temporary.git.password' /]
        [/#if]
    [/@ui.bambooSection]
    [@ui.bambooSection dependsOn='repository.git.authenticationType' showOn='SSH_KEYPAIR']
        [#if buildConfiguration.getString('repository.git.ssh.key')?has_content]
            [@ww.checkbox labelKey='repository.git.ssh.key.change' toggle='true' name='temporary.git.ssh.key.change' /]
            [@ui.bambooSection dependsOn='temporary.git.ssh.key.change' showOn='true']
                [@ww.file labelKey='repository.git.ssh.key' name='temporary.git.ssh.keyfile' /]
            [/@ui.bambooSection]
        [#else]
            [@ww.hidden name='temporary.git.ssh.key.change' value='true' /]
            [@ww.file labelKey='repository.git.ssh.key' name='temporary.git.ssh.keyfile' /]
        [/#if]

        [#if buildConfiguration.getString('repository.git.ssh.passphrase')?has_content]
            [@ww.checkbox labelKey='repository.passphrase.change' toggle='true' name='temporary.git.ssh.passphrase.change' /]
            [@ui.bambooSection dependsOn='temporary.git.ssh.passphrase.change' showOn='true']
                [@ww.password labelKey='repository.git.ssh.passphrase' name='temporary.git.ssh.passphrase' /]
            [/@ui.bambooSection]
        [#else]
            [@ww.hidden name='temporary.git.ssh.passphrase.change' value="true" /]
            [@ww.password labelKey='repository.git.ssh.passphrase' name='temporary.git.ssh.passphrase' /]
        [/#if]
    [/@ui.bambooSection]
[/@ui.bambooSection]