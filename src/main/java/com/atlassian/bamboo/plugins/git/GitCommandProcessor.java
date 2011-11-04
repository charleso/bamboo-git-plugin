package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.core.RepositoryUrlObfuscator;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.ProxyErrorReceiver;
import com.atlassian.utils.process.ExternalProcess;
import com.atlassian.utils.process.ExternalProcessBuilder;
import com.atlassian.utils.process.LineOutputHandler;
import com.atlassian.utils.process.OutputHandler;
import com.atlassian.utils.process.PluggableProcessHandler;
import com.atlassian.utils.process.StringOutputHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO: Document this class / interface here
 *
 * @since v5.0
 */
class GitCommandProcessor implements Serializable, ProxyErrorReceiver
{
    private static final Logger log = Logger.getLogger(GitCommandProcessor.class);

    // ------------------------------------------------------------------------------------------------------- Constants

    static final Pattern hgVersionPattern = Pattern.compile("^Mercurial Distributed SCM \\(version (.*)\\)");

    // ------------------------------------------------------------------------------------------------- Type Properties

    private final String gitExecutable;
    private final BuildLogger buildLogger;
    private final int commandTimeoutInMinutes;
    private final boolean maxVerboseOutput;
    private String proxyErrorMessage;
    private Throwable proxyException;
    private String sshCommand;
    private String sshKeyFile;
    private boolean sshCompression;

    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors

    public GitCommandProcessor(@Nullable final String gitExecutable, @NotNull final BuildLogger buildLogger, final int commandTimeoutInMinutes)
    {
        this(gitExecutable, buildLogger, commandTimeoutInMinutes, false);
    }

    public GitCommandProcessor(@Nullable final String gitExecutable, @NotNull final BuildLogger buildLogger, final int commandTimeoutInMinutes, boolean maxVerboseOutput)
    {
        this.gitExecutable = gitExecutable;
        this.buildLogger = buildLogger;
        this.commandTimeoutInMinutes = commandTimeoutInMinutes;
        this.maxVerboseOutput = maxVerboseOutput;
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods
    // -------------------------------------------------------------------------------------------------- Public Methods

    /**
     * Checks whether mercurial exist in current system.
     *
     * @param workingDirectory specifies arbitrary directory.
     * @throws RepositoryException when mercurial wasn't found in current system.
     */
    public void checkHgExistenceInSystem(@NotNull final File workingDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("version");

        GitStringOutputHandler outputHandler = new GitStringOutputHandler();

        try
        {
            runCommand(commandBuilder.build(), workingDirectory, outputHandler);
            String output = outputHandler.getOutput();
            Matcher matcher = hgVersionPattern.matcher(output);
            if (!matcher.find())
            {
                String errorMessage = "Mercurial Executable capability `" + gitExecutable + "' does not seem to be a hg client. Is it properly set?";
                log.error(errorMessage + " Output:\n" + output);
                throw new RepositoryException(errorMessage);
            }
        }
        catch (GitCommandException e)
        {
            throw new RepositoryException("Git not found. Is Git Executable capability properly set in configuration?", e);
        }
    }

    /**
     * Creates .hg repository in a given directory.
     *
     * @param workingDirectory - directory in which we want to create empty repository.
     * @throws RepositoryException when init command fails
     */
    public void runInitCommand(@NotNull final File workingDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("init");
        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    /**
     * Clones remote repository into local directory. It doesn't update working copy - it just clones .hg directory. Use runUpdateCommand
     * to fill working directory with repository working copy.
     *
     * @param workingDirectory specifies directory in which we want to clone remote repository. It has to be either empty directory or non existent.
 	 * @param repositoryUrl specifies remote repository.
 	 * @param revision specifies revision we want to clone to - especially it might point to a branch.
     * @throws RepositoryException when clone command fails.
     */
    public void runCloneCommand(@NotNull final File workingDirectory, @NotNull final String repositoryUrl, @Nullable final String revision) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("clone", "--noupdate")
                .source(repositoryUrl)
	            .destination(workingDirectory.getAbsolutePath())
                .revision(revision);
        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    /**
     * Updates (pulls changesets from remote repository) workingDirectory to specified revision.
     *
     * @param workingDirectory specifies directory we want to update.
     * @param revision specifies revision we want to update/pull to.
     * @throws RepositoryException when pull command fails.
     */
    public void runPullCommand(@NotNull final File workingDirectory, @NotNull final String repositoryUrl,
                               @Nullable final String revision) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("pull")
                .source(repositoryUrl)
                .revision(revision);

        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    /**
     * Updates working copy in workingDirectory to specified revision. It forces to clean up working copy (to avoid interactive merges etc).
     *
     * @param workingDirectory specifies directory we want to update. It must have hg repository.
     * @param revision specifies revision we want to update to.
     * @throws RepositoryException when update command fails.
     */
    public void runUpdateCommand(@NotNull final File workingDirectory, @Nullable final String revision) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("update", "--clean")
                .revision(revision);
        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public void runFetchCommand(@NotNull final File workingDirectory, @NotNull final String repoUrl, @Nullable final String branch) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("fetch", repoUrl);
          //      .shallowClone();
        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }


    /**
     * Archives (exports) contents of specified revision, applying the optional include pattern.
     *
     * @param destinationDirectory specifies directory to export to
     * @param repository hg repository to export from.
     * @param revision specifies revision we want to export.
     * @param includePattern set of include patterns
     * @throws RepositoryException when update command fails.
     */
    public void runArchiveCommand(@NotNull final File destinationDirectory, @NotNull File repository, @Nullable final String revision, @Nullable String[] includePattern) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("archive", "--type", "files")
                .revision(revision);

        if (includePattern != null)
        {
            for (String s : includePattern)
            {
                commandBuilder.append("-I").append(s);
            }
        }
        commandBuilder.destination(destinationDirectory.getAbsolutePath());
        runCommand(commandBuilder.build(), repository, new LoggingOutputHandler(buildLogger));
    }

