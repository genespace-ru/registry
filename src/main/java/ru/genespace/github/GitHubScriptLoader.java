package ru.genespace.github;

import java.nio.file.Path;
import java.nio.file.Paths;

import biouml.plugins.wdl.ScriptLoader;
import biouml.plugins.wdl.model.ScriptInfo;
import ru.genespace.content.ContentManager;

public class GitHubScriptLoader extends ScriptLoader
{
    public GitHubScriptLoader(String type)
    {
        super( type );
    }

    private GitHubManager manager;
    private String repositoryId;
    private String repositoryRef;
    private ContentManager cache;
    private String shortType;
    private Path baseScriptPath;

    public GitHubScriptLoader(String type, GitHubManager manager, String repositoryId, String repositoryRef, String primaryDescriptorPath, String shortType, ContentManager cache)
    {
        super( type );
        this.repositoryId = repositoryId;
        this.repositoryRef = repositoryRef;
        this.cache = cache;
        this.manager = manager;
        this.shortType = shortType;
        baseScriptPath = Paths.get( primaryDescriptorPath ).getParent();
    }

    @Override
    public ScriptInfo loadScript(String path) throws Exception
    {
        Path scriptPath = baseScriptPath.resolve( path ).normalize();
        String content = manager.getWorkflowContent( repositoryId, repositoryRef, scriptPath.toString(), shortType, cache );
        String name = scriptPath.getFileName().toString();

        ScriptInfo importedScript = this.readScript( name, content );
        return importedScript;
    }

}
