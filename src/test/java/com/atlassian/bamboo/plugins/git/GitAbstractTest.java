package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.util.BambooFileUtils;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class GitAbstractTest {
    protected static final Collection<File> filesToCleanUp = Collections.synchronizedCollection(new ArrayList<File>());

    File createTempDirectory() throws Exception
    {
        File tmp = BambooFileUtils.createTempDirectory("bamboo-git-plugin-test");
        FileUtils.forceDeleteOnExit(tmp);
        filesToCleanUp.add(tmp);
        return tmp;
    }

    @AfterClass
    static void cleanUpFiles()
    {
        for (File file : filesToCleanUp) {
            FileUtils.deleteQuietly(file);
        }
    }

}
