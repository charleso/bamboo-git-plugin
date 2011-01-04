package com.atlassian.bamboo.plugins.git;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class TweakedUsernamePasswordCredentialsProvider extends UsernamePasswordCredentialsProvider
{
    public TweakedUsernamePasswordCredentialsProvider(String username, String password)
    {
        super(username, password);
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem
    {
        if (items.length == 1 && items[0] instanceof CredentialItem.StringType && items[0].isValueSecure())
        {
            // they are actually asking for password
            CredentialItem.Password passwordPrompt = new CredentialItem.Password();
            super.get(uri, passwordPrompt);
            ((CredentialItem.StringType) items[0]).setValue(String.valueOf(passwordPrompt.getValue()));
            return true;
        }
        else
        {
            return super.get(uri, items);
        }
    }
}
