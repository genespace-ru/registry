package ru.genespace.github;

import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

public class GitHubModule extends ServletModule
{
    @Override
    protected void configureServlets()
    {
        //bind( GitHubManager.class ).in( Scopes.SINGLETON );
    }
}
