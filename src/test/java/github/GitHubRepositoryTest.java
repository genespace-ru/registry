package github;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import ru.genespace.dockstore.*;
import ru.genespace.dockstore.yaml.YamlAuthor;
import ru.genespace.github.GitHubFileContentProvider;
import ru.genespace.github.GitHubRepository;

/**
 * Unit tests for GitHubRepository using mocked GitHub API objects.
 * No real GitHub connections are made.
 */
public class GitHubRepositoryTest
{

    /**
     * Subclass that accepts a pre-configured mock GitHub instance,
     * avoiding any real network calls during construction.
     */
    private static class TestableGitHubRepository extends GitHubRepository
    {
        private TestableGitHubRepository(GitHub mockGitHub)
        {
            super(null, null); // super will try to build, but we override github field
            // The parent constructor sets github via builder; we replace it.
            try
            {
                java.lang.reflect.Field f = GitHubRepository.class.getDeclaredField("github");
                f.setAccessible(true);
                f.set(this, mockGitHub);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failed to inject mock GitHub", e);
            }
        }
    }

    private GitHub mockGitHub;
    private GHRepository mockRepo;
    private TestableGitHubRepository repo;

    @Before
    public void setUp() throws Exception
    {
        mockGitHub = mock(GitHub.class);
        mockRepo = mock(GHRepository.class);

        when(mockGitHub.getRepository(ArgumentMatchers.anyString())).thenReturn(mockRepo);
        when(mockRepo.getFullName()).thenReturn("test-org/test-repo");
        when(mockRepo.getName()).thenReturn("test-repo");
        when(mockRepo.getDescription()).thenReturn("Test repository");
        when(mockRepo.isPrivate()).thenReturn(false);
        when(mockRepo.getDefaultBranch()).thenReturn("main");
        // Default: return a dummy branch so reference checks pass
        GHRef dummyBranch = mock(GHRef.class);
        when(dummyBranch.getRef()).thenReturn("refs/heads/main");
        when(mockRepo.getRefs(ArgumentMatchers.anyString())).thenAnswer(inv -> {
            String refType = inv.getArgument(0);
            if (refType != null && refType.contains("heads")) {
                return new GHRef[]{dummyBranch};
            }
            return new GHRef[0];
        });
        // Default: no tags, so listTags().iterator().hasNext() returns false
        when(mockRepo.listTags()).thenAnswer(invocation -> {
            org.kohsuke.github.PagedIterable pagedMock = Mockito.mock(org.kohsuke.github.PagedIterable.class);
            org.kohsuke.github.PagedIterator pagedIter = Mockito.mock(org.kohsuke.github.PagedIterator.class);
            Mockito.when(pagedIter.hasNext()).thenReturn(false);
            Mockito.when(pagedMock.iterator()).thenReturn(pagedIter);
            return pagedMock;
        });

        repo = new TestableGitHubRepository(mockGitHub);
    }

    // -----------------------------------------------------------------------
    //  Constructor & basic setup
    // -----------------------------------------------------------------------

    @Test
    public void testConstructor_doesNotThrowWithMock()
    {
        // If we reach here without exception, the mock injection worked.
        assertNotNull("GitHubRepository should be created", repo);
    }

    // -----------------------------------------------------------------------
    //  getRepository  (caching + call counter)
    // -----------------------------------------------------------------------

    @Test
    public void testGetRepository_returnsMockRepo()
    {
        GHRepository result = repo.getRepository("test-org/test-repo");
        assertSame("Should return the mocked GHRepository", mockRepo, result);
    }

    @Test
    public void testGetRepository_cachesResult()
    {
        repo.getRepository("test-org/test-repo");
        repo.getRepository("test-org/test-repo");

        // Second call should hit the cache, not invoke github.getRepository again
        try
        {
            verify(mockGitHub, times(1)).getRepository("test-org/test-repo");
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetRepository_throwsCustomLoggedExceptionOnIoError() throws Exception
    {
        when(mockGitHub.getRepository("fail-repo")).thenThrow(new IOException("network error"));

        try
        {
            repo.getRepository("fail-repo");
            fail("Expected RuntimeException");
        }
        catch (RuntimeException e)
        {
            assertTrue("Message should mention GitHub",
                    e.getMessage().contains("GitHub"));
        }
    }

    @Test
    public void testGetRepoCallCounter_increments()
    {
        assertEquals(0, repo.getRepoCallCounter());
        repo.getRepository("test-org/test-repo");
        assertEquals(1, repo.getRepoCallCounter());
        repo.getRepository("test-org/test-repo");
        assertEquals(2, repo.getRepoCallCounter());
    }

    // -----------------------------------------------------------------------
    //  getDockstoreYml
    // -----------------------------------------------------------------------

    @Test
    public void testGetDockstoreYml_returnsContentWhenFound() throws Exception
    {
        String ymlContent = "version: 1.2\nworkflows:\n  - path: main.wdl\n";
        // Mock the .dockstore.yml file at root
        GHContent ymlFile = mock(GHContent.class);
        when(ymlFile.getContent()).thenReturn(ymlContent);
        when(ymlFile.getType()).thenReturn("blob");
        when(ymlFile.getPath()).thenReturn(".dockstore.yml");
        when(ymlFile.isDirectory()).thenReturn(false);
        when(ymlFile.getEncoding()).thenReturn("base64");
        // Root directory listing must include .dockstore.yml - use anyString for path
        when(mockRepo.getDirectoryContent(anyString(), anyString()))
                .thenReturn(Collections.singletonList(ymlFile));
        when(mockRepo.getFileContent(anyString(), anyString())).thenReturn(ymlFile);

        Optional<SourceFile> result = repo.getDockstoreYml("test-org/test-repo", "main");

        assertTrue("Should find .dockstore.yml", result.isPresent());
        assertEquals(ymlContent, result.get().getContent());
    }

    @Test
    public void testGetDockstoreYml_returnsEmptyWhenNotFound() throws Exception
    {
        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.emptyList());

        Optional<SourceFile> result = repo.getDockstoreYml("test-org/test-repo", "main");

        assertFalse("Should be empty when .dockstore.yml not found", result.isPresent());
    }

    // -----------------------------------------------------------------------
    //  readFileFromRepo
    // -----------------------------------------------------------------------

    @Test
    public void testReadFileFromRepo_returnsContent() throws Exception
    {
        String fileContent = "workflow hello {}";
        GHContent mockContent = mock(GHContent.class);
        when(mockContent.getContent()).thenReturn(fileContent);
        when(mockContent.getType()).thenReturn("blob");
        when(mockContent.getEncoding()).thenReturn("utf-8");
        when(mockContent.getPath()).thenReturn("main.wdl");
        when(mockContent.getSize()).thenReturn(100L);

        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.singletonList(mockContent));
        when(mockRepo.getFileContent(ArgumentMatchers.eq("main.wdl"), ArgumentMatchers.anyString())).thenReturn(mockContent);

        String result = repo.readFileFromRepo("main.wdl", "main", mockRepo);

        assertEquals(fileContent, result);
    }

