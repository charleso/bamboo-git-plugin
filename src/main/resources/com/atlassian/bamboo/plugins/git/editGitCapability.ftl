[@ww.select labelKey='agent.capability.type.system.git.executable.key' name='gitExecutableKind' list=capabilityType.executableTypes listKey='key' listValue='value' toggle='true' /]
[#list capabilityType.executableTypes.keySet() as executableTypeKey]
    [@ui.bambooSection  dependsOn='gitExecutableKind' showOn=executableTypeKey]
        [@ww.textfield labelKey='agent.capability.type.system.git.executable.value' name=executableTypeKey description=capabilityType.getExecutableDescription(executableTypeKey)/]
    [/@ui.bambooSection]
[/#list]