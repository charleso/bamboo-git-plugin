package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.util.BambooFileUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.testng.annotations.AfterClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.testng.Assert.assertEquals;

public class GitAbstractTest {
    protected static final Collection<File> filesToCleanUp = Collections.synchronizedCollection(new ArrayList<File>());

    File createTempDirectory() throws Exception
    {
        File tmp = BambooFileUtils.createTempDirectory("bamboo-git-plugin-test");
        FileUtils.forceDeleteOnExit(tmp);
        filesToCleanUp.add(tmp);
        return tmp;
    }

    static void verifyContents(File directory, final String expectedZip) throws IOException
    {
        final Enumeration<? extends ZipEntry> entries = new ZipFile(GitAbstractTest.class.getResource("/" + expectedZip).getFile()).entries();
        int fileCount = 0;
        while (entries.hasMoreElements())
        {
            ZipEntry zipEntry = entries.nextElement();
            if (!zipEntry.isDirectory())
            {
                fileCount++;
                String fileName = zipEntry.getName();
                final File file = new File(directory, fileName);
                assertEquals(FileUtils.checksumCRC32(file), zipEntry.getCrc(), "CRC for " + file);
            }
        }

        final Collection files = listFiles(directory, FileFilterUtils.trueFileFilter(), FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter(".git")));
        assertEquals(files.size(), fileCount, "Number of files");
    }


    @AfterClass
    static void cleanUpFiles()
    {
        for (File file : filesToCleanUp) {
            FileUtils.deleteQuietly(file);
        }
    }

}