    /**
     * Returns total count of commits since previousRevision up to tip revision. If no previousRevision is specified then it returns all hg logged changes.
     *
     *
     * @param branch branch on which to operate
     * @param workingDirectory specifies directory contained by hg repository.
     * @param previousRevision specifies the starting point of changeset history we are interested in.
     * @param targetRevision the latest revision we are interested in or tip if null
     * @return number of commits in workingDirectory since previousRevision
     * @throws RepositoryException when hg repository is not found or parsing log file failed.
     */
    public int getTotalChangesetsCountSinceRevision(final String branch, @NotNull final File workingDirectory,
                                                    @Nullable final String previousRevision, @Nullable final String targetRevision) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createLogCommandBuilder(branch, previousRevision, targetRevision)
                .template(" ");
        GitStringOutputHandler outputHandler = new GitStringOutputHandler();
        runCommand(commandBuilder.build(), workingDirectory, outputHandler);
        return outputHandler.getOutput().length();
    }

    private GitCommandBuilder createLogCommandBuilder(@Nullable final String branch, @Nullable final String previousRevision, @Nullable final String targetRevision)
    {
        final GitCommandBuilder commandBuilder = createCommandBuilder("log")
                .revision((StringUtils.isNotBlank(targetRevision) ? targetRevision : "tip") + ":0")
                .append("--follow")
//                .branch(branch) possibly not needed - follow seems to do the job and branch is supported only on recent Hg clients.
                .prune(StringUtils.isNotBlank(previousRevision) ? previousRevision : null);
        return commandBuilder;
    }

    /**
     * Returns list of commit extracted from hg log command. It returns no more than the specified amount of
     * changesets, and it returns commits since previousRevision up to tip revision. If no previousRevision is specified then it returns all changes.
     *
     *
     * @param branch branch on which to operate
     * @param workingDirectory specifies directory contained by hg repository.
     * @param previousRevision specifies the starting point of changeset history we are interested in.
     * @param targetRevision the latest revision we are interested in or tip if null
     * @param limit the maximum amount of changesets to be reported
     * @return list of commits in workingDirectory since previousRevision
     * @throws RepositoryException when hg repository is not found or parsing log file failed.
     */
    //@NotNull
    //public List<CommitContext> getChangesetsSinceRevision(@Nullable final String branch, @NotNull final File workingDirectory,
    //                                                      @Nullable final String previousRevision, @Nullable final String targetRevision, final int limit) throws RepositoryException
    //{
    //    File hgStyleFile;
    //    try
    //    {
    //        hgStyleFile = getHgStyleFile();
    //    }
    //    catch (IOException e)
    //    {
    //        throw new RepositoryException("Unable to create hg style file during extracting changes from hg log", e);
    //    }
    //
    //    try
    //    {
    //        GitCommandBuilder commandBuilder = createLogCommandBuilder(branch, previousRevision, targetRevision)
    //                .style(hgStyleFile.getAbsolutePath())
    //                .limit(limit);
    //        ExtractChangesetsOutputHandler outputHandler = new ExtractChangesetsOutputHandler(buildLogger, previousRevision);
    //        runCommand(commandBuilder.build(), workingDirectory, outputHandler);
    //
    //        return Lists.newArrayList(Lists.transform(outputHandler.getChangesets(), CommitWithParents.extractCommitTransformer));
    //    }
    //    finally
    //    {
    //        //noinspection ResultOfMethodCallIgnored
    //        hgStyleFile.delete();
    //    }
    //}

    /**
     * Returns 40char hash code of the tip-most changeset in remote repository.
     *
     * @param workingDirectory specifies where to run clone command from.
     * @param repositoryUrl specifies remote repository.
     * @param branch specifies which branch to check (might be null, which means "default" branch).
     * @return 40char tip hash.
     * @throws RepositoryException when clone command fails.
     */
    @NotNull
    public String getRemoteTipForBranch(@NotNull final File workingDirectory, @NotNull final String repositoryUrl, @Nullable final String branch) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("id", "-i")
                .revision(StringUtils.isBlank(branch) ? "default" : branch)
                .debug(true) //because we want to obtain full 40-char hash
                .source(repositoryUrl);

        return extractTipHash(commandBuilder, workingDirectory);
    }

    /**
     * Returns 40char hash code of the tip-most changeset in repository contained in workingDirectory (or it parents).
     *
     * @param workingDirectory directory contained by hg repository.
     * @return 40char tip hash.
     * @throws RepositoryException when workingDirectory is not versioned by hg repository.
     */
    @NotNull
    public String getLocalTipInDirectory(@NotNull final File workingDirectory, @Nullable final String branch) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("id", "-i")
                .revision(StringUtils.isNotBlank(branch) ? branch : "tip")
                .debug(true) //because we want to obtain full 40-char hash
                .source(workingDirectory.getAbsolutePath());
        try
        {
            return extractTipHash(commandBuilder, workingDirectory);
        }
        catch (GitCommandException e)
        {
            throw //e.getStderr().contains("abort: repository " + workingDirectory.getAbsolutePath() + " not found!") ?
              //      new GitRepositoryNotFoundException("Unable to find hg repository during running " + commandBuilder.toString(), e)
            //        :
            e;
        }
    }

    // -------------------------------------------------------------------------------------------------- Helper Methods

    private GitCommandBuilder createCommandBuilder(String... commands)
    {
        return new GitCommandBuilder(commands)
                .executable(gitExecutable)
                .verbose(true)
                .sshCommand(sshCommand)
                .sshKeyFile(sshKeyFile)
                .sshCompression(sshCompression);
    }

    public void reportProxyError(String message, Throwable exception)
    {
        proxyErrorMessage = message;
        proxyException = exception;
    }

    class LoggingOutputHandler extends LineOutputHandler implements GitCommandProcessor.GitOutputHandler
    {
        final BuildLogger buildLogger;
        final StringBuilder stringBuilder;

        public LoggingOutputHandler(@NotNull final BuildLogger buildLogger)
        {
            this.buildLogger = buildLogger;
            stringBuilder = new StringBuilder();
        }

        @Override
        protected void processLine(int i, String s)
        {
            buildLogger.addBuildLogEntry(s);
            stringBuilder.append(s);
        }

        public String getStdout()
        {
            return stringBuilder.toString();
        }
    }

    private void runCommand(@NotNull final List<String> commandArgs, @NotNull final File workingDirectory,
                            @NotNull final GitOutputHandler outputHandler) throws RepositoryException
    {
        //noinspection ResultOfMethodCallIgnored
        workingDirectory.mkdirs();

        PluggableProcessHandler handler = new PluggableProcessHandler();
        handler.setOutputHandler(outputHandler);
        StringOutputHandler errorHandler = new StringOutputHandler();
        handler.setErrorHandler(errorHandler);

        if (maxVerboseOutput)
        {
            StringBuilder stringBuilder = new StringBuilder();
            for (String s : RepositoryUrlObfuscator.obfuscatePasswordsInUrls(commandArgs))
            {
                stringBuilder.append(s).append(" ");
            }
            buildLogger.addBuildLogEntry(stringBuilder.toString());
        }

        ExternalProcess process = new ExternalProcessBuilder()
                .command((commandArgs), workingDirectory)
                .handler(handler)
                .build();
        process.setTimeout(TimeUnit.MINUTES.toMillis(commandTimeoutInMinutes));
        process.execute();

        if (!handler.succeeded())
        {
            // command may contain user password (url) in plaintext -> hide it from bamboo plan/build logs. see BAM-5781
            throw new GitCommandException("command " + RepositoryUrlObfuscator.obfuscatePasswordsInUrls(commandArgs) + " failed. Working directory was `"
                    + workingDirectory + "'.",
                    proxyException != null ? proxyException : handler.getException(),
                    outputHandler.getStdout(),
                    proxyErrorMessage != null ? "SSH Proxy error: " + proxyErrorMessage : errorHandler.getOutput());
        }
    }

    //List<CommitContext> filterOutUnreachableChangesets(List<CommitWithParents> commits, String targetRevision)
    //{
    //    if (targetRevision == null)
    //    {
    //        return Lists.newArrayList(Lists.transform(commits, CommitWithParents.extractCommitTransformer));
    //    }
    //
    //    Map<String, CommitWithParents> graph = new HashMap<String, CommitWithParents>(commits.size());
    //    for (CommitWithParents commitWithParents : commits)
    //    {
    //        String id = commitWithParents.getRevision();
    //        graph.put(id, commitWithParents);
    //    }
    //
    //    if (!graph.containsKey(targetRevision))
    //    {
    //        return Lists.newArrayList(Lists.transform(commits, CommitWithParents.extractCommitTransformer));
    //    }
    //
    //    Queue<String> referencedIds = new LinkedList<String>();
    //    referencedIds.add(targetRevision);
    //
    //    final List<CommitContext> filtered = Lists.newArrayListWithCapacity(commits.size());
    //    while (!referencedIds.isEmpty())
    //    {
    //        String id = referencedIds.remove();
    //        CommitWithParents cwp = graph.remove(id);
    //        if (cwp != null)
    //        {
    //            filtered.add(cwp.getCommit());
    //            referencedIds.addAll(cwp.getParents());
    //        }
    //    }
    //
    //    return filtered;
    //}

    interface GitOutputHandler extends OutputHandler
    {
        String getStdout();
    }

    class GitStringOutputHandler extends StringOutputHandler implements GitOutputHandler
    {
        public String getStdout()
        {
            return getOutput();
        }
    }

    private String extractTipHash(@NotNull final GitCommandBuilder commandBuilder, @NotNull final File workingDirectory) throws RepositoryException
    {
        GitStringOutputHandler outputHandler = new GitStringOutputHandler();
        runCommand(commandBuilder.build(), workingDirectory, outputHandler);
        String lines = outputHandler.getOutput();

        if (lines.length() == 0)
        {
            throw new RepositoryException("No output to process from hg command");
        }

        return extractChangesetIdFromOutput(lines);
    }

    static String extractChangesetIdFromOutput(String lines) throws RepositoryException
    {
        // This method used to match the LAST pattern in the input, now it matches first
        // This SHOULD be fine for the protocol, though

        Pattern p = Pattern.compile("^[0-9a-f]{40}$", Pattern.MULTILINE);
        final Matcher matcher = p.matcher(lines);
        if (matcher.find())
        {
            return matcher.group();
        }

        throw new RepositoryException("Changeset Id not found, hg output: " + lines);
    }

    @NotNull
    private File getHgStyleFile() throws IOException
    {
        File tmp = File.createTempFile("hg.style", ".tmp");
        tmp.deleteOnExit();
        FileUtils.copyURLToFile(getClass().getResource("hg.style"), tmp);
        return tmp;
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators


    public void setSshCommand(String sshCommand)
    {
        this.sshCommand = sshCommand;
    }

    public void setSshKeyFile(String sshKeyFile)
    {
        this.sshKeyFile = sshKeyFile;
    }

    public void setSshCompression(boolean sshCompression)
    {
        this.sshCompression = sshCompression;
    }
}