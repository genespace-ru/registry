package ru.genespace.github;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.genespace.dockstore.SourceFile;
import ru.genespace.dockstore.Workflow;
import ru.genespace.dockstore.WorkflowVersion;
import ru.genespace.dockstore.yaml.DockstoreYaml12;
import ru.genespace.dockstore.yaml.DockstoreYamlHelper;
import ru.genespace.dockstore.yaml.DockstoreYamlHelper.DockstoreYamlException;
import ru.genespace.dockstore.yaml.YamlWorkflow;
import ru.genespace.github.GitHubRepository.GitReferenceInfo;
import ru.genespace.misc.CustomLoggedException;

public class GitHubManager
{
    public static final Logger LOG = LoggerFactory.getLogger( GitHubManager.class );

    //Process repository, get tags and branches, read .dockstore.yml for all branches, 
    //create list of ? WorkflowVersion (map to our Versions) and Workflows (map to Resources)
    public Map<String, Workflow> processRepository(String repositoryId) throws DockstoreYamlException
    {
        //TODO: pass user name and token
        String gitUsername = "ryabova.anna@gmail.com";
        String gitToken = "token_here";
        GitHubRepository repo = new GitHubRepository( gitUsername, gitToken, null );
        GHRepository repository = repo.getRepository( repositoryId );
        Map<String, GitReferenceInfo> references = new HashMap<>();
        try
        {
            GHRef[] refs = repo.getBranchesAndTags( repository );
            for ( GHRef ref : refs )
            {
                GitReferenceInfo gitReferenceInfo = repo.getRef( ref, repository );
                if( gitReferenceInfo != null )
                {
                    references.put( ref.getRef(), gitReferenceInfo );
                }
            }
        }
        catch (GHFileNotFoundException e)
        {
            // seems to legitimately do this when the repo has no tags or releases
            LOG.debug( "repo had no releases or tags: " + repositoryId, e );
        }
        catch (IOException e)
        {
            LOG.info( "%s: Cannot get branches or tags for workflow {}".formatted( gitUsername ), e );
            throw new CustomLoggedException( "Could not reach GitHub, please try again later" );
        }

        Map<String, Workflow> workflows = new HashMap<>();
        // For each branch (reference) found, create a workflow version and find the associated descriptor files
        for ( Entry<String, GitReferenceInfo> refEntry : references.entrySet() )
        {
            GitReferenceInfo ref = refEntry.getValue();
            String referenceStr = refEntry.getKey();
            if( ref != null )
            {
                final String branchName = ref.refName();
                final Date lastModified = ref.branchDate();
                final String commitId = ref.sha();
                Optional<SourceFile> ymlFile = repo.getDockstoreYml( repositoryId, referenceStr );

                if( ymlFile.isPresent() )
                {
                    SourceFile file = ymlFile.get();
                    DockstoreYaml12 yaml = null;
                    try
                    {
                        yaml = DockstoreYamlHelper.readAsDockstoreYaml12( file.getContent(), true );
                    }
                    catch (DockstoreYamlHelper.DockstoreYamlException ex)
                    {
                        String msg = "Invalid .dockstore.yml: " + ex.getMessage();
                        LOG.info( msg, ex );
                        continue;
                    }

                    List<YamlWorkflow> ymlworkflows = yaml.getWorkflows();
                    for ( YamlWorkflow w : ymlworkflows )
                    {
                        String wfName = w.getName();
                        String dockstoreWorkflowPath = "github.com/" + repository.getFullName() + (wfName != null && !wfName.isEmpty() ? "/" + wfName : "");
                        String subclass = w.getSubclass();

                        Workflow workflow = null;
                        if( workflows.containsKey( dockstoreWorkflowPath ) )
                            workflow = workflows.get( dockstoreWorkflowPath );
                        else
                        {
                            workflow = createNewWorkflow( repo, subclass, wfName, repository, repositoryId );
                            workflows.put( dockstoreWorkflowPath, workflow );
                        }
                        WorkflowVersion version = repo.addDockstoreYmlVersionToWorkflow( repositoryId, referenceStr, file, workflow, true );
                        //                        Workflow existingWorkflow = workflow;
                        //                        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();
                        //                        existingWorkflow.getWorkflowVersions().forEach( existingVersion -> existingDefaults.put( existingVersion.getReference(), existingVersion ) );
                        //                        repo.setupWorkflowVersions( repositoryId, workflow, Optional.of( existingWorkflow ), new HashMap<>(), Optional.empty(), false );
                        /**
                         * incorrect paths commented version 1 working block
                         * Workflow workflow = workflows.computeIfAbsent( dockstoreWorkflowPath, w1 ->
                         * createNewWorkflow( repo, subclass, dockstoreWorkflowPath, repository, repositoryId ) );
                         * //workflow.setDescriptorType( null ); String primaryDescriptorPath =
                         * w.getPrimaryDescriptorPath(); String fileContent = repo.readFileFromRepo(
                         * primaryDescriptorPath, ref.refName(), repository ); if( fileContent != null ) {
                         * WorkflowVersion version = new WorkflowVersion(); version.setName( branchName );
                         * version.setReference( branchName ); version.setLastModified( lastModified );
                         * version.setCommitID( commitId ); workflow.addWorkflowVersion( version ); }
                         */
                    }
                    //TODO: add to resources
                }
            }
        }
        return workflows;
    }

    private static AtomicLong wflId = new AtomicLong();

    private Workflow createNewWorkflow(GitHubRepository repo, String subclass, String name, GHRepository repository, String repositoryId)
    {
        String id = String.valueOf(wflId.getAndIncrement());
        return repo.initializeWorkflowFromGitHub( repositoryId, subclass, name, repository );
    }
}