    @Test
    public void testReadFileFromRepo_returnsNullWhenFileNotFound() throws Exception
    {
        when(mockRepo.getFileContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(null);

        String result = repo.readFileFromRepo("missing.txt", "main", mockRepo);

        assertNull("Should return null for missing file", result);
    }

    @Test
    public void testReadFileFromRepo_handlesLargeFilesViaBlob() throws Exception
    {
        GHContent mockContent = mock(GHContent.class);
        when(mockContent.getContent()).thenReturn("");
        when(mockContent.getEncoding()).thenReturn("none");
        when(mockContent.getSize()).thenReturn(2L * 1024 * 1024); // 2 MB (over 1MB threshold but under 10MB limit)
        when(mockContent.getSha()).thenReturn("abc123");
        when(mockContent.getPath()).thenReturn("big.bin");
        when(mockContent.getType()).thenReturn("blob");

        GHBlob mockBlob = mock(GHBlob.class);
        when(mockBlob.read()).thenReturn(new java.io.ByteArrayInputStream("large file content".getBytes("UTF-8")));
        when(mockRepo.getBlob("abc123")).thenReturn(mockBlob);

        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.singletonList(mockContent));
        when(mockRepo.getFileContent(ArgumentMatchers.eq("big.bin"), ArgumentMatchers.anyString())).thenReturn(mockContent);

        String result = repo.readFileFromRepo("big.bin", "main", mockRepo);

        assertEquals("large file content", result);
    }

    @Test
    public void testReadFileFromRepo_returnsErrorForTooLargeFiles() throws Exception
    {
        GHContent mockContent = mock(GHContent.class);
        when(mockContent.getContent()).thenReturn("");
        when(mockContent.getEncoding()).thenReturn("none");
        when(mockContent.getSize()).thenReturn(20L * 1024 * 1024 * 1024); // 20 GB
        when(mockContent.getPath()).thenReturn("huge.bin");
        when(mockContent.getType()).thenReturn("blob");

        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.singletonList(mockContent));
        when(mockRepo.getFileContent(ArgumentMatchers.eq("huge.bin"), ArgumentMatchers.anyString())).thenReturn(mockContent);

        String result = repo.readFileFromRepo("huge.bin", "main", mockRepo);

