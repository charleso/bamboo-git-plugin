package com.atlassian.bamboo.plugins.git.testutils;

import com.atlassian.bamboo.commit.CommitContext;
import com.google.common.base.Function;

public class ExtractComments implements Function<CommitContext, String>
{
    public String apply(CommitContext from)
    {
        return from.getComment().trim();
    }
}
