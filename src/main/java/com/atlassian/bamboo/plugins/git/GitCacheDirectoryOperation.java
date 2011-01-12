package com.atlassian.bamboo.plugins.git;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.Callable;

public interface GitCacheDirectoryOperation<V> extends Callable<V>
{
    void setCacheDirectory(@NotNull File cacheDirectory);
}
