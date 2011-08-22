package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ShallowClonesTest extends GitAbstractTest
{
    @BeforeClass
    void setupGlobalShallowClones() throws Exception
    {
        Field useShallowClones = GitRepository.class.getDeclaredField("USE_SHALLOW_CLONES");
        useShallowClones.setAccessible(true);
        useShallowClones.setBoolean(null, true);
    }

    @DataProvider(parallel = true)
    Object[][] testShallowCloneData() throws Exception
    {
        return new Object[][]{
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"},
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"},
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/5.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/2.git", "1455", "shallow-clones/1-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/2.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/2.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
        };
    }

//    final static Map<String, String[]> testShallowCloneDataMappings = new HashMap<String, String[]>() {{
//        put("1", new String[]{"git://github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"});
//        put("2", new String[]{"git://github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"});
//        put("3", new String[]{"git://github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"});
//    }};

    static final String[] protocols = new String[]{"git://", "https://"};

    @Test(dataProvider = "testShallowCloneData")
    public void testShallowClone(final String[][] successiveFetches) throws Exception
    {
        for (String protocol : protocols)
        {
            File tmp = createTempDirectory();
            GitOperationHelper helper = createGitOperationHelper();

            String revision = null;
            for (String[] currentFetch : successiveFetches)
            {
                helper.fetch(tmp, createAccessData(protocol + currentFetch[0]), true);
                revision = helper.checkout(null, tmp, currentFetch[1], revision);
                verifyContents(tmp, currentFetch[2]);
            }
        }
    }

    @DataProvider(parallel = true)
    Object[][] testShallowCloneDataFailing() throws Exception
    {
        return new Object[][]{
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/5.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/5.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/5.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
        };
    }

    @Test(dataProvider = "testShallowCloneDataFailing")
    public void testRepositoryHandlesFailingShallowClone(final String[][] successiveFetches) throws Exception
    {
        for (String protocol : protocols)
        {
            GitRepository gitRepository = createGitRepository();

            for (String[] currentFetch : successiveFetches)
            {
                setRepositoryProperties(gitRepository, protocol + currentFetch[0]);
                gitRepository.retrieveSourceCode(mockBuildContext(), currentFetch[1]);
                verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), currentFetch[2]);
            }
        }
    }

    @DataProvider(parallel = true)
    Object[][] testUseShallowClonesCheckboxData() throws Exception
    {
        return new Object[][]{
                {"git://github.com/pstefaniak/7.git",         "728b4f095a115a91be26",  true,    2},
                {"git://github.com/pstefaniak/7.git",         "728b4f095a115a91be26", false,    7},
                {"git://github.com/pstefaniak/72parents.git", "f9a3b37fcbf5298c1bfa",  true,   73},
                {"git://github.com/pstefaniak/72parents.git", "f9a3b37fcbf5298c1bfa", false,   74},
        };
    }

    @Test(dataProvider = "testUseShallowClonesCheckboxData")
    public void testUseShallowClonesCheckbox(String repositoryUrl, String targetRevision, boolean shallow, int expectedChangesetCount) throws Exception
    {
        GitRepository gitRepository = createGitRepository();

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("repository.git.useShallowClones", shallow);
        setRepositoryProperties(gitRepository, repositoryUrl, params);

        gitRepository.retrieveSourceCode(mockBuildContext(), targetRevision);
        assertEquals(createGitOperationHelper().extractCommits(gitRepository.getSourceCodeDirectory(PLAN_KEY), null, targetRevision).getChanges().size(), expectedChangesetCount);
    }

    @Test
    public void testShallowClone72Parents() throws Exception
    {
        File tmp = createTempDirectory();
        GitOperationHelper helper = createGitOperationHelper();

        helper.fetch(tmp, createAccessData("git://github.com/pstefaniak/72parents.git"), true);
        assertEquals(FileUtils.readLines(new File(tmp, ".git/shallow")).size(), 72);
        helper.checkout(null, tmp, "f9a3b37fcbf5298c1bfa", null);
        verifyContents(tmp, "shallow-clones/72parents-contents.zip");
    }

    @Test
    public void testDefaultFetchingToShallowedCopy() throws Exception
    {
        File tmp = createTempDirectory();
        GitOperationHelper helper = createGitOperationHelper();

        helper.fetch(tmp, createAccessData("git://github.com/pstefaniak/3.git"), true);
        assertEquals(FileUtils.readFileToString(new File(tmp, ".git/shallow")), "4c9d0c7e6167407deff1d31af5884911202dd3db\n");
        helper.fetch(tmp, createAccessData("git://github.com/pstefaniak/7.git"), false);
        assertEquals(FileUtils.readFileToString(new File(tmp, ".git/shallow")), "4c9d0c7e6167407deff1d31af5884911202dd3db\n");
        helper.checkout(null, tmp, "1070f438270b8cf1ca36", null);
        verifyContents(tmp, "shallow-clones/5-contents.zip");

        FileRepository repository = new FileRepository(new File(tmp, Constants.DOT_GIT));
        Iterable<RevCommit> log = new Git(repository).log().call();
        List<String> commits = new ArrayList<String>();
        for (RevCommit revCommit : log)
        {
            commits.add(revCommit.name());
        }

        Assert.assertEquals(commits.get(0), "1070f438270b8cf1ca36a026e70302208bf40349");
        Assert.assertEquals(commits.size(), 4);

        Assert.assertTrue(repository.getObjectDatabase().has(ObjectId.fromString("1070f438270b8cf1ca36a026e70302208bf40349")));
        Assert.assertFalse(repository.getObjectDatabase().has(ObjectId.fromString("145570eea6bf9f87a7bcf0cab2c995bf084b0698")));
        repository.close();
    }

    @Test
    public void testShallowDoesNotContainTooMuch() throws Exception
    {
        long sizeDeep = countFetchSize(false);
        long sizeShallow = countFetchSize(true);

        assertTrue(sizeDeep > sizeShallow + 500, "Expecting significant difference: " + sizeDeep + " >> " + sizeShallow);
    }

    private long countFetchSize(boolean useShallow)
            throws IOException, RepositoryException
    {
        File tmpDeep = createTempDirectory();
        GitOperationHelper helper = createGitOperationHelper();

        helper.fetch(tmpDeep, createAccessData("git://github.com/pstefaniak/5.git"), useShallow);
        helper.fetch(tmpDeep, createAccessData("git://github.com/pstefaniak/7.git"), false);

        RepositorySummary rsDeep = new RepositorySummary(tmpDeep);
        assertTrue(rsDeep.objects.isEmpty());
        long sizeDeep = 0;
        for (File pack : rsDeep.packs)
        {
            sizeDeep += pack.length();
        }
        return sizeDeep;
    }

    @Test
    public void testShallowCloneFromCacheContainsShalowInfo() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, "git://github.com/pstefaniak/7.git", Collections.singletonMap("repository.git.useShallowClones", true));

        BuildRepositoryChanges buildChanges = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        gitRepository.retrieveSourceCode(mockBuildContext(), buildChanges.getVcsRevisionKey());

        File sourceCodeDirectory = gitRepository.getSourceCodeDirectory(PLAN_KEY);

        FileRepository repository = register(new FileRepositoryBuilder().setWorkTree(sourceCodeDirectory).build());
        Git git = new Git(repository);
        Iterable<RevCommit> commits = git.log().call();
        assertEquals(Iterables.size(commits), 2);
    }

}
