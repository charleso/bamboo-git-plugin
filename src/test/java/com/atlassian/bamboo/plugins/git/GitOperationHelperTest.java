package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.author.AuthorImpl;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.testtools.ZipResourceDirectory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class GitOperationHelperTest extends GitAbstractTest
{

    @DataProvider(parallel = false)
    Object[][] testObtainLatestRevisionData()
    {
        return new Object[][]{
                {"obtainLatestRevision/1.zip", "HEAD",         "b91edd07dfd908cada0f4279aafe66c3beafc114"},
                {"obtainLatestRevision/2.zip", "HEAD",         "41455a21cda3002b40abe8f4d8940e6d304d4dee"},
                {"obtainLatestRevision/3.zip", "HEAD",         "3f90c11e650a33cb4ea31b958941082d1f4b8a69"},
                {"basic-repository.zip",       null,           "a26ff19c3c63e19d6a57a396c764b140f48c530a"},
                {"basic-repository.zip",       "HEAD",         "a26ff19c3c63e19d6a57a396c764b140f48c530a"},
                {"basic-repository.zip",       "master",       "a26ff19c3c63e19d6a57a396c764b140f48c530a"},
                {"basic-repository.zip",       "myBranch",     "4367e71d438f091a5e85304618a8f78f9db6738e"},
                {"basic-repository.zip",       "secondBranch", "f15f4c4a5881a2fdfa8b153dc377a081685e1d24"},
        };
    }

    @Test(dataProvider = "testObtainLatestRevisionData")
    public void testObtainLatestRevision(String zipFile, String branch, String expectedRevision) throws Exception
    {
        File repository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory(zipFile, repository);

        String result = new GitOperationHelper(new NullBuildLogger()).obtainLatestRevision(repository.getAbsolutePath(), branch, null, null);
        assertEquals(result, expectedRevision);
    }

    @DataProvider(parallel = false)
    Object[][] testCheckoutData()
    {
        return new Object[][]{
                {"basic-repository.zip", new String[][]{{"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"}}},
                {"basic-repository.zip", new String[][]{{"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"}}},
                {"basic-repository.zip", new String[][]{{"a26ff19c3c63e19d6a57a396c764b140f48c530a", "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"}}},
                {"basic-repository.zip", new String[][]{
                        {"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"},
                        {"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"},
                        {"a26ff19c3c63e19d6a57a396c764b140f48c530a", "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"},
                        {"a26ff19c3c63e19d6a57a396c764b140f48c530a", "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"},
                        {"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"},
                        {"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"},
                },
                }
        };
    }

    @Test(dataProvider = "testCheckoutData")
    public void testCheckout(String repositoryZip, String[][] targetRevisions) throws Exception
    {
        File tmp = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory(repositoryZip, tmp);

        String previousRevision = null;
        for(String[] testCase : targetRevisions)
        {
            String targetRevision = testCase[0];
            String expectedContentsInZip = testCase[1];
            String result = new GitOperationHelper(new NullBuildLogger()).checkout(tmp, targetRevision, previousRevision);

            assertEquals(result, targetRevision);
            verifyContents(tmp, expectedContentsInZip);

            previousRevision = result;
        }
    }

    static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss' 'Z"); //rfc3339date, see hg.style file

    CommitImpl createCommitImpl(String author, String comment, String date, CommitFileImpl[] commitFiles) throws Exception
    {
        CommitImpl commitImpl = new CommitImpl(new AuthorImpl(author), comment, dateFormat.parse(date));
        for(CommitFileImpl file : commitFiles)
        {
            commitImpl.addFile(file);
        }
        return commitImpl;
    }

    @DataProvider(parallel = false)
    Object[][] testExtractCommitsData() throws Exception
    {
        return new Object[][]{
                {"bamboo-git-plugin-repo.zip", "8c1010ac20da59bde61b16062445727c700ea14f", "56c986bf4aa952590e147023d2bc0dbe835d2633",
                        new Commit[] {
                                createCommitImpl("Piotr Stefaniak", "readme, license updates\n", "2010-12-07 16:55:13 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("56c986bf4aa952590e147023d2bc0dbe835d2633", "LICENSE.TXT"),
                                        new CommitFileImpl("56c986bf4aa952590e147023d2bc0dbe835d2633", "README.TXT"),
                                }),
                        }
                },
                {"bamboo-git-plugin-repo.zip", "9b66e227a34a8aceef0bed9f567b5391cf975c14", "2e396c5ff8a668df4d224e6e5632d82a5c01e0ba",
                        new Commit[] {
                                createCommitImpl("Sławomir Ginter", "Clean up working directories after test.\nThe forceDeleteOnExit does not work if the VM does not exit\n", "2010-12-09 16:19:35 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("2e396c5ff8a668df4d224e6e5632d82a5c01e0ba", "src/test/java/com/atlassian/bamboo/plugins/git/GitRepositoryTest.java"),
                                }),
                                createCommitImpl("Sławomir Ginter", "Merge branch 'master' of https://github.com/sginter/bamboo-git-plugin\n", "2010-12-09 13:58:46 +0100", new CommitFileImpl[] {
                                }),
                                createCommitImpl("Sławomir Ginter", "Use LazyReference to lazily initialize encrypter\n", "2010-12-09 13:56:00 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("01860622af59114b301186924fddb2d61ffeb50f", "src/main/java/com/atlassian/bamboo/plugins/git/GitRepository.java"),
                                }),
                                createCommitImpl("Sławomir Ginter", "Adding .idea to .gitignore, removing dummy generated Integration test\n", "2010-12-09 20:28:35 +0800", new CommitFileImpl[] {
                                        new CommitFileImpl("69aada2980a9a4484c1c13246ebff5386d8d0655", ".gitignore"),
                                        new CommitFileImpl("69aada2980a9a4484c1c13246ebff5386d8d0655", "src/test/java/it/IntegrationTestMyPlugin.java"),
                                }),
                                createCommitImpl("Sławomir Ginter", "Adding .idea to .gitignore, removing dummy generated Integration test\n", "2010-12-09 13:28:35 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("e18295d808ae266bcfd663cb7061457f9eb1b574", ".gitignore"),
                                        new CommitFileImpl("e18295d808ae266bcfd663cb7061457f9eb1b574", "src/test/java/it/IntegrationTestMyPlugin.java"),
                                }),
                        }
                },
                {"bamboo-git-plugin-repo.zip", "8f50a6fb639d3e7500d4180993f9ee083fd339d3", "7ffea3f46b8cd9c5b5d626528c0bea4e31aec705",
                        new Commit[] {
                                createCommitImpl("Sławomir Ginter", "BAM-7454 - Use jGit insteat of command line git also for tests (remove constant)\n", "2010-12-13 21:12:57 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("7ffea3f46b8cd9c5b5d626528c0bea4e31aec705", "src/test/java/com/atlassian/bamboo/plugins/git/GitMavenPomAccessorTest.java"),
                                }),
                                createCommitImpl("Sławomir Ginter", "BAM-7454 - Use jGit insteat of command line git also for tests\n", "2010-12-13 21:09:50 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("3b53371d0d178c05b0927a95719ce90e3f60f52d", "src/test/java/com/atlassian/bamboo/plugins/git/GitMavenPomAccessorTest.java"),
                                }),
                                createCommitImpl("Slawomir Ginter", "BAM-7454- Importing Git Repository from maven\n", "2010-12-13 15:15:39 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("691a59145a2d61c3672c2c90d903fcfd0c95390e", "src/main/java/com/atlassian/bamboo/plugins/git/GitMavenPomAccessor.java"),
                                        new CommitFileImpl("691a59145a2d61c3672c2c90d903fcfd0c95390e", "src/main/java/com/atlassian/bamboo/plugins/git/GitRepository.java"),
                                        new CommitFileImpl("691a59145a2d61c3672c2c90d903fcfd0c95390e", "src/test/java/com/atlassian/bamboo/plugins/git/GitAbstractTest.java"),
                                        new CommitFileImpl("691a59145a2d61c3672c2c90d903fcfd0c95390e", "src/test/java/com/atlassian/bamboo/plugins/git/GitMavenPomAccessorTest.java"),
                                        new CommitFileImpl("691a59145a2d61c3672c2c90d903fcfd0c95390e", "src/test/java/com/atlassian/bamboo/plugins/git/GitRepositoryTest.java"),
                                }),
                                createCommitImpl("Piotr Stefaniak", "BAM-7360 BAM-7397 - tests for Ssh connection to GitHub\nalso StrictHostKeyChecking=no\n", "2010-12-10 20:00:55 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("41640582d7a719fd9c0f29de71f716c17768e3b0", "src/main/java/com/atlassian/bamboo/plugins/git/GitSshSessionFactory.java"),
                                        new CommitFileImpl("41640582d7a719fd9c0f29de71f716c17768e3b0", "src/test/java/com/atlassian/bamboo/plugins/git/GitRepositoryTest.java"),
                                        new CommitFileImpl("41640582d7a719fd9c0f29de71f716c17768e3b0", "src/test/resources/bamboo-git-plugin-tests-passphrased.id_rsa"),
                                        new CommitFileImpl("41640582d7a719fd9c0f29de71f716c17768e3b0", "src/test/resources/bamboo-git-plugin-tests-passphraseless.id_rsa"),
                                }),
                                createCommitImpl("Piotr Stefaniak", "BAM-7397 Write tests for git plugin\nGitOperationHelper.checkout tests\n", "2010-12-10 19:00:22 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("1de48d29183cfb86352196fc0fc0cd066d079642", "src/test/java/com/atlassian/bamboo/plugins/git/GitAbstractTest.java"),
                                        new CommitFileImpl("1de48d29183cfb86352196fc0fc0cd066d079642", "src/test/java/com/atlassian/bamboo/plugins/git/GitOperationHelperTest.java"),
                                        new CommitFileImpl("1de48d29183cfb86352196fc0fc0cd066d079642", "src/test/java/com/atlassian/bamboo/plugins/git/GitRepositoryTest.java"),
                                }),
                        }
                },
                {"bamboo-git-plugin-repo.zip", null, "8c1010ac20da59bde61b16062445727c700ea14f",
                        new Commit[] {
                                createCommitImpl("Piotr Stefaniak", "Backporting Git plugin to 2.7 + separating it from bamboo-trunk\n", "2010-12-07 16:50:24 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "pom.xml"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/java/com/atlassian/bamboo/plugins/ExampleServlet.java"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/java/com/atlassian/bamboo/plugins/GitCacheDirectory.java"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/java/com/atlassian/bamboo/plugins/GitOperationHelper.java"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/java/com/atlassian/bamboo/plugins/GitRepository.java"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/java/com/atlassian/bamboo/plugins/GitSshSessionFactory.java"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/resources/atlassian-plugin.xml"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/resources/com/atlassian/bamboo/plugins/git/gitRepositoryEdit.ftl"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/resources/com/atlassian/bamboo/plugins/git/gitRepositoryView.ftl"),
                                        new CommitFileImpl("8c1010ac20da59bde61b16062445727c700ea14f", "src/main/resources/com/atlassian/bamboo/plugins/git/i18n.properties"),
                                }),
                                createCommitImpl("Piotr Stefaniak", "initial plugin skeleton, from SDK 3.2.3\n", "2010-12-07 14:43:25 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "LICENSE.TXT"),
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "README.TXT"),
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "pom.xml"),
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "src/main/java/com/atlassian/bamboo/plugins/ExampleServlet.java"),
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "src/main/resources/atlassian-plugin.xml"),
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "src/test/java/com/atlassian/bamboo/plugins/ExampleServletTest.java"),
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "src/test/java/it/IntegrationTestMyPlugin.java"),
                                        new CommitFileImpl("27c3d98f8fc3841288b24c6228d68f4fc6dc1b59", "src/test/resources/TEST_RESOURCES_README.TXT"),
                                }),
                        }
                },
                {"repo-with-merges.zip", null, "1cfce5981b1186ed8aba90184cc8a171127dd1fa",
                        new Commit[] {
                                createCommitImpl("Piotr Stefaniak", "Merge branch 'master' into slave\n\nConflicts:\n\tshared.txt\n", "2010-12-17 16:20:36 +0100", new CommitFileImpl[] {
                                }),
                                createCommitImpl("Piotr Stefaniak", "shared-master\n", "2010-12-17 16:19:25 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("c1e199fec9007b63d8030f3e21bd8c890015829c", "shared.txt"),
                                }),
                                createCommitImpl("Piotr Stefaniak", "shared slave\n", "2010-12-17 16:18:49 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("5d09f997e79d3c3cc88246c79c92c22b700e9f46", "shared.txt"),
                                }),
                                createCommitImpl("Piotr Stefaniak", "Merge branch 'slave'\n", "2010-12-17 16:14:05 +0100", new CommitFileImpl[] {
                                }),
                                createCommitImpl("Piotr Stefaniak", "master2\n", "2010-12-17 16:13:55 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("374cee009df728f093bf0866b7c8377b256779ab", "master.txt"),
                                }),
                                createCommitImpl("Piotr Stefaniak", "slave\n", "2010-12-17 16:08:23 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("d277ed76406ca4b7bf4668d6ea627ad1a16544d3", "slave.txt"),
                                }),
                                createCommitImpl("Piotr Stefaniak", "master commit\n", "2010-12-17 16:03:08 +0100", new CommitFileImpl[] {
                                        new CommitFileImpl("229b83e21d22b1bafeaabca886d24ad506c9cfc9", "master.txt"),
                                }),
                        }
                },
        };
    }

    @Test(dataProvider = "testExtractCommitsData")
    public void testExtractCommits(String repositoryZip, String previousRevision, String targetRevision, Commit[] expectedCommits) throws Exception
    {
        File tmp = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory(repositoryZip, tmp);

        List<Commit> commits = new GitOperationHelper(new NullBuildLogger()).extractCommits(tmp, previousRevision, targetRevision);

        assertEquals(commits.size(), expectedCommits.length);
        for (int i = 0; i < commits.size(); i++)
        {
            assertEquals(commits.get(i), expectedCommits[i]);

            List<CommitFile> expectedFiles = expectedCommits[i].getFiles();
            List<CommitFile> files = commits.get(i).getFiles();

            assertEquals(files.size(), expectedFiles.size());
            for (int j = 0; j < files.size(); j++)
            {
                assertEquals(files.get(j), expectedFiles.get(j));
            }
        }
    }

}
