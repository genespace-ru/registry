package ru.genespace.github;

import static ru.genespace.dockstore.Constants.DOCKSTORE_YML_PATH;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.genespace.dockstore.AppTool;
import ru.genespace.dockstore.DescriptorLanguage;
import ru.genespace.dockstore.Notebook;
import ru.genespace.dockstore.SourceFile;
import ru.genespace.dockstore.Workflow;
import ru.genespace.dockstore.WorkflowMode;
import ru.genespace.dockstore.WorkflowVersion;
import ru.genespace.dockstore.WorkflowVersion.ReferenceType;
import ru.genespace.dockstore.yaml.DockstoreYaml12;
import ru.genespace.dockstore.yaml.DockstoreYamlHelper;
import ru.genespace.dockstore.yaml.DockstoreYamlHelper.DockstoreYamlException;
import ru.genespace.dockstore.yaml.YamlNotebook;
import ru.genespace.dockstore.yaml.YamlTool;
import ru.genespace.dockstore.yaml.YamlWorkflow;
import ru.genespace.github.GitHubRepository.GitReferenceInfo;
import ru.genespace.github.GitHubRepository.GitVisibility;
import ru.genespace.github.GitHubRepository.SourceControl;
import ru.genespace.misc.CustomLoggedException;

public class GitHubManager
{
    public static final Logger LOG = LoggerFactory.getLogger( GitHubManager.class );

    private String gitUsername;
    private String gitToken;

    public GitHubManager(String gitUsername, String gitToken)
    {
        this.gitUsername = gitUsername;
        this.gitToken = gitToken;

    }

    //Process repository, get tags and branches, read .dockstore.yml for all branches, 
    //create list of Workflows (map to our Resources). Workflow contains list of WorkflowVersion (map to our Versions) 
    public Map<String, Workflow> processRepository(String repositoryId) throws DockstoreYamlException
    {
        return processRepository( repositoryId, Integer.MAX_VALUE );
    }

    //Process repository, get tags and branches, read .dockstore.yml for all branches, 
    //create list of Workflows (map to our Resources). Workflow contains list of WorkflowVersion (map to our Versions) 
    //Stop when workflowNumberLimit of processed is reached (need for testing)
    public Map<String, Workflow> processRepository(String repositoryId, int workflowNumberLimit) throws DockstoreYamlException
    {
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

                    for ( YamlWorkflow yamlWorkflow : yaml.getWorkflows() )
                    {
                        String wfName = yamlWorkflow.getName();
                        String dockstoreWorkflowPath = "github.com/" + repository.getFullName() + (wfName != null && !wfName.isEmpty() ? "/" + wfName : "");

                        Workflow workflow = null;
                        if( workflows.containsKey( dockstoreWorkflowPath ) )
                            workflow = workflows.get( dockstoreWorkflowPath );
                        else
                        {
                            workflow = repo.initializeWorkflowFromGitHub( repositoryId, yamlWorkflow.getSubclass(), wfName );
                            workflows.put( dockstoreWorkflowPath, workflow );
                            if( workflows.size() >= workflowNumberLimit )
                                break;
                        }
                        WorkflowVersion version = repo.addDockstoreYmlVersionToWorkflow( repositoryId, referenceStr, file, workflow, true );
                        version.setName( branchName );
                    }
                    if( workflows.size() >= workflowNumberLimit )
                        break;
                    for ( YamlNotebook yamlNotebook : yaml.getNotebooks() )
                    {
                        String wfName = yamlNotebook.getName();
                        String dockstoreWorkflowPath = "github.com/" + repository.getFullName() + (wfName != null && !wfName.isEmpty() ? "/" + wfName : "");

                        Notebook workflow = null;
                        if( workflows.containsKey( dockstoreWorkflowPath ) )
                            workflow = (Notebook) workflows.get( dockstoreWorkflowPath );
                        else
                        {
                            workflow = repo.initializeNotebookFromGitHub( repositoryId, yamlNotebook.getFormat(), yamlNotebook.getLanguage(), wfName );
                            workflows.put( dockstoreWorkflowPath, workflow );
                            if( workflows.size() >= workflowNumberLimit )
                                break;
                        }
                        WorkflowVersion version = repo.addDockstoreYmlVersionToWorkflow( repositoryId, referenceStr, file, workflow, true );
                        version.setName( branchName );
                    }

                    if( workflows.size() >= workflowNumberLimit )
                        break;
                    for ( YamlTool yamlTool : yaml.getTools() )
                    {
                        String wfName = yamlTool.getName();
                        String dockstoreWorkflowPath = "github.com/" + repository.getFullName() + (wfName != null && !wfName.isEmpty() ? "/" + wfName : "");

                        AppTool workflow = null;
                        if( workflows.containsKey( dockstoreWorkflowPath ) )
                            workflow = (AppTool) workflows.get( dockstoreWorkflowPath );
                        else
                        {
                            workflow = repo.initializeOneStepWorkflowFromGitHub( repositoryId, yamlTool.getSubclass(), wfName );
                            workflows.put( dockstoreWorkflowPath, workflow );
                            if( workflows.size() >= workflowNumberLimit )
                                break;
                        }
                        WorkflowVersion version = repo.addDockstoreYmlVersionToWorkflow( repositoryId, referenceStr, file, workflow, true );
                        version.setName( branchName );
                    }
                }
            }
        }
        return workflows;
    }


    public Map<String, Workflow> processRepositoryTest(String repositoryId) throws DockstoreYamlException
    {
        Workflow workflow = new Workflow();
        String testName = "align_and_metrics";
        workflow.setWorkflowName( testName );
        DescriptorLanguage descriptorLanguage = DescriptorLanguage.convertShortStringToEnum( "WDL" );
        workflow.setDescriptorType( descriptorLanguage );
        workflow.setOrganization( repositoryId.split( "/" )[0] );
        workflow.setRepository( repositoryId.split( "/" )[1] );
        workflow.setSourceControl( SourceControl.GITHUB );
        workflow.setGitUrl( "git@github.com:" + repositoryId + ".git" );
        workflow.setLastUpdated( new Date() );
        workflow.setDefaultWorkflowPath( DOCKSTORE_YML_PATH );
        workflow.setMode( WorkflowMode.DOCKSTORE_YML );

        //workflow.setTopicAutomatic( repository.getDescription() );
        workflow.setGitVisibility( GitVisibility.PUBLIC );

        WorkflowVersion version = new WorkflowVersion();
        version.setName( "commit_name" );
        version.setCommitID( "320c60b61a76a23e937a5ad4ac9546a7a77190c5" );
        version.setValid( false );
        version.setWorkflowPath( "/wdl-training/exercise3/solution/align_and_metrics.wdl" );
        version.setReferenceType( ReferenceType.BRANCH );
        version.setLastModified( new Date() );

        workflow.addWorkflowVersion( version );

        WorkflowVersion version2 = new WorkflowVersion();
        version2.setName( "commit_name2" );
        version2.setCommitID( "320c60b61a76a23e937a5ad4ac9546a7a77190c5" );
        version2.setValid( false );
        version2.setWorkflowPath( "/wdl-training/exercise3/solution/align_and_metrics.wdl" );
        version2.setReferenceType( ReferenceType.BRANCH );
        version2.setLastModified( new Date() );

        workflow.addWorkflowVersion( version2 );

        String dockstoreWorkflowPath = "github.com/" + repositoryId + testName;

        Map<String, Workflow> workflows = new HashMap<>();
        workflows.put( dockstoreWorkflowPath, workflow );
        return workflows;
    }
}
