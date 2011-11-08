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
import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GitCommandProcessor implements Serializable, ProxyErrorReceiver
{
    private static final Logger log = Logger.getLogger(GitCommandProcessor.class);

    // ------------------------------------------------------------------------------------------------------- Constants

    static final Pattern gitVersionPattern = Pattern.compile("^git version (.*)");

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
     * Checks whether git exist in current system.
     *
     * @param workingDirectory specifies arbitrary directory.
     * @throws RepositoryException when git wasn't found in current system.
     */
    public void checkGitExistenceInSystem(@NotNull final File workingDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("version");

        GitStringOutputHandler outputHandler = new GitStringOutputHandler();

        try
        {
            runCommand(commandBuilder.build(), workingDirectory, outputHandler);
            String output = outputHandler.getOutput();
            Matcher matcher = gitVersionPattern.matcher(output);
            if (!matcher.find())
            {
                String errorMessage = "Git Executable capability `" + gitExecutable + "' does not seem to be a git client. Is it properly set?";
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
     * Creates .git repository in a given directory.
     *
     * @param workingDirectory - directory in which we want to create empty repository.
     * @throws RepositoryException when init command fails
     */
    public void runInitCommand(@NotNull final File workingDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("init");
        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public void runFetchCommand(@NotNull final File workingDirectory, @NotNull final GitRepository.GitRepositoryAccessData accessData, RefSpec refSpec, boolean useShallow) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("fetch", accessData.repositoryUrl, refSpec.getDestination());
        if (useShallow)
        {
            commandBuilder.shallowClone();
        }
        if (accessData.verboseLogs)
        {
            commandBuilder.verbose(true);
        }
        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public void runCheckoutCommand(@NotNull final File workingDirectory, String revision) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("checkout", revision);
        runCommand(commandBuilder.build(), workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    // -------------------------------------------------------------------------------------------------- Helper Methods

    private GitCommandBuilder createCommandBuilder(String... commands)
    {
        return new GitCommandBuilder(commands)
                .executable(gitExecutable)
                .sshCommand(sshCommand)
                .sshKeyFile(sshKeyFile)
                .sshCompression(sshCompression);
    }

    public void reportProxyError(String message, Throwable exception)
    {
        proxyErrorMessage = message;
        proxyException = exception;
    }

    private void runCommand(@NotNull final List<String> commandArgs, @NotNull final File workingDirectory,
                            @NotNull final GitOutputHandler outputHandler) throws RepositoryException
    {
        //noinspection ResultOfMethodCallIgnored
        workingDirectory.mkdirs();

        PluggableProcessHandler handler = new PluggableProcessHandler();
        handler.setOutputHandler(outputHandler);
        handler.setErrorHandler(outputHandler);

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
                    proxyErrorMessage != null ? "SSH Proxy error: " + proxyErrorMessage : outputHandler.getStdout());
        }
    }

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