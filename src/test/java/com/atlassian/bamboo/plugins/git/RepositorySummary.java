package com.atlassian.bamboo.plugins.git;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

class RepositorySummary
{
    Collection<File> objects;
    Collection<File> packs;

    @SuppressWarnings({"unchecked"})
    RepositorySummary(File targetDir) throws IOException
    {
        FileRepositoryBuilder targetInfo = new FileRepositoryBuilder().setWorkTree(targetDir).setup();
        File objectDir = targetInfo.getObjectDirectory();
        objects = FileUtils.listFiles(objectDir, new RegexFileFilter("[0-9a-f]{38}"), new RegexFileFilter("[0-9a-f]{2}"));
        packs = FileUtils.listFiles(objectDir, new RegexFileFilter("pack-[0-9a-f]{40}.pack"), new RegexFileFilter("pack"));
    }
}
