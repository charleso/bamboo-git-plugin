package com.atlassian.bamboo.plugins.git.testutils;

import com.atlassian.bamboo.commit.Commit;
import com.google.common.base.Function;

public class ExtractComments implements Function<Commit, String>
{
    public String apply(Commit from)
    {
        return from.getComment().trim();
    }
}