        assertEquals("Dockstore does not process extremely large files", result);
    }

    @Test
    public void testReadFileFromRepo_handlesIOException() throws Exception
    {
        when(mockRepo.getFileContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenThrow(new IOException("read failed"));

        String result = repo.readFileFromRepo("error.txt", "main", mockRepo);

        assertNull("Should return null on IOException", result);
    }

    // -----------------------------------------------------------------------
    //  getBranchesAndTags
    // -----------------------------------------------------------------------

    @Test
    public void testGetBranchesAndTags_returnsBranchesAndTags() throws Exception
    {
        GHRef mockBranch = mock(GHRef.class);
        when(mockBranch.getRef()).thenReturn("refs/heads/main");
        GHRef mockTag = mock(GHRef.class);
        when(mockTag.getRef()).thenReturn("refs/tags/v1.0");

        when(mockRepo.getRefs("refs/heads/")).thenReturn(new GHRef[]{mockBranch});
        // Return non-empty iterator so code proceeds to get tags
        when(mockRepo.listTags()).thenAnswer(invocation -> {
            org.kohsuke.github.PagedIterable pagedMock = Mockito.mock(org.kohsuke.github.PagedIterable.class);
            org.kohsuke.github.PagedIterator pagedIter = Mockito.mock(org.kohsuke.github.PagedIterator.class);
            Mockito.when(pagedIter.hasNext()).thenReturn(true);
            Mockito.when(pagedIter.next()).thenReturn(null);
            Mockito.when(pagedMock.iterator()).thenReturn(pagedIter);
            return pagedMock;
        });
        when(mockRepo.getRefs("refs/tags/")).thenReturn(new GHRef[]{mockTag});

        GHRef[] result = repo.getBranchesAndTags(mockRepo);

        assertEquals(2, result.length);
    }

    @Test
    public void testGetBranchesAndTags_returnsEmptyWhenNoBranchesOrTags() throws Exception
    {
        when(mockRepo.getRefs("refs/heads/"))
                .thenThrow(new GHFileNotFoundException("no branches"));
        when(mockRepo.getRefs("refs/tags/"))
                .thenThrow(new GHFileNotFoundException("no tags"));

        GHRef[] result = repo.getBranchesAndTags(mockRepo);

        assertNotNull("Should not throw", result);
        assertEquals(0, result.length);
    }

    // -----------------------------------------------------------------------
    //  getRef
    // -----------------------------------------------------------------------

    @Test
    public void testGetRef_returnsInfoForBranch() throws Exception
    {
        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/heads/feature");

        GHRef.GHObject mockObject = mock(GHRef.GHObject.class);
        when(mockObject.getType()).thenReturn("commit");
        when(mockObject.getSha()).thenReturn("abc123");
        when(mockRef.getObject()).thenReturn(mockObject);

        GHBranch mockBranch = mock(GHBranch.class);
        when(mockBranch.getSHA1()).thenReturn("abc123");

        GHCommit mockCommit = mock(GHCommit.class);
        Date commitDate = new Date();
        when(mockCommit.getCommitDate()).thenReturn(commitDate);

        when(mockRepo.getBranch("feature")).thenReturn(mockBranch);
        when(mockRepo.getCommit("abc123")).thenReturn(mockCommit);

        GitHubRepository.GitReferenceInfo result = repo.getRef(mockRef, mockRepo);

        assertNotNull(result);
        assertEquals("feature", result.refName());
        assertEquals("abc123", result.sha());
        assertEquals(commitDate, result.branchDate());
    }

    @Test
    public void testGetRef_returnsInfoForTag() throws Exception
    {
        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/tags/v1.0");

        GHRef.GHObject mockObject = mock(GHRef.GHObject.class);
        when(mockObject.getType()).thenReturn("commit");
        when(mockObject.getSha()).thenReturn("def456");
        when(mockRef.getObject()).thenReturn(mockObject);

        GHCommit mockCommit = mock(GHCommit.class);
        Date commitDate = new Date();
        when(mockCommit.getCommitDate()).thenReturn(commitDate);

        when(mockRepo.getCommit("def456")).thenReturn(mockCommit);

        GitHubRepository.GitReferenceInfo result = repo.getRef(mockRef, mockRepo);

        assertNotNull(result);
        assertEquals("v1.0", result.refName());
        assertEquals("def456", result.sha());
    }

    @Test
    public void testGetRef_returnsNullForPullRequests() throws Exception
    {
        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/pull/42/head");

        GitHubRepository.GitReferenceInfo result = repo.getRef(mockRef, mockRepo);

        assertNull("Pull request refs should be ignored", result);
    }

    @Test
    public void testGetRef_stripsRefsPrefix() throws Exception
    {
        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/heads/long/branch/name");

        GHRef.GHObject mockObject = mock(GHRef.GHObject.class);
        when(mockObject.getType()).thenReturn("commit");
        when(mockObject.getSha()).thenReturn("abc123");
        when(mockRef.getObject()).thenReturn(mockObject);

        GHCommit mockCommit = mock(GHCommit.class);
        when(mockCommit.getCommitDate()).thenReturn(new Date());

        when(mockRepo.getCommit("abc123")).thenReturn(mockCommit);

        GitHubRepository.GitReferenceInfo result = repo.getRef(mockRef, mockRepo);

        assertEquals("long/branch/name", result.refName());
    }

    // -----------------------------------------------------------------------
    //  initializeWorkflowVersion (protected, uses reflection)
    // -----------------------------------------------------------------------

    private WorkflowVersion callInitializeWorkflowVersion(String branch, Optional<Workflow> workflow, Map<String, WorkflowVersion> existingDefaults) throws Exception
    {
        java.lang.reflect.Method m = GitHubRepository.class
                .getDeclaredMethod("initializeWorkflowVersion",
                        String.class, Optional.class, Map.class);
        m.setAccessible(true);
        return (WorkflowVersion) m.invoke(repo, branch, workflow, existingDefaults);
    }

    @Test
    public void testInitializeWorkflowVersion_newVersion() throws Exception
    {
        Workflow workflow = new Workflow();
        workflow.setDefaultWorkflowPath("/main.wdl");

        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();

        WorkflowVersion version = callInitializeWorkflowVersion("main", Optional.of(workflow), existingDefaults);

        assertEquals("main", version.getName());
        assertEquals("main", version.getReference());
        assertFalse(version.isValid());
        assertTrue(version.isSynced());
        assertFalse(version.isDirtyBit());
        assertEquals("/main.wdl", version.getWorkflowPath());
    }

    @Test
    public void testInitializeWorkflowVersion_existingDirtyVersion() throws Exception
    {
        Workflow workflow = new Workflow();
        workflow.setDefaultWorkflowPath("/main.wdl");

        WorkflowVersion existingVersion = new WorkflowVersion();
        existingVersion.setName("main");
        existingVersion.setWorkflowPath("/custom.wdl");
        existingVersion.setDirtyBit(true);

        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();
        existingDefaults.put("main", existingVersion);

        WorkflowVersion version = callInitializeWorkflowVersion("main", Optional.of(workflow), existingDefaults);

        assertEquals("/custom.wdl", version.getWorkflowPath());
        assertTrue(version.isDirtyBit());
    }

    @Test
    public void testInitializeWorkflowVersion_existingCleanVersion() throws Exception
    {
        Workflow workflow = new Workflow();
        workflow.setDefaultWorkflowPath("/main.wdl");

        WorkflowVersion existingVersion = new WorkflowVersion();
        existingVersion.setName("main");
        existingVersion.setWorkflowPath("/custom.wdl");
        existingVersion.setDirtyBit(false);

        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();
        existingDefaults.put("main", existingVersion);

        WorkflowVersion version = callInitializeWorkflowVersion("main", Optional.of(workflow), existingDefaults);

        // Clean existing version should use default path
        assertEquals("/main.wdl", version.getWorkflowPath());
        assertFalse(version.isDirtyBit());
    }

    // -----------------------------------------------------------------------
    //  toRefreshVersion (protected, uses reflection)
    // -----------------------------------------------------------------------

    private boolean callToRefreshVersion(String commitId, WorkflowVersion existing, boolean hardRefresh) throws Exception
    {
        java.lang.reflect.Method m = GitHubRepository.class
                .getDeclaredMethod("toRefreshVersion",
                        String.class, WorkflowVersion.class, boolean.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(repo, commitId, existing, hardRefresh);
    }

    @Test
    public void testToRefreshVersion_hardRefresh() throws Exception
    {
        WorkflowVersion existing = new WorkflowVersion();
        existing.setCommitID("abc123");

        assertTrue(callToRefreshVersion("abc123", existing, true));
    }

    @Test
    public void testToRefreshVersion_nullExistingVersion() throws Exception
    {
        assertTrue(callToRefreshVersion("abc123", null, false));
    }

    @Test
    public void testToRefreshVersion_nullCommitId() throws Exception
    {
        WorkflowVersion existing = new WorkflowVersion();
        existing.setCommitID(null);

        assertTrue(callToRefreshVersion("abc123", existing, false));
    }

    @Test
    public void testToRefreshVersion_differentCommitId() throws Exception
    {
        WorkflowVersion existing = new WorkflowVersion();
        existing.setCommitID("abc123");

        assertTrue(callToRefreshVersion("def456", existing, false));
    }

    @Test
    public void testToRefreshVersion_sameCommitId_noRefresh() throws Exception
    {
        WorkflowVersion existing = new WorkflowVersion();
        existing.setCommitID("abc123");

        assertFalse(callToRefreshVersion("abc123", existing, false));
    }

    // -----------------------------------------------------------------------
    //  isValidOrcidId
    // -----------------------------------------------------------------------

    @Test
    public void testIsValidOrcidId_valid()
    {
        assertTrue(GitHubRepository.isValidOrcidId("0000-0002-1825-009X"));
        assertTrue(GitHubRepository.isValidOrcidId("0000-0002-1825-0099"));
    }

    @Test
    public void testIsValidOrcidId_invalid()
    {
        assertFalse(GitHubRepository.isValidOrcidId(""));
        assertFalse(GitHubRepository.isValidOrcidId("0000-0002-1825-009"));
        assertFalse(GitHubRepository.isValidOrcidId("not-an-orcid"));
    }

    // -----------------------------------------------------------------------
    //  getDefaultBranch
    // -----------------------------------------------------------------------

    @Test
    public void testGetDefaultBranch_returnsMain()
    {
        String result = repo.getDefaultBranch("test-org/test-repo");
        assertEquals("main", result);
    }

    @Test
    public void testGetDefaultBranch_nullId_returnsNull()
    {
        String result = repo.getDefaultBranch(null);
        assertNull(result);
    }

    // -----------------------------------------------------------------------
    //  getGitVisibility
    // -----------------------------------------------------------------------

    @Test
    public void testGetGitVisibility_publicRepo() throws Exception
    {
        when(mockRepo.isPrivate()).thenReturn(false);
        when(mockGitHub.getRepository("public-repo")).thenReturn(mockRepo);

        GitHubRepository.GitVisibility result = repo.getGitVisibility("public-repo");
        assertEquals(GitHubRepository.GitVisibility.PUBLIC, result);
    }

    @Test
    public void testGetGitVisibility_privateRepo() throws Exception
    {
        when(mockRepo.isPrivate()).thenReturn(true);
        when(mockGitHub.getRepository("private-repo")).thenReturn(mockRepo);

        GitHubRepository.GitVisibility result = repo.getGitVisibility("private-repo");
        assertEquals(GitHubRepository.GitVisibility.PRIVATE, result);
    }

    @Test
    public void testGetGitVisibility_notFound_returnsPrivateOrNonExistent() throws Exception
    {
        when(mockGitHub.getRepository("missing-repo"))
                .thenThrow(new GHFileNotFoundException("404 Not Found"));

        GitHubRepository.GitVisibility result = repo.getGitVisibility("missing-repo");
        assertEquals(GitHubRepository.GitVisibility.PRIVATE_OR_NON_EXISTENT, result);
    }

    @Test
    public void testGetGitVisibility_ioException_returnsUnknown() throws Exception
    {
        when(mockGitHub.getRepository("error-repo")).thenThrow(new IOException("network"));

        GitHubRepository.GitVisibility result = repo.getGitVisibility("error-repo");
        assertEquals(GitHubRepository.GitVisibility.UNKNOWN, result);
    }

    // -----------------------------------------------------------------------
    //  initializeWorkflowFromGitHub
    // -----------------------------------------------------------------------

    @Test
    public void testInitializeWorkflowFromGitHub_setsProperties()
    {
        Workflow workflow = repo.initializeWorkflowFromGitHub("test-org/test-repo", "WDL", "my-workflow");

        assertEquals("my-workflow", workflow.getWorkflowName());
        assertEquals("test-org", workflow.getOrganization());
        assertEquals("test-repo", workflow.getRepository());
        assertEquals(DescriptorLanguage.WDL, workflow.getDescriptorType());
        assertEquals("Test repository", workflow.getTopic());
        assertEquals(GitHubRepository.GitVisibility.PUBLIC, workflow.getGitVisibility());
    }

    @Test
    public void testInitializeWorkflowFromGitHub_nextflow()
    {
        Workflow workflow = repo.initializeWorkflowFromGitHub("org/repo", "NFL", "nf-workflow");

        assertEquals(DescriptorLanguage.NEXTFLOW, workflow.getDescriptorType());
        assertEquals("nf-workflow", workflow.getWorkflowName());
    }

    @Test
    public void testInitializeWorkflowFromGitHub_cwl()
    {
        Workflow workflow = repo.initializeWorkflowFromGitHub("org/repo", "CWL", "cwl-tool");

        assertEquals(DescriptorLanguage.CWL, workflow.getDescriptorType());
    }

    // -----------------------------------------------------------------------
    //  initializeNotebookFromGitHub
    // -----------------------------------------------------------------------

    @Test
    public void testInitializeNotebookFromGitHub()
    {
        // Use WDL format with n/a subclass (same as workflow init)
        Notebook notebook = repo.initializeNotebookFromGitHub("org/repo", "WDL", "n/a", "notebook1");

        assertEquals("notebook1", notebook.getWorkflowName());
        assertEquals("org", notebook.getOrganization());
        assertEquals("repo", notebook.getRepository());
    }

    // -----------------------------------------------------------------------
    //  initializeOneStepWorkflowFromGitHub
    // -----------------------------------------------------------------------

    @Test
    public void testInitializeOneStepWorkflowFromGitHub()
    {
        AppTool appTool = repo.initializeOneStepWorkflowFromGitHub("org/repo", "WDL", "one-step-tool");

        assertEquals("one-step-tool", appTool.getWorkflowName());
        assertEquals(DescriptorLanguage.WDL, appTool.getDescriptorType());
    }

    // -----------------------------------------------------------------------
    //  GitBranchTagPattern
    // -----------------------------------------------------------------------

    @Test
    public void testGitBranchTagPattern_validBranch()
    {
        Matcher m = GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/main");
        assertTrue(m.find());
        assertEquals("heads", m.group(1));
        assertEquals("main", m.group(2));
    }

    @Test
    public void testGitBranchTagPattern_validTag()
    {
        Matcher m = GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/tags/v1.0.0");
        assertTrue(m.find());
        assertEquals("tags", m.group(1));
        assertEquals("v1.0.0", m.group(2));
    }

    @Test
    public void testGitBranchTagPattern_validNestedBranch()
    {
        Matcher m = GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/feature/new-feature");
        assertTrue(m.find());
        assertEquals("feature/new-feature", m.group(2));
    }

    @Test
    public void testGitBranchTagPattern_rejectsDoubleSlash()
    {
        Matcher m = GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/feature//bad");
        assertFalse(m.find());
    }

    @Test
    public void testGitBranchTagPattern_rejectsDoubleDot()
    {
        Matcher m = GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/feature/../bad");
        assertFalse(m.find());
    }

    @Test
    public void testGitBranchTagPattern_rejectsSpecialChars()
    {
        assertFalse(GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/bad@tag").find());
        assertFalse(GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/bad[tag]").find());
        assertFalse(GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/bad?tag").find());
        assertFalse(GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/bad~tag").find());
        assertFalse(GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/bad\\tag").find());
        assertFalse(GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/bad^tag").find());
        assertFalse(GitHubRepository.GIT_BRANCH_TAG_PATTERN.matcher("refs/heads/bad:tag").find());
    }

    // -----------------------------------------------------------------------
    //  SourceControl enum
    // -----------------------------------------------------------------------

    @Test
    public void testSourceControl_values()
    {
        assertEquals("github.com", GitHubRepository.SourceControl.GITHUB.toString());
        assertEquals("GitHub", GitHubRepository.SourceControl.GITHUB.getFriendlyName());

        assertEquals("dockstore.org", GitHubRepository.SourceControl.DOCKSTORE.toString());
        assertEquals("Dockstore", GitHubRepository.SourceControl.DOCKSTORE.getFriendlyName());

        assertEquals("gitlab.com", GitHubRepository.SourceControl.GITLAB.toString());
        assertEquals("bitbucket.org", GitHubRepository.SourceControl.BITBUCKET.toString());
    }

    // -----------------------------------------------------------------------
    //  GitVisibility enum
    // -----------------------------------------------------------------------

    @Test
    public void testGitVisibility_values()
    {
        assertEquals(GitHubRepository.GitVisibility.UNKNOWN,
                GitHubRepository.GitVisibility.valueOf("UNKNOWN"));
        assertEquals(GitHubRepository.GitVisibility.PRIVATE,
                GitHubRepository.GitVisibility.valueOf("PRIVATE"));
        assertEquals(GitHubRepository.GitVisibility.PUBLIC,
                GitHubRepository.GitVisibility.valueOf("PUBLIC"));
        assertEquals(GitHubRepository.GitVisibility.PRIVATE_OR_NON_EXISTENT,
                GitHubRepository.GitVisibility.valueOf("PRIVATE_OR_NON_EXISTENT"));
    }

    // -----------------------------------------------------------------------
    //  GitReferenceInfo record
    // -----------------------------------------------------------------------

    @Test
    public void testGitReferenceInfo_record()
    {
        Date testDate = new Date(1234567890L);
        GitHubRepository.GitReferenceInfo info =
                new GitHubRepository.GitReferenceInfo("main", testDate, "abc123");

        assertEquals("main", info.refName());
        assertEquals(testDate, info.branchDate());
        assertEquals("abc123", info.sha());
    }

    // -----------------------------------------------------------------------
    //  getContentAndMetadataForFileName
    // -----------------------------------------------------------------------

    @Test
    public void testGetContentAndMetadataForFileName_returnsContent() throws Exception
    {
        GHContent mockContent = mock(GHContent.class);
        when(mockContent.getContent()).thenReturn("workflow test {}");
        when(mockContent.getType()).thenReturn("blob");
        when(mockContent.getPath()).thenReturn("test.wdl");
        when(mockContent.isDirectory()).thenReturn(false);

        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/heads/main");

        when(mockRepo.getRefs("refs/heads/")).thenReturn(new GHRef[]{mockRef});
        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.singletonList(mockContent));
        when(mockRepo.getFileContent(ArgumentMatchers.eq("test.wdl"), ArgumentMatchers.anyString())).thenReturn(mockContent);

        Pair<GHContent, String> result =
                repo.getContentAndMetadataForFileName("test.wdl", "main", mockRepo, false);

        assertNotNull(result);
        assertEquals("workflow test {}", result.getRight());
    }

    @Test
    public void testGetContentAndMetadataForFileName_returnsNullForNonExistentReference() throws Exception
    {
        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/heads/main");

        when(mockRepo.getRefs("refs/heads/")).thenReturn(new GHRef[]{mockRef});
        // Directory content is empty — reference doesn't match
        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.emptyList());

        Pair<GHContent, String> result =
                repo.getContentAndMetadataForFileName("test.wdl", "nonexistent", mockRepo, false);

        assertNull(result);
    }

    @Test
    public void testGetContentAndMetadataForFileName_returnsNullForDirectory() throws Exception
    {
        GHContent mockDir = mock(GHContent.class);
        when(mockDir.isDirectory()).thenReturn(true);
        when(mockDir.getPath()).thenReturn("src");

        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/heads/main");

        when(mockRepo.getRefs("refs/heads/")).thenReturn(new GHRef[]{mockRef});
        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.singletonList(mockDir));

        Pair<GHContent, String> result =
                repo.getContentAndMetadataForFileName("src", "main", mockRepo, false);

        assertNull(result);
    }

    // -----------------------------------------------------------------------
    //  listFiles
    // -----------------------------------------------------------------------

    @Test
    public void testListFiles_returnsFileNames() throws Exception
    {
        GHContent file1 = mock(GHContent.class);
        when(file1.getName()).thenReturn("file1.txt");
        GHContent file2 = mock(GHContent.class);
        when(file2.getName()).thenReturn("file2.wdl");

        GHRef mockRef = mock(GHRef.class);
        when(mockRef.getRef()).thenReturn("refs/heads/main");

        when(mockRepo.getRefs("refs/heads/")).thenReturn(new GHRef[]{mockRef});
        when(mockRepo.getDirectoryContent("src", "main"))
                .thenReturn(Arrays.asList(file1, file2));

        List<String> result = repo.listFiles("test-org/test-repo", "src", "main");

        assertEquals(2, result.size());
        assertTrue(result.contains("file1.txt"));
        assertTrue(result.contains("file2.wdl"));
    }

    // -----------------------------------------------------------------------
    //  getWorkflowContent
    // -----------------------------------------------------------------------

    @Test
    public void testGetWorkflowContent_delegatesToLanguageHandler()
    {
        GitHubFileContentProvider mockProvider = mock(GitHubFileContentProvider.class);

        String result = repo.getWorkflowContent("org/repo", "main",
                "workflow test {}", "WDL", "/main.wdl", mockProvider);

        // The result depends on LanguageHandlerFactory, but the call should not throw
        assertNotNull(result);
    }

    // -----------------------------------------------------------------------
    //  resolveImports / resolveUserFiles
    // -----------------------------------------------------------------------

    @Test
    public void testResolveImports_delegatesToLanguageHandler()
    {
        WorkflowVersion version = new WorkflowVersion();
        Map<String, SourceFile> result = repo.resolveImports("org/repo",
                "workflow test {}", DescriptorLanguage.FileType.DOCKSTORE_WDL, version, "/main.wdl");

        // Returns empty map when no imports found
        assertNotNull(result);
    }

    @Test
    public void testResolveUserFiles_delegatesToLanguageHandler()
    {
        WorkflowVersion version = new WorkflowVersion();
        version.setUserFiles(Arrays.asList("test.json"));
        Map<String, SourceFile> result = repo.resolveUserFiles("org/repo",
                DescriptorLanguage.FileType.DOCKSTORE_WDL, version, Collections.emptySet());

        assertNotNull(result);
    }

    // -----------------------------------------------------------------------
    //  isValidVersion
    // -----------------------------------------------------------------------

    @Test
    public void testIsValidVersion_allValid()
    {
        WorkflowVersion version = new WorkflowVersion();
        Validation wdlValidation = new Validation(
                DescriptorLanguage.FileType.DOCKSTORE_WDL,
                new VersionTypeValidation(true, Collections.emptyMap()));
        version.addOrUpdateValidation(wdlValidation);

        assertTrue(repo.isValidVersion(version));
    }

    @Test
    public void testIsValidVersion_oneInvalid()
    {
        WorkflowVersion version = new WorkflowVersion();
        Validation wdlValidation = new Validation(
                DescriptorLanguage.FileType.DOCKSTORE_WDL,
                new VersionTypeValidation(false, Collections.singletonMap("err", "bad")));
        version.addOrUpdateValidation(wdlValidation);

        assertFalse(repo.isValidVersion(version));
    }

    @Test
    public void testIsValidVersion_dockstoreYmlInvalidButOthersValid()
    {
        WorkflowVersion version = new WorkflowVersion();
        // dockstore.yml validation failure should be ignored
        Validation dockstoreValidation = new Validation(
                DescriptorLanguage.FileType.DOCKSTORE_YML,
                new VersionTypeValidation(false, Collections.singletonMap("err", "bad")));
        Validation wdlValidation = new Validation(
                DescriptorLanguage.FileType.DOCKSTORE_WDL,
                new VersionTypeValidation(true, Collections.emptyMap()));
        version.addOrUpdateValidation(dockstoreValidation);
        version.addOrUpdateValidation(wdlValidation);

        assertTrue(repo.isValidVersion(version));
    }

    // -----------------------------------------------------------------------
    //  getReferenceTypeFromGitRef (via createVersionForWorkflow)
    // -----------------------------------------------------------------------

    @Test
    public void testGetReferenceTypeFromGitRef_branch() throws Exception
    {
        // Test that branch references are correctly identified via reflection
        Method method = GitHubRepository.class.getDeclaredMethod("getReferenceTypeFromGitRef", String.class);
        method.setAccessible(true);
        assertEquals(WorkflowVersion.ReferenceType.BRANCH, method.invoke(repo, "refs/heads/feature-branch"));
        assertEquals(WorkflowVersion.ReferenceType.TAG, method.invoke(repo, "refs/tags/v1.0.0"));
        assertEquals(WorkflowVersion.ReferenceType.NOT_APPLICABLE, method.invoke(repo, "refs/pull/123"));
        assertEquals(WorkflowVersion.ReferenceType.NOT_APPLICABLE, method.invoke(repo, "refs/heads"));
    }

    // -----------------------------------------------------------------------
    //  getGitVisibility(GHRepository) overload
    // -----------------------------------------------------------------------

    @Test
    public void testGetGitVisibility_withGHRepository()
    {
        when(mockRepo.isPrivate()).thenReturn(true);
        GitHubRepository.GitVisibility result = repo.getGitVisibility(mockRepo);
        assertEquals(GitHubRepository.GitVisibility.PRIVATE, result);
    }

    // -----------------------------------------------------------------------
    //  readGitRepositoryFile
    // -----------------------------------------------------------------------

    @Test
    public void testReadGitRepositoryFile_nullReference_returnsNull()
    {
        WorkflowVersion version = new WorkflowVersion();
        version.setReference(null);

        String result = repo.readGitRepositoryFile("org/repo",
                DescriptorLanguage.FileType.DOCKSTORE_WDL, version, "/main.wdl");

        assertNull(result);
    }

    // -----------------------------------------------------------------------
    //  readPath / readPaths
    // -----------------------------------------------------------------------

    @Test
    public void testReadPath_excludedPath_returnsEmpty()
    {
        Set<String> excludePaths = new HashSet<>();
        excludePaths.add("excluded.txt");

        List<SourceFile> result = repo.readPath("org/repo",
                new WorkflowVersion(), DescriptorLanguage.FileType.DOCKSTORE_WDL,
                excludePaths, "excluded.txt");

        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    //  getGhRateLimitQuietly
    // -----------------------------------------------------------------------

    @Test
    public void testGetGhRateLimitQuietly_returnsRateLimit() throws Exception
    {
        GHRateLimit mockRateLimit = mock(GHRateLimit.class);
        when(mockGitHub.getRateLimit()).thenReturn(mockRateLimit);

        GHRateLimit result = repo.getGhRateLimitQuietly();
        assertSame(mockRateLimit, result);
    }

    @Test
    public void testGetGhRateLimitQuietly_handlesIOException() throws Exception
    {
        when(mockGitHub.getRateLimit()).thenThrow(new IOException("rate limit error"));

        GHRateLimit result = repo.getGhRateLimitQuietly();
        assertNull("Should return null on IOException", result);
    }

    // -----------------------------------------------------------------------
    //  reportOnRateLimit
    // -----------------------------------------------------------------------

    @Test
    public void testReportOnRateLimit_logsUsage()
    {
        GHRateLimit start = mock(GHRateLimit.class);
        when(start.getRemaining()).thenReturn(100);
        GHRateLimit end = mock(GHRateLimit.class);
        when(end.getRemaining()).thenReturn(95);

        repo.reportOnRateLimit("test", start, end);
        // No assertion — just verifying it doesn't throw
    }

    @Test
    public void testReportOnRateLimit_logsCacheHit()
    {
        GHRateLimit start = mock(GHRateLimit.class);
        when(start.getRemaining()).thenReturn(100);
        GHRateLimit end = mock(GHRateLimit.class);
        when(end.getRemaining()).thenReturn(100);

        repo.reportOnRateLimit("test", start, end);
        // No assertion — just verifying it doesn't throw
    }

    // -----------------------------------------------------------------------
    //  setDefaultVersionToLatestTagIfAppropriate (via addDockstoreYmlVersionToWorkflow)
    // -----------------------------------------------------------------------

    @Test
    public void testSetDefaultVersionToLatestTagIfAppropriate_setsDefault()
    {
        Workflow workflow = new Workflow();
        WorkflowVersion version = new WorkflowVersion();
        version.setReferenceType(WorkflowVersion.ReferenceType.TAG);
        version.setLastModified(new Date());

        // Use reflection to call the private method
        try
        {
            java.lang.reflect.Method m = GitHubRepository.class
                    .getDeclaredMethod("setDefaultVersionToLatestTagIfAppropriate",
                            boolean.class, Workflow.class, WorkflowVersion.class);
            m.setAccessible(true);
            m.invoke(repo, true, workflow, version);

            assertSame(version, workflow.getActualDefaultVersion());
        }
        catch (Exception e)
        {
            fail("Should not throw: " + e.getCause().getMessage());
        }
    }

    @Test
    public void testSetDefaultVersionToLatestTagIfAppropriate_ignoresBranch()
    {
        Workflow workflow = new Workflow();
        WorkflowVersion version = new WorkflowVersion();
        version.setReferenceType(WorkflowVersion.ReferenceType.BRANCH);
        version.setLastModified(new Date());

        try
        {
            java.lang.reflect.Method m = GitHubRepository.class
                    .getDeclaredMethod("setDefaultVersionToLatestTagIfAppropriate",
                            boolean.class, Workflow.class, WorkflowVersion.class);
            m.setAccessible(true);
            m.invoke(repo, true, workflow, version);

            assertNull("Branch should not be set as default when latestTagAsDefault=true",
                    workflow.getActualDefaultVersion());
        }
        catch (Exception e)
        {
            fail("Should not throw: " + e.getCause().getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  setDefaultVersionToGitHubDefaultIfAppropriate
    // -----------------------------------------------------------------------

    @Test
    public void testSetDefaultVersionToGitHubDefaultIfAppropriate_setsDefault()
    {
        Workflow workflow = new Workflow();
        WorkflowVersion version = new WorkflowVersion();
        version.setReferenceType(WorkflowVersion.ReferenceType.BRANCH);
        version.setName("main");

        try
        {
            java.lang.reflect.Method m = GitHubRepository.class
                    .getDeclaredMethod("setDefaultVersionToGitHubDefaultIfAppropriate",
                            boolean.class, Workflow.class, WorkflowVersion.class, String.class);
            m.setAccessible(true);
            m.invoke(repo, false, workflow, version, "test-org/test-repo");

            assertSame(version, workflow.getActualDefaultVersion());
        }
        catch (Exception e)
        {
            fail("Should not throw: " + e.getCause().getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  getWorkflowContent
    // -----------------------------------------------------------------------

    @Test
    public void testGetWorkflowContent_returnsProcessedContent()
    {
        GitHubFileContentProvider provider = mock(GitHubFileContentProvider.class);
        String result = repo.getWorkflowContent("org/repo", "main",
                "workflow test {}", "WDL", "/main.wdl", provider);
        assertNotNull(result);
    }

    // -----------------------------------------------------------------------
    //  getWorkflowContent with script loader (used by WebserverController)
    // -----------------------------------------------------------------------

    @Test
    public void testGetWorkflowContent_cwl()
    {
        GitHubFileContentProvider provider = mock(GitHubFileContentProvider.class);
        String result = repo.getWorkflowContent("org/repo", "main",
                "{ \"cwlVersion\": \"v1.0\" }", "CWL", "/main.cwl", provider);
        assertNotNull(result);
    }

    // -----------------------------------------------------------------------
    //  combineVersionAndSourcefile (package-private, uses reflection)
    // -----------------------------------------------------------------------

    @Test
    public void testCombineVersionAndSourcefile_nullSourceFile() throws Exception
    {
        Workflow workflow = new Workflow();
        workflow.setDefaultWorkflowPath("/main.wdl");
        WorkflowVersion version = new WorkflowVersion();
        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();

        java.lang.reflect.Method m = GitHubRepository.class
                .getDeclaredMethod("combineVersionAndSourcefile",
                        String.class, SourceFile.class, Workflow.class,
                        DescriptorLanguage.FileType.class, WorkflowVersion.class,
                        Map.class);
        m.setAccessible(true);
        WorkflowVersion result = (WorkflowVersion) m.invoke(repo,
                "org/repo", null, workflow, DescriptorLanguage.FileType.DOCKSTORE_WDL,
                version, existingDefaults);

        assertNotNull(result);
    }

    @Test
    public void testCombineVersionAndSourcefile_nullContent() throws Exception
    {
        Workflow workflow = new Workflow();
        workflow.setDefaultWorkflowPath("/main.wdl");
        WorkflowVersion version = new WorkflowVersion();
        SourceFile emptyFile = SourceFile.limitedBuilder()
                .type(DescriptorLanguage.FileType.DOCKSTORE_WDL)
                .content(null)
                .paths("/main.wdl")
                .build();
        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();

        java.lang.reflect.Method m = GitHubRepository.class
                .getDeclaredMethod("combineVersionAndSourcefile",
                        String.class, SourceFile.class, Workflow.class,
                        DescriptorLanguage.FileType.class, WorkflowVersion.class,
                        Map.class);
        m.setAccessible(true);
        WorkflowVersion result = (WorkflowVersion) m.invoke(repo,
                "org/repo", emptyFile, workflow, DescriptorLanguage.FileType.DOCKSTORE_WDL,
                version, existingDefaults);

        assertNotNull(result);
    }

    // -----------------------------------------------------------------------
    //  versionValidation
    // -----------------------------------------------------------------------

    @Test
    public void testVersionValidation_withMainDescriptor()
    {
        Workflow workflow = new Workflow();
        workflow.setDefaultWorkflowPath("/main.wdl");

        WorkflowVersion version = new WorkflowVersion();
        SourceFile mainFile = SourceFile.limitedBuilder()
                .type(DescriptorLanguage.FileType.DOCKSTORE_WDL)
                .content("workflow test {}")
                .paths("/main.wdl")
                .build();
        version.getSourceFiles().add(mainFile);

        WorkflowVersion result = repo.versionValidation(version, workflow, "/main.wdl");

        assertNotNull(result);
    }

    @Test
    public void testVersionValidation_withoutMainDescriptor()
    {
        Workflow workflow = new Workflow();
        workflow.setDefaultWorkflowPath("/main.wdl");

        WorkflowVersion version = new WorkflowVersion();
        // No source files added

        WorkflowVersion result = repo.versionValidation(version, workflow, "/main.wdl");

        assertNotNull(result);
        // Should have a validation entry indicating missing descriptor
        boolean hasMissing = result.getValidations().stream()
                .anyMatch(v -> !v.isValid());
        assertTrue("Should mark as invalid when main descriptor is missing", hasMissing);
    }

    // -----------------------------------------------------------------------
    //  readFile(String, WorkflowVersion, DescriptorLanguage.FileType, String)
    // -----------------------------------------------------------------------

    @Test
    public void testReadFile_returnsOptionalWithContent() throws Exception
    {
        GHContent mockContent = mock(GHContent.class);
        when(mockContent.getContent()).thenReturn("file content");
        when(mockContent.getType()).thenReturn("blob");
        when(mockContent.getEncoding()).thenReturn("utf-8");
        when(mockContent.getPath()).thenReturn("test.txt");
        when(mockContent.getSize()).thenReturn(100L);

        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.singletonList(mockContent));
        when(mockRepo.getFileContent(ArgumentMatchers.eq("test.txt"), ArgumentMatchers.anyString())).thenReturn(mockContent);

        WorkflowVersion version = new WorkflowVersion();
        version.setReference("main");

        Optional<SourceFile> result = repo.readFile("test-org/test-repo",
                version, DescriptorLanguage.FileType.DOCKSTORE_WDL, "test.txt");

        assertTrue(result.isPresent());
        assertEquals("file content", result.get().getContent());
    }

    // -----------------------------------------------------------------------
    //  readFile(String, WorkflowVersion, Collection, DescriptorLanguage.FileType, String)
    // -----------------------------------------------------------------------

    @Test
    public void testReadFile_addsToCollection() throws Exception
    {
        GHContent mockContent = mock(GHContent.class);
        when(mockContent.getContent()).thenReturn("file content");
        when(mockContent.getType()).thenReturn("blob");
        when(mockContent.getEncoding()).thenReturn("utf-8");
        when(mockContent.getPath()).thenReturn("test.txt");
        when(mockContent.getSize()).thenReturn(100L);

        when(mockRepo.getDirectoryContent(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Collections.singletonList(mockContent));
        when(mockRepo.getFileContent(ArgumentMatchers.eq("test.txt"), ArgumentMatchers.anyString())).thenReturn(mockContent);

        WorkflowVersion version = new WorkflowVersion();
        version.setReference("main");
        Collection<SourceFile> files = new ArrayList<>();

        repo.readFile("test-org/test-repo", version, files,
                DescriptorLanguage.FileType.DOCKSTORE_WDL, "test.txt");

        assertEquals(1, files.size());
    }

    // -----------------------------------------------------------------------
    //  readPaths
    // -----------------------------------------------------------------------

    @Test
    public void testReadPaths_emptyList()
    {
        List<SourceFile> result = repo.readPaths("org/repo",
                new WorkflowVersion(), DescriptorLanguage.FileType.DOCKSTORE_WDL,
                Collections.emptySet(), Collections.emptyList());

        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    @Test
    public void testConstants()
    {
        assertEquals(10L * 1024 * 1024, GitHubRepository.MAXIMUM_FILE_DOWNLOAD_SIZE);
        assertEquals(30, GitHubRepository.GITHUB_MAX_CACHE_AGE_SECONDS);
        assertEquals("Out of GitHub rate limit", GitHubRepository.OUT_OF_GIT_HUB_RATE_LIMIT);
        assertEquals(50, GitHubRepository.SLEEP_AT_RATE_LIMIT_OR_BELOW);
        assertEquals("GitHub abuse limit reached", GitHubRepository.GITHUB_ABUSE_LIMIT_REACHED);
        assertEquals("refs/heads/", GitHubRepository.REFS_HEADS);
        assertEquals("submodule", GitHubRepository.SUBMODULE);
        assertEquals("symlink", GitHubRepository.SYMLINK);
        assertEquals("registry-web-cache", GitHubRepository.REGISTRY_WEB_CACHE);
    }
}
