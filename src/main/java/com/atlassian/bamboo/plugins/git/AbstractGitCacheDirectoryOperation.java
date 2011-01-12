package com.atlassian.bamboo.plugins.git;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class AbstractGitCacheDirectoryOperation<T> implements GitCacheDirectoryOperation<T>
{
    private File cacheDirectory;
    public void setCacheDirectory(@NotNull File cacheDirectory)
    {
        this.cacheDirectory = cacheDirectory;
    }

    public T call() throws Exception
    {
        return call(cacheDirectory);
    }

    public abstract T call(@NotNull File cacheDirectory) throws Exception;
}
