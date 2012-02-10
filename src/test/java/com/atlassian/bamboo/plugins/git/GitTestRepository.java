package com.atlassian.bamboo.plugins.git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;

import java.io.File;
import java.io.IOException;

class GitTestRepository
{
    public final FileRepository srcRepo;
    public final Git git;
    public final File textFile;
    public final File srcDir;

    GitTestRepository(File srcDir) throws IOException
    {
        this.srcDir = srcDir;
        File gitDir = new File(srcDir, Constants.DOT_GIT);
        srcRepo = new FileRepository(gitDir);
        if (!gitDir.exists())
        {
            srcRepo.create();
        }
        textFile = getTextFile(srcDir);
        git = new Git(srcRepo);
    }

    void close()
    {
        srcRepo.close();
    }

    RevCommit commitFileContents(String text) throws IOException, GitAPIException
    {
        FileUtils.writeStringToFile(textFile, text);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(text).setCommitter("testUser", "testUser@testDomain").call();
    }

    File getTextFile(File baseDir)
    {
        return new File(baseDir, "file.txt");
    }
}
