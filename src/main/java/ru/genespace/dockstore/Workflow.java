package ru.genespace.dockstore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ru.genespace.github.GitHubRepository.GitVisibility;
import ru.genespace.github.GitHubRepository.SourceControl;

public class Workflow
{
    //This is the name of the workflow, not needed when only one workflow in a repo
    private String workflowName;
    //This is a git organization for the workflow
    private String organization;

    //This is a git repository name
    private String repository;
    //This is a descriptor type for the workflow, by default either SMK, CWL, WDL, NFL, or gxformat2 (Defaults to CWL)
    private DescriptorLanguage descriptorType;

    //This indicates what mode this is in which informs how we do things like refresh, dockstore specific
    private WorkflowMode mode = WorkflowMode.STUB;

    private SortedSet<WorkflowVersion> workflowVersions;

    private WorkflowVersion actualDefaultVersion;

    private Map<DescriptorLanguage.FileType, String> defaultPaths = new HashMap<>();

    //This is a descriptor type subclass for the workflow. Currently it is only used for services and notebooks
    private DescriptorLanguageSubclass descriptorTypeSubclass = DescriptorLanguageSubclass.NOT_APPLICABLE;

    private Date lastUpdated;

    //This is a specific source control provider like github, bitbucket, gitlab, etc.", required = true, position = 17, dataType = "string")
    private SourceControl sourceControl;

    private GitVisibility gitVisibility;

    public Workflow()
    {
        workflowVersions = new TreeSet<>();
    }

    public Workflow(long id, String workflowName)
    {
        this.workflowName = workflowName;
        workflowVersions = new TreeSet<>();
    }

    //This is a link to the associated repo with a descriptor, required GA4GH
    private String gitUrl;

    public String getGitUrl()
    {
        return gitUrl;
    }

    public void setGitUrl(String gitUrl)
    {
        this.gitUrl = gitUrl;
    }

    public String getWorkflowName()
    {
        return workflowName;
    }

    public Set<WorkflowVersion> getWorkflowVersions()
    {
        return this.workflowVersions;
    }

    public void setWorkflowVersionsOverride(SortedSet<WorkflowVersion> newWorkflowVersions)
    {
        this.workflowVersions = newWorkflowVersions;
    }

    /**
     * @param workflowName the repo name to set
     */
    public void setWorkflowName(String workflowName)
    {
        this.workflowName = workflowName;
    }

    public String getOrganization()
    {
        return organization;
    }

    public void setOrganization(String organization)
    {
        this.organization = organization;
    }

    public String getRepository()
    {
        return repository;
    }

    public void setRepository(String repository)
    {
        this.repository = repository;
    }

    public void setActualDefaultVersion(WorkflowVersion version)
    {
        this.actualDefaultVersion = version;
    }

    public WorkflowVersion getActualDefaultVersion()
    {
        return this.actualDefaultVersion;
    }

    public boolean addWorkflowVersion(WorkflowVersion workflowVersion)
    {
        //workflowVersion.setParent(this);
        return getWorkflowVersions().add( workflowVersion );
    }

    public DescriptorLanguage.FileType getFileType()
    {
        return this.getDescriptorType().getFileType();
    }

    public void setDescriptorType(DescriptorLanguage descriptorType)
    {
        this.descriptorType = descriptorType;
    }

    public DescriptorLanguage getDescriptorType()
    {
        // due to DB constraints, this should only come into play with newly created, non-persisted Workflows
        return Objects.requireNonNullElse( this.descriptorType, DescriptorLanguage.CWL );
    }

    public EntryTypeMetadata getEntryTypeMetadata()
    {
        return EntryTypeMetadata.WORKFLOW;
    }

    public EntryType getEntryType()
    {
        return getEntryTypeMetadata().getType();
    }

    public WorkflowMode getMode()
    {
        return mode;
    }

    public boolean isHosted()
    {
        return getMode().equals( WorkflowMode.HOSTED );
    }

    public void setMode(WorkflowMode mode)
    {
        this.mode = mode;
    }

    public Map<DescriptorLanguage.FileType, String> getDefaultPaths()
    {
        return defaultPaths;
    }

    public void setDefaultPaths(Map<DescriptorLanguage.FileType, String> defaultPaths)
    {
        this.defaultPaths = defaultPaths;
    }

    public String getPath()
    {
        return sourceControl.toString() + '/' + organization + '/' + repository;
    }

    public String getWorkflowPath()
    {
        return getPath() + (workflowName == null || "".equals( workflowName ) ? "" : '/' + workflowName);
    }

    public String getEntryPath()
    {
        return this.getWorkflowPath();
    }

    public DescriptorLanguageSubclass getDescriptorTypeSubclass()
    {
        return descriptorTypeSubclass;
    }

    public void setDescriptorTypeSubclass(final DescriptorLanguageSubclass descriptorTypeSubclass)
    {
        this.descriptorTypeSubclass = descriptorTypeSubclass;
    }

    public DescriptorLanguage.FileType getTestParameterType()
    {
        return this.getDescriptorType().getTestParamType();
    }

    public SourceControl getSourceControl()
    {
        return sourceControl;
    }

    public void setSourceControl(SourceControl sourceControl)
    {
        this.sourceControl = sourceControl;
    }

    public Date getLastUpdated()
    {
        if( lastUpdated == null )
        {
            return new Date( 0L );
        }
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated)
    {
        this.lastUpdated = lastUpdated;
    }

    //This indicates for the associated git repository, the default path to the primary descriptor document", required = true, position = 19)
    public String getDefaultWorkflowPath()
    {
        return getDefaultPaths().getOrDefault( this.getDescriptorType().getFileType(), "/Dockstore.cwl" );
    }

    //TODO: odd side effect, this means that if the descriptor language is set wrong, we will get or set the wrong the default paths
    public void setDefaultWorkflowPath(String defaultWorkflowPath)
    {
        getDefaultPaths().put( this.getDescriptorType().getFileType(), defaultWorkflowPath );
    }

    public GitVisibility getGitVisibility()
    {
        return gitVisibility;
    }

    public void setGitVisibility(final GitVisibility gitVisibility)
    {
        this.gitVisibility = gitVisibility;
    }

}
