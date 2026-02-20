package ru.genespace.github;

import static ru.genespace.dockstore.Constants.DOCKSTORE_YML_PATH;
import static ru.genespace.dockstore.Constants.DOCKSTORE_YML_PATHS;
import static ru.genespace.dockstore.Constants.SKIP_COMMIT_ID;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import org.kohsuke.github.extras.okhttp3.ObsoleteUrlFactory;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import ru.genespace.dockstore.AppTool;
import ru.genespace.dockstore.DescriptorLanguage;
import ru.genespace.dockstore.DescriptorLanguageSubclass;
import ru.genespace.dockstore.EntryType;
import ru.genespace.dockstore.Notebook;
import ru.genespace.dockstore.SourceFile;
import ru.genespace.dockstore.Validation;
import ru.genespace.dockstore.VersionTypeValidation;
import ru.genespace.dockstore.Workflow;
import ru.genespace.dockstore.WorkflowMode;
import ru.genespace.dockstore.WorkflowVersion;
import ru.genespace.dockstore.languages.LanguageHandlerFactory;
import ru.genespace.dockstore.languages.LanguageHandlerInterface;
import ru.genespace.dockstore.yaml.DockstoreYaml12;
import ru.genespace.dockstore.yaml.DockstoreYamlHelper;
import ru.genespace.dockstore.yaml.Service12;
import ru.genespace.dockstore.yaml.Workflowish;
import ru.genespace.dockstore.yaml.YamlNotebook;
import ru.genespace.misc.CustomLoggedException;

public class GitHubRepository
{

    private final GitHub github;
    private final String githubTokenUsername;
    private String gitUsername;

    public static final Logger LOG = LoggerFactory.getLogger( GitHubRepository.class );
    public static final long MAXIMUM_FILE_DOWNLOAD_SIZE = 10L * 1024L * 1024L;
    public static final int GITHUB_MAX_CACHE_AGE_SECONDS = 30;

    public static final String OUT_OF_GIT_HUB_RATE_LIMIT = "Out of GitHub rate limit";
    public static final int SLEEP_AT_RATE_LIMIT_OR_BELOW = 50;

    public static final String GITHUB_ABUSE_LIMIT_REACHED = "GitHub abuse limit reached";
    public static final String REFS_HEADS = "refs/heads/";
    public static final String SUBMODULE = "submodule";
    public static final String SYMLINK = "symlink";

    private static final int BYTES_IN_KILOBYTE = 1024;
    private static final int KILOBYTES_IN_MEGABYTE = 1024;
    private static final int CACHE_IN_MB = 100;

    public static final String DOCKSTORE_WEB_CACHE = "/tmp/dockstore-web-cache";

    public static final Pattern GIT_BRANCH_TAG_PATTERN = Pattern
            .compile( "^refs/(tags|heads)/((?!.*//)(?!.*\\^)(?!.*:)(?!.*\\\\)(?!.*@)(?!.*\\[)(?!.*\\?)(?!.*~)(?!.*\\.\\.)[\\p{Punct}\\p{L}\\d\\-_/]+)$" );

    private static OkHttpClient okHttpClient = null;
    private static Cache cache = null;
    /**
     * @param githubTokenUsername the username for githubTokenContent
     * @param githubTokenContent authorization token
     */
    public GitHubRepository(String githubTokenUsername, String githubTokenContent, Long installationId)
    {
        initialize();
        this.githubTokenUsername = githubTokenUsername;
        this.gitUsername = githubTokenUsername != null ? githubTokenUsername : "Unauthenticated";
        try
        {
            if( githubTokenUsername != null && githubTokenContent != null )
            {
                final GitHubBuilder gitHubBuilder = getBuilder( githubTokenUsername ).withOAuthToken( githubTokenContent, githubTokenUsername );
                this.github = gitHubBuilder.build();
            }
            else
            {
                //Anonymous authorization
                final GitHubBuilder gitHubBuilder = getBuilder( githubTokenUsername );
                this.github = gitHubBuilder.build();
            }

        }
        catch (IOException e)
        {
            throw new RuntimeException( e );
        }
    }

    private void initialize()
    {
        if( cache == null )
        {
            cache = generateCache( null );
        }
        try
        {
            cache.initialize();
        }
        catch (IOException e)
        {
            LOG.error( "Could not create web cache, initialization exception", e );
            throw new RuntimeException( e );
        }
        // match HttpURLConnection which does not have a timeout by default
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
        okHttpClient = builder.cache( cache ).connectTimeout( 0, TimeUnit.SECONDS ).readTimeout( 0, TimeUnit.SECONDS ).writeTimeout( 0, TimeUnit.SECONDS ).build();
        try
        {
            // this can only be called once per JVM, a factory exception is thrown in our tests
            URL.setURLStreamHandlerFactory( new ObsoleteUrlFactory( okHttpClient ) );
        }
        catch (Error factoryException)
        {
            if( factoryException.getMessage().contains( "factory already defined" ) )
            {
                LOG.debug( "OkHttpClient already registered, skipping" );
            }
            else
            {
                LOG.error( "Could not create web cache, factory exception", factoryException );
                throw new RuntimeException( factoryException );
            }
        }
    }

    public GitHubRepository(long installationId)
    {
        this( null, null, installationId );
    }

    public GitHubRepository(String githubTokenUsername, String githubTokenContent)
    {
        this( githubTokenUsername, githubTokenContent, null );
    }

    /**
     * Get a github client builder with everything configured except for auth
     * 
     * @param cacheNamespace namespace for logging in the cache, cache miss reports, etc.
     * @return github client builder
     */
    public static GitHubBuilder getBuilder(String cacheNamespace)
    {
        //TODO: fix
        OkHttpClient.Builder builder = okHttpClient.newBuilder();
        builder.eventListener( new CacheHitListener( GitHubRepository.class.getSimpleName(), cacheNamespace ) );
        builder.cache( getCache( null ) );
        OkHttpClient build = builder.build();
        // Must set the cache max age otherwise kohsuke assumes 0 which significantly slows down our GitHub requests
        OkHttpGitHubConnector okHttp3Connector = new OkHttpGitHubConnector( build, GITHUB_MAX_CACHE_AGE_SECONDS );
        GitHubBuilder gitHubBuilder = new GitHubBuilder().withAbuseLimitHandler( new FailAbuseLimitHandler( cacheNamespace ) ).withConnector( okHttp3Connector );
        gitHubBuilder = gitHubBuilder.withRateLimitHandler( new FailRateLimitHandler( cacheNamespace ) );
        return gitHubBuilder;
    }

    public static Cache getCache(String cacheNamespace)
    {
        if( cacheNamespace == null )
        {
            return cache;
        }
        else
        {
            return generateCache( cacheNamespace );
        }
    }

    private static Cache generateCache(String suffix)
    {
        int cacheSize = CACHE_IN_MB * BYTES_IN_KILOBYTE * KILOBYTES_IN_MEGABYTE; // 100 MiB
        final File cacheDir;
        try
        {
            // let's try using the same cache each time
            // not sure how corruptible/non-corruptible the cache is
            // namespace cache when testing on circle ci
            cacheDir = Files.createDirectories( Paths.get( DOCKSTORE_WEB_CACHE + (suffix == null ? "" : "/" + suffix) ) ).toFile();
        }
        catch (IOException e)
        {
            LOG.error( "Could not create or re-use web cache", e );
            throw new RuntimeException( e );
        }
        return new Cache( cacheDir, cacheSize );
    }

    private static final class FailAbuseLimitHandler extends GitHubAbuseLimitHandler
    {
        private final String username;

        private FailAbuseLimitHandler(String username)
        {
            this.username = username;
        }

        @Override
        public void onError(GitHubConnectorResponse connectorResponse)
        {
            LOG.error( GITHUB_ABUSE_LIMIT_REACHED + " for " + username );
            throw new CustomLoggedException( GITHUB_ABUSE_LIMIT_REACHED );
        }
    }

    /**
     * 1. This logs username 2. We control the string in the error message
     */
    private static final class FailRateLimitHandler extends GitHubRateLimitHandler
    {

        private final String username;

        private FailRateLimitHandler(String username)
        {
            this.username = username;
        }

        @Override
        public void onError(GitHubConnectorResponse connectorResponse)
        {
            LOG.error( OUT_OF_GIT_HUB_RATE_LIMIT + " for " + username );
            throw new CustomLoggedException( OUT_OF_GIT_HUB_RATE_LIMIT );
        }
    }

    /**
     * Retrieves a repository from github
     * 
     * @param repositoryId of the form organization/repository (Ex. dockstore/dockstore-ui2)
     * @return GitHub repository
     * @throws Exception
     */
    public GHRepository getRepository(String repositoryId)
    {
        GHRepository repository;
        try
        {
            repository = github.getRepository( repositoryId );
        }
        catch (IOException e)
        {
            LOG.error( gitUsername + ": Cannot retrieve the workflow from GitHub", e );
            throw new CustomLoggedException( "Could not reach GitHub, please try again later" );
        }

        return repository;
    }

    public Optional<SourceFile> getDockstoreYml(String repositoryId, String gitReference)
    {
        GHRepository repository;
        try
        {
            repository = getRepository( repositoryId );
        }
        catch (CustomLoggedException ex)
        {
            throw new CustomLoggedException( "Could not find repository " + repositoryId + "." );
        }
        String dockstoreYmlContent = null;
        for ( String dockstoreYmlPath : DOCKSTORE_YML_PATHS )
        {
            dockstoreYmlContent = this.readFileFromRepo( dockstoreYmlPath, gitReference, repository );
            if( dockstoreYmlContent != null )
            {
                // Create file for .dockstore.yml
                return Optional
                        .of( SourceFile.limitedBuilder().type( DescriptorLanguage.FileType.DOCKSTORE_YML ).content( dockstoreYmlContent ).paths( dockstoreYmlPath ).build() );
            }
        }
        return Optional.empty();
    }

    /**
     * This method appears to read files from github in a cache-aware manner, taking into account symlinks and
     * submodules.
     *
     * @param originalFileName the original filename that we're looking for
     * @param originalReference the original reference (tag, branch, commit) we're looking for
     * @param originalRepo the original repo we're looking for the file in
     * @return
     */
    public String readFileFromRepo(final String originalFileName, final String originalReference, final GHRepository originalRepo)
    {
        GHRateLimit startRateLimit = null;
        // when looking through submodules, we always look for a specific commit
        boolean submoduleRedirected = false;
        GHRepository repo = originalRepo;
        String reference = originalReference;
        String fileName = originalFileName;
        try
        {
            startRateLimit = getGhRateLimitQuietly();

            // may need to pass owner from git url, as this may differ from the git username
            List<String> folders = Arrays.asList( fileName.split( "/" ) );
            List<String> start = new ArrayList<>();
            // this complicated code is for accounting for symbolic links to directories
            // basically, we need to check if each folder level is actually a symbolic link to somewhere
            // else entirely and then switch to checking that path instead if it is
            for ( int i = 0; i < folders.size() - 1; i++ )
            {
                // ignore leading slash
                if( i == 0 && folders.get( i ).isEmpty() )
                {
                    continue;
                }
                // build up from the root and look for folders (potentially in the cache)
                start.add( folders.get( i ) );
                String partialPath = Joiner.on( "/" ).join( start );
                try
                {
                    Pair<GHContent, String> innerContent = getContentAndMetadataForFileName( partialPath, reference, repo, submoduleRedirected );
                    if( innerContent != null )
                    {
                        if( innerContent.getLeft().getType().equals( SYMLINK ) )
                        {
                            // restart the loop to look for symbolic links pointed to by symbolic links
                            List<String> newfolders = Lists.newArrayList( innerContent.getRight().split( "/" ) );
                            List<String> sublist = folders.subList( i + 1, folders.size() );
                            newfolders.addAll( sublist );
                            folders = newfolders;
                        }
                        else if( innerContent.getLeft().getType().equals( SUBMODULE ) )
                        {
                            String otherRepo = innerContent.getRight();
                            if( otherRepo == null )
                            {
                                // likely means this submodule is not on GitHub, rest API reports it as null
                                LOG.warn( "Could not process {} at {}, is likely a submodule that is not on GitHub", originalFileName, originalReference );
                                return null;
                            }
                            URL otherRepoURL = new URL( otherRepo );
                            // reassign repo and reference
                            final String[] split = otherRepoURL.getPath().split( "/" );
                            final int indexPastReposPrefix = 2;
                            String newRepositoryId = split[indexPastReposPrefix] + "/" + split[indexPastReposPrefix + 1];
                            String newReference = split[split.length - 1];
                            repo = github.getRepository( newRepositoryId );
                            reference = newReference;

                            // discard the old folders we've looked at already and start looking through folders in the submodule repository
                            folders = folders.subList( i + 1, folders.size() );
                            submoduleRedirected = true;
                        }
                        // in both the case of a symbolic link or a submodule, reset the path we're looking for since the "old" path getting to the link or submodule is no longer relevant
                        // only the path "inside" the submodule or link is what you are looking for e.g. if your path is `../foo/test/a/b` but `foo` is a submodule, you
                        // look at the repo that `foo` corresponds to and look for the path `test/a/b` inside it
                        start = new ArrayList<>();
                        i = -1;
                    }
                }
                catch (IOException e)
                {
                    // move on if a file is not found
                    LOG.warn( "Could not find " + partialPath + " at " + reference, e );
                }
            }
            fileName = Joiner.on( "/" ).join( folders );

            Pair<GHContent, String> decodedContentAndMetadata = getContentAndMetadataForFileName( fileName, reference, repo, submoduleRedirected );
            if( decodedContentAndMetadata == null )
            {
                return null;
            }
            else
            {
                String content = decodedContentAndMetadata.getRight();
                String encoding = decodedContentAndMetadata.getLeft().getEncoding();
                // If the file size is 1MB or larger, content will be "" and the encoding will be "none":
                // https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28 (see "Notes")
                // In such a case, we retrieve the content via the blob endpoint.
                if( "".equals( content ) && "none".equals( encoding ) )
                {
                    long size = decodedContentAndMetadata.getLeft().getSize();
                    if( size > MAXIMUM_FILE_DOWNLOAD_SIZE )
                    {
                        LOG.warn( gitUsername + ": file too large in readFileFromRepo " + fileName + " from repository " + repo.getFullName() + ":" + reference );
                        return "Dockstore does not process extremely large files";
                    }
                    String sha = decodedContentAndMetadata.getLeft().getSha();
                    GHBlob blob = repo.getBlob( sha );
                    content = IOUtils.toString( blob.read(), StandardCharsets.UTF_8 );
                }
                return content;
            }
        }
        catch (IOException e)
        {
            LOG.warn( gitUsername + ": IOException on readFileFromRepo " + fileName + " from repository " + repo.getFullName() + ":" + reference + ", " + e.getMessage(), e );
            return null;
        }
        finally
        {
            GHRateLimit endRateLimit = getGhRateLimitQuietly();
            reportOnRateLimit( "readFileFromRepo", startRateLimit, endRateLimit );
        }
    }

    /**
     * For a given file, in a github repo, with a particular cleaned reference name.
     * 
     * @param fileName
     * @param reference
     * @param repo
     * @parasm submoduleRedirected if the reference is a submodule, it will be a commit hash
     * @return metadata describing the type of file and its decoded content
     * @throws IOException
     */
    private Pair<GHContent, String> getContentAndMetadataForFileName(String fileName, String reference, GHRepository repo, boolean submoduleRedirected) throws IOException
    {
        // retrieval of directory content is cached as opposed to retrieving individual files
        String fullPathNoEndSeparator = FilenameUtils.getFullPathNoEndSeparator( fileName );
        // but tags on quay.io that do not match github are costly, avoid by checking cached references

        GHRef[] branchesAndTags = getBranchesAndTags( repo );

        // only look at github if the reference exists
        if( !submoduleRedirected && Lists.newArrayList( branchesAndTags ).stream().noneMatch( ref -> ref.getRef().contains( reference ) ) )
        {
            return null;
        }
        List<GHContent> directoryContent = repo.getDirectoryContent( fullPathNoEndSeparator, reference );

        String stripStart = StringUtils.stripStart( fileName, "/" );
        Optional<GHContent> firstMatch = directoryContent.stream().filter( content -> stripStart.equals( content.getPath() ) ).findFirst();
        if( firstMatch.isPresent() )
        {
            GHContent content = firstMatch.get();
            if( content.isDirectory() )
            {
                // directories do not have content directly
                return null;
            }
            // need to double-check whether this is a symlink by getting the specific file which sucks
            GHContent fileContent = repo.getFileContent( content.getPath(), reference );
            try
            {
                if( fileContent.getType().equals( SUBMODULE ) )
                {
                    // fileContent.getContent() assumes content to decode, but a submodule reference has no content, return the giturl instead
                    return Pair.of( fileContent, fileContent.getGitUrl() );
                }
                // this is deprecated, but getContent() seems to be the only way to get the actual content, rather than the content of the symbolic link
                return Pair.of( fileContent, fileContent.getContent() );
            }
            catch (NullPointerException ex)
            {
                LOG.info( "looks like we were unable to retrieve " + fileName + " at " + reference + " , possible submodule reference?", ex );
                // seems to be thrown on submodules with the new library
                return null;
            }
        }

        return null;
    }

    public GHRateLimit getGhRateLimitQuietly()
    {
        GHRateLimit startRateLimit = null;
        try
        {
            // github.rateLimit() was deprecated and returned a much lower limit, low balling our rate limit numbers
            startRateLimit = github.getRateLimit();
        }
        catch (IOException e)
        {
            LOG.error( "unable to retrieve rate limit, weird", e );
        }
        return startRateLimit;
    }

    public void reportOnRateLimit(String id, GHRateLimit startRateLimit, GHRateLimit endRateLimit)
    {
        if( startRateLimit != null && endRateLimit != null )
        {
            int used = startRateLimit.getRemaining() - endRateLimit.getRemaining();
            if( used > 0 )
            {
                LOG.debug( id + ": used up " + used + " GitHub rate limited requests" );
            }
            else
            {
                LOG.debug( id + ": was served entirely from cache" );
            }
        }
    }

    /**
     * This function replaces calling repo.getRefs(). Calling getRefs() will return all GHRefs, including old PRs. This
     * change makes two calls instead to get only the branches and tags separately. Previously, an exception would get
     * thrown if the repo had no GHRefs at all; now it will throw an exception only if the repo has neither tags nor
     * branches, so that it is as similar as possible.
     * 
     * @param repo Repository path (ex. dockstore/dockstore-ui2)
     * @return GHRef[] Array of branches and tags
     */
    public GHRef[] getBranchesAndTags(GHRepository repo) throws IOException
    {
        boolean getBranchesSucceeded = false;
        GHRef[] branches = {};
        GHRef[] tags = {};

        // getRefs() fails with a GHFileNotFoundException if there are no matching results instead of returning an empty array/null.
        try
        {
            branches = repo.getRefs( "refs/heads/" );
            getBranchesSucceeded = true;
        }
        catch (org.kohsuke.github.GHFileNotFoundException ex)
        {
            LOG.debug( "No branches found for " + repo.getName(), ex );
        }
        catch (org.kohsuke.github.HttpException ex)
        {
            if( ex.getResponseCode() == HttpStatus.SC_CONFLICT )
            {
                // it seems like this is what is returned if there are absolutely no branches or tags
                return new GHRef[] {};
            }
            throw ex;
        }

        try
        {
            // this crazy looking structure is because getRefs can result in a cache miss (on repos without tags) whereas listTags seems to not have this problem
            // yes this could probably be re-coded to use listTags directly
            if( repo.listTags().iterator().hasNext() )
            {
                tags = repo.getRefs( "refs/tags/" );
            }
        }
        catch (GHFileNotFoundException ex)
        {
            LOG.debug( "No tags found for  " + repo.getName() );
            if( !getBranchesSucceeded )
            {
                throw ex;
            }
        }
        return ArrayUtils.addAll( branches, tags );
    }

    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow, Map<String, WorkflowVersion> existingDefaults,
            Optional<String> versionName, boolean hardRefresh)
    {
        GHRateLimit startRateLimit = getGhRateLimitQuietly();

        // Get repository from GitHub
        GHRepository repository = getRepository( repositoryId );

        // when getting a full workflow, look for versions and check each version for valid workflows
        List<GitReferenceInfo> references = new ArrayList<>();

        GHRef[] refs = {};
        try
        {
            refs = getBranchesAndTags( repository );
            for ( GHRef ref : refs )
            {
                GitReferenceInfo gitReferenceInfo = getRef( ref, repository );
                if( gitReferenceInfo != null && (versionName.isEmpty() || Objects.equals( versionName.get(), gitReferenceInfo.refName() )) )
                {
                    references.add( gitReferenceInfo );
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

        // For each branch (reference) found, create a workflow version and find the associated descriptor files
        for ( GitReferenceInfo ref : references )
        {
            if( ref != null )
            {
                final String branchName = ref.refName();
                final Date lastModified = ref.branchDate();
                final String commitId = ref.sha();
                if( toRefreshVersion( commitId, existingDefaults.get( branchName ), hardRefresh ) )
                {
                    WorkflowVersion version = setupWorkflowVersionsHelper( workflow, ref, existingWorkflow, existingDefaults, repository, null, versionName );
                    if( version != null )
                    {
                        workflow.addWorkflowVersion( version );
                    }
                }
                else
                {
                    // Version didn't change, but we don't want to delete
                    // Add a stub version with commit ID set to an ignore value so that the version isn't deleted
                    LOG.info( "%s: Skipping GitHub reference: %s".formatted( gitUsername, ref ) );
                    WorkflowVersion version = new WorkflowVersion();
                    version.setName( branchName );
                    version.setReference( branchName );
                    version.setLastModified( lastModified );
                    version.setCommitID( SKIP_COMMIT_ID );
                    workflow.addWorkflowVersion( version );
                }
            }
        }

        GHRateLimit endRateLimit = getGhRateLimitQuietly();
        reportOnRateLimit( "setupWorkflowVersions", startRateLimit, endRateLimit );

        return workflow;
    }

    /**
     * Retrieve important information related to a reference
     * 
     * @param ref GitHub reference object
     * @param repository GitHub repository object
     * @return Record containing reference name, branch date, and SHA
     */
    public GitReferenceInfo getRef(GHRef ref, GHRepository repository)
    {
        final Date epochStart = new Date( 0 );
        Date branchDate = new Date( 0 );
        String refName = ref.getRef();
        String sha = null;
        boolean toIgnore = false;
        if( refName.startsWith( "refs/heads/" ) )
        {
            refName = StringUtils.removeStart( refName, "refs/heads/" );
        }
        else if( refName.startsWith( "refs/tags/" ) )
        {
            refName = StringUtils.removeStart( refName, "refs/tags/" );
        }
        else if( refName.startsWith( "refs/pull/" ) )
        {
            // ignore these strange pull request objects that this library produces
            toIgnore = true;
        }

        if( !toIgnore )
        {
            try
            {
                sha = getCommitSHA( ref, repository, refName );

                GHCommit commit = repository.getCommit( sha );
                branchDate = commit.getCommitDate();
                if( branchDate.before( epochStart ) )
                {
                    branchDate = epochStart;
                }
            }
            catch (IOException e)
            {
                LOG.error( "unable to retrieve commit date for branch " + refName, e );
            }
            return new GitReferenceInfo( refName, branchDate, sha );
        }
        else
        {
            return null;
        }
    }

    // When a user creates an annotated tag, the object type will be a tag. Otherwise, it's probably of type commit?
    // The documentation doesn't list the possibilities https://github-api.kohsuke.org/apidocs/org/kohsuke/github/GHRef.GHObject.html#getType(),
    // but I'll assume it mirrors the 4 Git types: blobs, trees, commits, and tags.
    private String getCommitSHA(GHRef ref, GHRepository repository, String refName) throws IOException
    {
        String sha;
        String type = ref.getObject().getType();
        if( "commit".equals( type ) )
        {
            sha = ref.getObject().getSha();
        }
        else if( "tag".equals( type ) )
        {
            sha = repository.getTagObject( ref.getObject().getSha() ).getObject().getSha();
        }
        else if( "branch".equals( type ) )
        {
            GHBranch branch = repository.getBranch( refName );
            sha = branch.getSHA1();
        }
        else
        {
            // I'm not sure when this would happen.
            // Keeping the sha as-is is probably wrong, but we should mimic the behaviour from before since this is a hotfix.
            sha = ref.getObject().getSha();
            LOG.error( "Unsupported GitHub reference object. Unable to find commit ID for type: " + ref.getObject().getType() );
        }
        return sha;
    }
    
    /**
     * Determine whether to refresh a version or not
     * Refresh version if any of the following is true
     * * this is a hard refresh
     * * version doesn't exist
     * * commit id isn't set
     * * commitId is different
     * * synced == false
     *
     * @param commitId
     * @param existingVersion
     * @param hardRefresh
     * @return
     */
    protected boolean toRefreshVersion(String commitId, WorkflowVersion existingVersion, boolean hardRefresh) {
        return hardRefresh || existingVersion == null || existingVersion.getCommitID() == null || !Objects.equals( existingVersion.getCommitID(), commitId );
    }

    /**
     * Creates a workflow version for a specific branch/tag on GitHub
     * 
     * @param workflow Workflow object
     * @param ref record containing reference name, branch date, and SHA
     * @param existingWorkflow Optional existing workflow
     * @param existingDefaults Optional mapping of existing versions
     * @param repository GitHub repository object
     * @param dockstoreYml Dockstore YML sourcefile
     * @param versionName Optional version name to refresh
     * @return WorkflowVersion for the given reference
     */
    private WorkflowVersion setupWorkflowVersionsHelper(Workflow workflow, GitReferenceInfo ref, Optional<Workflow> existingWorkflow, Map<String, WorkflowVersion> existingDefaults,
            GHRepository repository, SourceFile dockstoreYml, Optional<String> versionName)
    {
        LOG.info( gitUsername + ": Looking at GitHub reference: " + ref.toString() );
        // Initialize the workflow version
        WorkflowVersion version = initializeWorkflowVersion( ref.refName(), existingWorkflow, existingDefaults );
        version.setLastModified( ref.branchDate() );
        version.setCommitID( ref.sha() );
        String calculatedPath = version.getWorkflowPath();

        DescriptorLanguage.FileType identifiedType = workflow.getFileType();

        if( workflow.getMode() == WorkflowMode.DOCKSTORE_YML )
        {
            if( versionName.isEmpty() )
            {
                version = setupEntryFilesForGitHubVersion( ref, repository, version, workflow, existingDefaults, dockstoreYml );
                if( version == null )
                {
                    return null;
                }
                calculatedPath = version.getWorkflowPath();
            }
            else
            {
                // Legacy version refresh of Dockstore.yml workflow, so use existing path for version (instead of default path)
                if( !existingDefaults.containsKey( versionName.get() ) )
                {
                    throw new CustomLoggedException( "Cannot refresh version " + versionName.get() + ". Only existing legacy versions can be refreshed." );
                }
                calculatedPath = existingDefaults.get( versionName.get() ).getWorkflowPath();
                version.setWorkflowPath( calculatedPath );
                version = setupWorkflowFilesForVersion( calculatedPath, ref, repository, version, identifiedType, workflow, existingDefaults );
            }
        }
        else
        {
            version = setupWorkflowFilesForVersion( calculatedPath, ref, repository, version, identifiedType, workflow, existingDefaults );
        }

        return versionValidation( version, workflow, calculatedPath );
    }

    /**
     * Grab files for workflow version based on the entry type
     * 
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to add source files to
     * @param workflow Workflow object
     * @param existingDefaults Optional mapping of existing versions
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Updated workflow version
     */
    private WorkflowVersion setupEntryFilesForGitHubVersion(GitReferenceInfo ref, GHRepository repository, WorkflowVersion version, Workflow workflow,
            Map<String, WorkflowVersion> existingDefaults, SourceFile dockstoreYml)
    {
        // Add Dockstore.yml to version
        SourceFile dockstoreYmlClone = dockstoreYml.duplicate();
        if( workflow.getDescriptorType() == DescriptorLanguage.SERVICE )
        {
            dockstoreYmlClone.setType( DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML );
        }
        version.addSourceFile( dockstoreYmlClone );
        version.setLegacyVersion( false );

        if( workflow.getDescriptorType() == DescriptorLanguage.SERVICE )
        {
            return setupServiceFilesForGitHubVersion( ref, repository, version, dockstoreYml );
        }
        else
        {
            return setupWorkflowFilesForGitHubVersion( ref, repository, version, workflow, existingDefaults, dockstoreYml );
        }
    }

    /**
     * Download workflow files for a given workflow version
     * 
     * @param calculatedPath Path to primary descriptor
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param identifiedType Descriptor type of file
     * @param workflow Workflow for given version
     * @param existingDefaults Optional mapping of existing versions
     * @return Version with updated sourcefiles
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private WorkflowVersion setupWorkflowFilesForVersion(String calculatedPath, GitReferenceInfo ref, GHRepository repository, WorkflowVersion version,
            DescriptorLanguage.FileType identifiedType, Workflow workflow, Map<String, WorkflowVersion> existingDefaults)
    {
        // Grab workflow file from github
        try
        {
            // Get contents of descriptor file and store
            String decodedContent = this.readFileFromRepo( calculatedPath, ref.refName(), repository );
            if( decodedContent != null )
            {
                SourceFile file = SourceFile.limitedBuilder().type( identifiedType ).content( decodedContent ).paths( calculatedPath ).build();
                version = combineVersionAndSourcefile( repository.getFullName(), file, workflow, identifiedType, version, existingDefaults );

                //                // Use default test parameter file if either new version or existing version that hasn't been edited
                //                // TODO: why is this here? Does this code not have a counterpart in BitBucket and GitLab?
                //                if (!version.isDirtyBit() && workflow.getDefaultTestParameterFilePath() != null) {
                //                    String testJsonPath = workflow.getDefaultTestParameterFilePath();
                //                    String testJsonContent = this.readFileFromRepo(testJsonPath, ref.refName(), repository);
                //                    if (testJsonContent != null) {
                //                        DescriptorLanguage.FileType testJsonType = workflow.getDescriptorType().getTestParamType();
                //                        SourceFile testJson = SourceFile.limitedBuilder().type(testJsonType).content(testJsonContent).paths(testJsonPath).build();
                //                        // Only add test parameter file if it hasn't already been added
                //                        boolean hasDuplicate = version.getSourceFiles().stream().anyMatch((SourceFile sf) -> sf.getPath().equals(workflow.getDefaultTestParameterFilePath())
                //                            && sf.getType() == testJson.getType());
                //                        if (!hasDuplicate) {
                //                            version.getSourceFiles().add(testJson);
                //                        }
                //                    }
                //                }
            }

        }
        catch (Exception ex)
        {
            LOG.info( gitUsername + ": " + workflow.getDefaultWorkflowPath() + " on " + ref + " was not valid workflow", ex );
        }
        return version;
    }

    /**
     * Pull descriptor files for the given service version and add to version
     * 
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Version with updated sourcefiles
     */
    private WorkflowVersion setupServiceFilesForGitHubVersion(GitReferenceInfo ref, GHRepository repository, WorkflowVersion version, SourceFile dockstoreYml)
    {
        // Grab all files from files array
        List<String> files;
        try
        {
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12( dockstoreYml.getContent() );
            final Service12 service = dockstoreYaml12.getService();
            if( service == null )
            {
                LOG.info( ".dockstore.yml has no service" );
                return null;
            }
            // TODO: Handle more than one service.
            files = service.getFiles();
            // null catch due to .dockstore.yml files like https://raw.githubusercontent.com/denis-yuen/test-malformed-app/c43103f4004241cb738280e54047203a7568a337/.dockstore.yml
        }
        catch (DockstoreYamlHelper.DockstoreYamlException ex)
        {
            String msg = "Invalid .dockstore.yml";
            LOG.info( msg, ex );
            return null;
        }
        for ( String filePath : files )
        {
            String fileContent = this.readFileFromRepo( filePath, ref.refName(), repository );
            if( fileContent != null )
            {
                SourceFile file = SourceFile.limitedBuilder().type( DescriptorLanguage.FileType.DOCKSTORE_SERVICE_OTHER ).content( fileContent ).paths( filePath ).build();
                version.getSourceFiles().add( file );
            }
            else
            {
                // File not found or null
                LOG.info( "Could not find file " + filePath + " in repo " + repository );
            }
        }

        return version;
    }

    /**
     * Pull descriptor files for the given workflow version and add to version
     * 
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param workflow Workflow to add version to
     * @param existingDefaults Existing defaults
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Version with updated sourcefiles
     */
    private WorkflowVersion setupWorkflowFilesForGitHubVersion(GitReferenceInfo ref, GHRepository repository, WorkflowVersion version, Workflow workflow,
            Map<String, WorkflowVersion> existingDefaults, SourceFile dockstoreYml)
    {
        // Determine version information from dockstore.yml
        Workflowish theWf = null;
        List<String> testParameterPaths = null;
        try
        {
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12( dockstoreYml.getContent() );
            // TODO: Need to handle services; the YAML is guaranteed to have at least one of either
            List<? extends Workflowish> workflows;
            if( workflow instanceof Notebook )
            {
                workflows = dockstoreYaml12.getNotebooks();
            }
            else if( workflow instanceof AppTool )
            {
                workflows = dockstoreYaml12.getTools();
            }
            else
            {
                workflows = dockstoreYaml12.getWorkflows();
            }

            final Optional<? extends Workflowish> maybeWorkflow = workflows.stream().filter( wf -> {
                final String wfName = wf.getName();
                final String dockstoreWorkflowPath = "github.com/" + repository.getFullName() + (wfName != null && !wfName.isEmpty() ? "/" + wfName : "");

                return (Objects.equals( dockstoreWorkflowPath, workflow.getEntryPath() ));
            } ).findFirst();
            if( maybeWorkflow.isEmpty() )
            {
                return null;
            }
            theWf = maybeWorkflow.get();
            testParameterPaths = theWf.getTestParameterFiles();
        }
        catch (DockstoreYamlHelper.DockstoreYamlException ex)
        {
            String msg = "Invalid .dockstore.yml: " + ex.getMessage();
            LOG.info( msg, ex );
            return null;
        }

        version.setUserFiles( theWf.getOtherFiles() );
        // If this is a notebook, set the version's user-specified files and image.
        if( theWf instanceof YamlNotebook yamlNotebook )
        {
            version.setKernelImagePath( yamlNotebook.getKernel() );
        }

        // No need to check for null, has been validated
        String primaryDescriptorPath = theWf.getPrimaryDescriptorPath();
        version.setWorkflowPath( primaryDescriptorPath );
        //commented
        //        String readMePath = theWf.getReadMePath();
        //        version.setReadMePath( readMePath );

        String validationMessage = "";
        String fileContent = this.readFileFromRepo( primaryDescriptorPath, ref.refName(), repository );
        if( fileContent != null )
        {
            // Add primary descriptor file and resolve imports
            DescriptorLanguage.FileType identifiedType = workflow.getDescriptorType().getFileType();
            SourceFile primaryDescriptorFile = SourceFile.limitedBuilder().type( identifiedType ).content( fileContent ).paths( primaryDescriptorPath ).build();

            version = combineVersionAndSourcefile( repository.getFullName(), primaryDescriptorFile, workflow, identifiedType, version, existingDefaults );

            if( testParameterPaths != null )
            {
                List<String> missingParamFiles = new ArrayList<>();
                for ( String testParameterPath : testParameterPaths )
                {
                    // Only add test parameter file if it hasn't already been added
                    boolean hasDuplicate = version.getSourceFiles().stream()
                            .anyMatch( (SourceFile sf) -> sf.getPath().equals( testParameterPath ) && sf.getType() == workflow.getDescriptorType().getTestParamType() );
                    if( hasDuplicate )
                    {
                        continue;
                    }
                    String testFileContent = this.readFileFromRepo( testParameterPath, ref.refName(), repository );
                    if( testFileContent != null )
                    {
                        DescriptorLanguage.FileType testFileType = workflow.getDescriptorType().getTestParamType();
                        SourceFile testFile = SourceFile.limitedBuilder().type( testFileType ).content( testFileContent ).paths( testParameterPath ).build();
                        version.getSourceFiles().add( testFile );
                    }
                    else
                    {
                        missingParamFiles.add( testParameterPath );
                    }
                }

                if( missingParamFiles.size() > 0 )
                {
                    validationMessage = String.format( "The following %s missing: %s.", missingParamFiles.size() == 1 ? "file is" : "files are",
                            missingParamFiles.stream().map( paramFile -> String.format( "'%s'", paramFile ) ).collect( Collectors.joining( ", " ) ) );
                }
            }
        }
        else
        {
            // File not found or null
            LOG.info( "Could not find the file " + primaryDescriptorPath + " in repo " + repository );
            validationMessage = "Could not find the primary descriptor file '" + primaryDescriptorPath + "'.";
        }
        //commented
        //        try
        //        {
        //            DockstoreYamlHelper.validateDockstoreYamlProperties( dockstoreYml.getContent() ); // Validate that there are no unknown properties
        //        }
        //        catch (DockstoreYamlHelper.DockstoreYamlException ex)
        //        {
        //            validationMessage = validationMessage.isEmpty() ? ex.getMessage() : validationMessage + " " + ex.getMessage();
        //        }
        //
        //        Map<String, String> validationMessageObject = new HashMap<>();
        //        if( !validationMessage.isEmpty() )
        //        {
        //            validationMessageObject.put( DOCKSTORE_YML_PATH, validationMessage );
        //        }
        //        VersionTypeValidation dockstoreYmlValidationMessage = new VersionTypeValidation( validationMessageObject.isEmpty(), validationMessageObject );
        //        Validation dockstoreYmlValidation = new Validation( DescriptorLanguage.FileType.DOCKSTORE_YML, dockstoreYmlValidationMessage );
        //        version.addOrUpdateValidation( dockstoreYmlValidation );

        return version;
    }


    /**
     * Initializes workflow version for given branch
     *
     * @param branch
     * @param existingWorkflow
     * @param existingDefaults
     * @return workflow version
     */
    protected WorkflowVersion initializeWorkflowVersion(String branch, Optional<Workflow> existingWorkflow, Map<String, WorkflowVersion> existingDefaults)
    {
        WorkflowVersion version = new WorkflowVersion();
        version.setName( branch );
        version.setReference( branch );
        version.setValid( false );
        version.setSynced( true );

        // Determine workflow version from previous
        String calculatedPath = existingWorkflow.get().getDefaultWorkflowPath();

        // Set to false if new version
        if( existingDefaults.get( branch ) == null )
        {
            version.setDirtyBit( false );
            calculatedPath = existingWorkflow.get().getDefaultWorkflowPath();
        }
        else
        {
            // existing version
            if( existingDefaults.get( branch ).isDirtyBit() )
            {
                calculatedPath = existingDefaults.get( branch ).getWorkflowPath();
            }
            else
            {
                calculatedPath = existingWorkflow.get().getDefaultWorkflowPath();
            }
            version.setDirtyBit( existingDefaults.get( branch ).isDirtyBit() );
        }

        version.setWorkflowPath( calculatedPath );

        return version;
    }

    /**
     * Returns a workflow version with validation information updated
     * 
     * @param version Version to validate
     * @param entry Entry containing version to validate
     * @param mainDescriptorPath Descriptor path to validate
     * @return Workflow version with validation information
     */
    public WorkflowVersion versionValidation(WorkflowVersion version, Workflow entry, String mainDescriptorPath)
    {
        Set<SourceFile> sourceFiles = version.getSourceFiles();
        DescriptorLanguage.FileType identifiedType = entry.getFileType();
        Optional<SourceFile> mainDescriptor = sourceFiles.stream().filter( (sourceFile -> Objects.equals( sourceFile.getPath(), mainDescriptorPath )) ).findFirst();

        // Validate descriptor set
        if( mainDescriptor.isPresent() )
        {
            VersionTypeValidation validDescriptorSet;
            if( entry.getEntryType() == EntryType.APPTOOL )
            {
                validDescriptorSet = LanguageHandlerFactory.getInterface( identifiedType ).validateToolSet( sourceFiles, mainDescriptorPath );
            }
            else
            {
                validDescriptorSet = LanguageHandlerFactory.getInterface( identifiedType ).validateWorkflowSet( sourceFiles, mainDescriptorPath, entry );
            }
            Validation descriptorValidation = new Validation( identifiedType, validDescriptorSet );
            version.addOrUpdateValidation( descriptorValidation );
        }
        else
        {
            Map<String, String> validationMessage = new HashMap<>();
            validationMessage.put( mainDescriptorPath, "Primary descriptor file not found." );
            VersionTypeValidation noPrimaryDescriptor = new VersionTypeValidation( false, validationMessage );
            Validation noPrimaryDescriptorValidation = new Validation( identifiedType, noPrimaryDescriptor );
            version.addOrUpdateValidation( noPrimaryDescriptorValidation );
        }

        // Validate test parameter set
        VersionTypeValidation validTestParameterSet = LanguageHandlerFactory.getInterface( identifiedType ).validateTestParameterSet( sourceFiles );
        Validation testParameterValidation = new Validation( entry.getTestParameterType(), validTestParameterSet );
        version.addOrUpdateValidation( testParameterValidation );

        version.setValid( isValidVersion( version ) );

        return version;
    }

    /**
     * Resolves imports for a sourcefile, associates with version
     * 
     * @param repositoryId identifies the git repository that we wish to use, normally something like
     * 'organization/repo_name`
     * @param sourceFile
     * @param workflow
     * @param identifiedType
     * @param version
     * @return workflow version
     */
    WorkflowVersion combineVersionAndSourcefile(String repositoryId, SourceFile sourceFile, Workflow workflow, DescriptorLanguage.FileType identifiedType, WorkflowVersion version,
            Map<String, WorkflowVersion> existingDefaults)
    {
        Set<SourceFile> sourceFileSet = new HashSet<>();

        if( sourceFile != null && sourceFile.getContent() != null )
        {
            final Map<String, SourceFile> importFileMap = resolveImports( repositoryId, sourceFile.getContent(), identifiedType, version, sourceFile.getPath() );
            sourceFileSet.addAll( importFileMap.values() );
            final Map<String, SourceFile> otherFileMap = resolveUserFiles( repositoryId, identifiedType, version, importFileMap.keySet() );
            sourceFileSet.addAll( otherFileMap.values() );
        }

        // Look for test parameter files if existing workflow
        if( existingDefaults.get( version.getName() ) != null )
        {
            WorkflowVersion existingVersion = existingDefaults.get( version.getName() );
            DescriptorLanguage.FileType workflowDescriptorType = workflow.getTestParameterType();

            List<SourceFile> testParameterFiles = existingVersion.getSourceFiles().stream().filter( (SourceFile u) -> u.getType() == workflowDescriptorType ).toList();
            testParameterFiles.forEach( file -> this.readFile( repositoryId, existingVersion, sourceFileSet, workflowDescriptorType, file.getPath() ) );
        }

        // If source file is found and valid then add it
        if( sourceFile != null && sourceFile.getContent() != null )
        {
            // carry over metadata from plugins
            final Optional<SourceFile> matchingFile = sourceFileSet.stream().filter( f -> f.getPath().equals( sourceFile.getPath() ) ).findFirst();
            matchingFile.ifPresent( file -> sourceFile.getMetadata().setTypeVersion( file.getMetadata().getTypeVersion() ) );
            version.getSourceFiles().add( sourceFile );
        }

        // look for a mutated version and delete it first (can happen due to leading slash)
        if( sourceFile != null )
        {
            Set<SourceFile> collect = sourceFileSet.stream()
                    .filter( file -> file.getPath().equals( sourceFile.getPath() ) || file.getPath().equals( StringUtils.stripStart( sourceFile.getPath(), "/" ) ) )
                    .collect( Collectors.toSet() );
            sourceFileSet.removeAll( collect );
        }
        // add extra source files here (dependencies from "main" descriptor)
        if( sourceFileSet.size() > 0 )
        {
            version.getSourceFiles().addAll( sourceFileSet );
        }

        return version;
    }

    public Map<String, SourceFile> resolveImports(String repositoryId, String content, DescriptorLanguage.FileType fileType, WorkflowVersion version, String filepath)
    {
        LanguageHandlerInterface languageInterface = LanguageHandlerFactory.getInterface( fileType );
        return languageInterface.processImports( repositoryId, content, version, this, filepath );
    }

    public Map<String, SourceFile> resolveUserFiles(String repositoryId, DescriptorLanguage.FileType fileType, WorkflowVersion version, Set<String> excludePaths)
    {
        LanguageHandlerInterface languageInterface = LanguageHandlerFactory.getInterface( fileType );
        return languageInterface.processUserFiles( repositoryId, version.getUserFiles(), version, this, excludePaths );
    }

    /**
     * If this interface is pointed at a specific repository, grab a file from a specific branch/tag
     *
     * @param repositoryId identifies the git repository that we wish to use, normally something like
     * 'organization/repo_name`
     * @param fileName the name of the file (full path) to retrieve
     * @param reference the tag/branch to get the file from
     * @return content of the file
     */
    public String readFile(String repositoryId, String fileName, String reference)
    {
        //checkNotNull(fileName, "The fileName given is null.");

        GHRepository repo;
        try
        {
            repo = github.getRepository( repositoryId );
        }
        catch (IOException e)
        {
            LOG.error( gitUsername + ": IOException on readFile while trying to get the repository " + repositoryId + " " + e.getMessage(), e );
            throw new CustomLoggedException( "Could not get repository " + repositoryId + " from GitHub." );
        }
        return readFileFromRepo( fileName, reference, repo );
    }

    /**
     * Read a file from the importer and add it into files
     * 
     * @param repositoryId identifies the git repository that we wish to use, normally something like
     * 'organization/repo_name`
     * @param tag the version of source control we want to read from
     * @param files the files collection we want to add to
     * @param fileType the type of file
     */
    public void readFile(String repositoryId, WorkflowVersion tag, Collection<SourceFile> files, DescriptorLanguage.FileType fileType, String path)
    {
        Optional<SourceFile> sourceFile = this.readFile( repositoryId, tag, fileType, path );
        sourceFile.ifPresent( files::add );
    }

    /**
     * Read a file from the importer and add it into files
     * 
     * @param repositoryId identifies the git repository that we wish to use, normally something like
     * 'organization/repo_name`
     * @param tag the version of source control we want to read from
     * @param fileType the type of file
     */
    public Optional<SourceFile> readFile(String repositoryId, WorkflowVersion tag, DescriptorLanguage.FileType fileType, String path)
    {
        String fileResponse = this.readGitRepositoryFile( repositoryId, fileType, tag, path );
        if( fileResponse != null )
        {
            SourceFile dockstoreFile = SourceFile.limitedBuilder().type( fileType ).content( fileResponse ).paths( path ).build();
            return Optional.of( dockstoreFile );
        }
        return Optional.empty();
    }

    /**
     * Look in a source code repo for a particular file
     * 
     * @param repositoryId identifies the git repository that we wish to use, normally something like
     * 'organization/repo_name`
     * @param fileType
     * @param version
     * @param specificPath if specified, look for a specific file, otherwise return the "default" for a fileType
     * @return a FileResponse instance
     */
    public String readGitRepositoryFile(String repositoryId, DescriptorLanguage.FileType fileType, WorkflowVersion version, String specificPath)
    {

        final String reference = version.getReference();

        // Do not try to get file if the reference is not available
        if( reference == null )
        {
            return null;
        }

        String fileName = "";
        if( specificPath != null )
        {
            String workingDirectory = version.getWorkingDirectory();
            if( specificPath.startsWith( "/" ) )
            {
                // if we're looking at an absolute path, ignore the working directory
                fileName = specificPath;
            }
            else if( !workingDirectory.isEmpty() && !"/".equals( workingDirectory ) )
            {
                // if the working directory is different from the root, take it into account
                fileName = workingDirectory + "/" + specificPath;
            }
            else
            {
                fileName = specificPath;
            }
        }//!!!!! not processed but should?
        //        else if( version instanceof Tag tag )
        //        {
        //            // Add for new descriptor types
        //            if( fileType == DescriptorLanguage.FileType.DOCKERFILE )
        //            {
        //                fileName = tag.getDockerfilePath();
        //            }
        //            else if( fileType == DescriptorLanguage.FileType.DOCKSTORE_CWL )
        //            {
        //                if( Strings.isNullOrEmpty( tag.getCwlPath() ) )
        //                {
        //                    return null;
        //                }
        //                fileName = tag.getCwlPath();
        //            }
        //            else if( fileType == DescriptorLanguage.FileType.DOCKSTORE_WDL )
        //            {
        //                if( Strings.isNullOrEmpty( tag.getWdlPath() ) )
        //                {
        //                    return null;
        //                }
        //                fileName = tag.getWdlPath();
        //            }
        //        }
        else if( version instanceof WorkflowVersion workflowVersion )
        {
            fileName = workflowVersion.getWorkflowPath();
        }

        if( !fileName.isEmpty() )
        {
            return this.readFile( repositoryId, fileName, reference );
        }
        else
        {
            return null;
        }
    }

    /**
     * Read the specified list of files and directories.
     */
    public List<SourceFile> readPaths(String repositoryId, WorkflowVersion version, DescriptorLanguage.FileType fileType, Set<String> excludePaths, List<String> paths)
    {
        return paths.stream().flatMap( path -> readPath( repositoryId, version, fileType, excludePaths, path ).stream() ).toList();
    }

    /**
     * Read the specified file or directory and convert it to a list of SourceFiles. If the specified path is a file, a
     * list containing the single corresponding SourceFile is returned. If the specified path is a directory, it is
     * searched recursively and a SourceFile corresponding to each file is returned. If the specified path is neither a
     * file or directory, or if the path is excluded, an empty list is returned.
     */
    public List<SourceFile> readPath(String repositoryId, WorkflowVersion version, DescriptorLanguage.FileType fileType, Set<String> excludePaths, String path)
    {
        // If the path is excluded, return an empty list.
        if( excludePaths.contains( path ) )
        {
            return List.of();
        }
        // Attempt to read the path as a file, and if we're successful, return it.
        Optional<SourceFile> file = readFile( repositoryId, version, fileType, path );
        if( file.isPresent() )
        {
            return List.of( file.get() );
        }
        // Attempt to list the contents of the path as if it was a directory, and if we're successful, read the contents.
        List<String> names = listFiles( repositoryId, path, version.getReference() );
        if( names != null )
        {
            return readPaths( repositoryId, version, fileType, excludePaths, prependPath( path, names ) );
        }
        // We couldn't read the path, return an empty list.
        return List.of();
    }

    private List<String> prependPath(String path, List<String> names)
    {
        String normalizedPath = path.endsWith( "/" ) ? path : path + "/";
        return names.stream().map( name -> normalizedPath + name ).toList();
    }

    public List<String> listFiles(String repositoryId, String pathToDirectory, String reference)
    {
        GHRepository repo;
        try
        {
            repo = github.getRepository( repositoryId );
            List<GHContent> directoryContent = repo.getDirectoryContent( pathToDirectory, reference );
            return directoryContent.stream().map( GHContent::getName ).toList();
        }
        catch (IOException e)
        {
            LOG.error( gitUsername + ": IOException on listFiles in " + pathToDirectory + " for repository " + repositoryId + ":" + reference + ", " + e.getMessage(), e );
            return null;
        }
    }

    public Notebook initializeNotebookFromGitHub(String repositoryId, String format, String language, String workflowName)
    {
        Notebook notebook = new Notebook();
        setWorkflowInfo( repositoryId, format, language, workflowName, notebook );
        return notebook;
    }

    /**
     * Initialize workflow object for GitHub repository
     * 
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     * @param subclass Subclass of the workflow
     * @param workflowName Name of the workflow
     * @return Workflow
     */
    public Workflow initializeWorkflowFromGitHub(String repositoryId, String subclass, String workflowName)
    {
        Workflow workflow = new Workflow();
        setWorkflowInfo( repositoryId, subclass, DescriptorLanguageSubclass.NOT_APPLICABLE.toString(), workflowName, workflow );
        return workflow;
    }

    public AppTool initializeOneStepWorkflowFromGitHub(String repositoryId, String subclass, String workflowName)
    {
        AppTool appTool = new AppTool();
        setWorkflowInfo( repositoryId, subclass, DescriptorLanguageSubclass.NOT_APPLICABLE.toString(), workflowName, appTool );
        return appTool;
    }

    /**
     * Initialize bioworkflow/apptool object for GitHub repository
     * 
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     * @param typeSubclass Subclass of the workflow
     * @param workflowName Name of the workflow
     * @param workflow Workflow to update
     * @return Workflow
     */
    private void setWorkflowInfo(final String repositoryId, final String type, final String typeSubclass, final String workflowName, final Workflow workflow)
    {

        // The checks/catches in the following blocks are all backups, they should not fail in normal operation.
        // Thus, the error messages are more technical and less user-friendly.
        //
        // setDescriptorType() needs to execute before setDefaultWorkflowPath(), because
        // setDefaultWorkflowPath() is not a simple property setter, but one that adds to map
        // where the key is getDescriptorType(). #5636
        try
        {
            DescriptorLanguage descriptorLanguage = DescriptorLanguage.convertShortStringToEnum( type );
            if( descriptorLanguage.getEntryTypes().contains( workflow.getEntryType() ) )
            {
                workflow.setDescriptorType( descriptorLanguage );
            }
            else
            {
                logAndThrowLambdaFailure( String.format( "The descriptor type %s is not supported by the %s", descriptorLanguage, workflow.getEntryType() ) );
            }
        }
        catch (UnsupportedOperationException ex)
        {
            logAndThrowLambdaFailure( String.format( "Type %s is not a valid descriptor language.", type ) );
        }

        workflow.setWorkflowName( workflowName );
        workflow.setOrganization( repositoryId.split( "/" )[0] );
        workflow.setRepository( repositoryId.split( "/" )[1] );
        workflow.setSourceControl( SourceControl.GITHUB );
        workflow.setGitUrl( "git@github.com:" + repositoryId + ".git" );
        workflow.setLastUpdated( new Date() );
        workflow.setDefaultWorkflowPath( DOCKSTORE_YML_PATH );
        workflow.setMode( WorkflowMode.DOCKSTORE_YML );
        //workflow.setTopicAutomatic( repository.getDescription() );
        workflow.setGitVisibility( getGitVisibility( repositoryId ) );
        //this.setLicenseInformation( workflow, repositoryId );

        try
        {
            DescriptorLanguageSubclass descriptorLanguageSubclass = DescriptorLanguageSubclass.convertShortNameStringToEnum( typeSubclass );
            if( descriptorLanguageSubclass.getEntryTypes().contains( workflow.getEntryType() ) )
            {
                workflow.setDescriptorTypeSubclass( descriptorLanguageSubclass );
            }
            else
            {
                logAndThrowLambdaFailure( String.format( "The descriptor type subclass %s is not supported by the %s", descriptorLanguageSubclass, workflow.getEntryType() ) );
            }
        }
        catch (UnsupportedOperationException ex)
        {
            logAndThrowLambdaFailure( String.format( "Subclass %s is not a valid descriptor language subclass.", typeSubclass ) );
        }
    }

    private void logAndThrowLambdaFailure(String message)
    {
        LOG.error( message );
        throw new CustomLoggedException( message );
    }

    public WorkflowVersion addDockstoreYmlVersionToWorkflow(String repository, String gitReference, SourceFile dockstoreYml, Workflow workflow, boolean latestTagAsDefault)
    {
        Instant startTime = Instant.now();
        try
        {
            // Create version and pull relevant files
            WorkflowVersion remoteWorkflowVersion = createVersionForWorkflow( repository, gitReference, workflow, dockstoreYml );
            remoteWorkflowVersion.setReferenceType( getReferenceTypeFromGitRef( gitReference ) );
            // Update the version metadata of the remoteWorkflowVersion. This will also set authors found in the descriptor.
            updateVersionMetadata( remoteWorkflowVersion.getWorkflowPath(), remoteWorkflowVersion, workflow.getDescriptorType(), repository );
            // Set .dockstore.yml authors if they exist, which will override the descriptor authors that were set by updateVersionMetadata.
            //            if (!yamlAuthors.isEmpty()) {
            //                setDockstoreYmlAuthorsForVersion(yamlAuthors, remoteWorkflowVersion);
            //            }

            // Mark the version as valid/invalid.
            remoteWorkflowVersion.setValid( isValidVersion( remoteWorkflowVersion ) );

            // So we have workflowversion which is the new version, we want to update the version and associated source files
            //            WorkflowVersion existingWorkflowVersion = workflowVersionDAO.getWorkflowVersionByWorkflowIdAndVersionName(workflow.getId(), remoteWorkflowVersion.getName());
            //            WorkflowVersion updatedWorkflowVersion;
            //            // Update existing source files, add new source files, remove deleted sourcefiles, clear json for dag and tool table
            //            if (existingWorkflowVersion != null) {
            //                // Only update workflow if it's not frozen
            //                if (!existingWorkflowVersion.isFrozen()) {
            //                    // Copy over workflow version level information.
            //                    existingWorkflowVersion.setWorkflowPath(remoteWorkflowVersion.getWorkflowPath());
            //                    existingWorkflowVersion.setLastModified(remoteWorkflowVersion.getLastModified());
            //                    existingWorkflowVersion.setLegacyVersion(remoteWorkflowVersion.isLegacyVersion());
            //                    existingWorkflowVersion.setAliases(remoteWorkflowVersion.getAliases());
            //                    existingWorkflowVersion.setCommitID(remoteWorkflowVersion.getCommitID());
            //                    existingWorkflowVersion.setDagJson(null);
            //                    existingWorkflowVersion.setToolTableJson(null);
            //                    existingWorkflowVersion.setReferenceType(remoteWorkflowVersion.getReferenceType());
            //                    existingWorkflowVersion.setValid(remoteWorkflowVersion.isValid());
            //                    existingWorkflowVersion.setAuthors(remoteWorkflowVersion.getAuthors());
            //                    existingWorkflowVersion.setOrcidAuthors(remoteWorkflowVersion.getOrcidAuthors());
            //                    existingWorkflowVersion.setKernelImagePath(remoteWorkflowVersion.getKernelImagePath());
            //                    existingWorkflowVersion.setReadMePath(remoteWorkflowVersion.getReadMePath());
            //                    existingWorkflowVersion.setDescriptionAndDescriptionSource(remoteWorkflowVersion.getDescription(), remoteWorkflowVersion.getDescriptionSource());
            //                    // this kinda sucks but needs to be updated with workflow metadata too
            //                    existingWorkflowVersion.getVersionMetadata().setEngineVersions(remoteWorkflowVersion.getVersionMetadata().getEngineVersions());
            //                    existingWorkflowVersion.getVersionMetadata().setDescriptorTypeVersions(remoteWorkflowVersion.getVersionMetadata().getDescriptorTypeVersions());
            //                    existingWorkflowVersion.getVersionMetadata().setParsedInformationSet(remoteWorkflowVersion.getVersionMetadata().getParsedInformationSet());
            //                    existingWorkflowVersion.getVersionMetadata().setPublicAccessibleTestParameterFile(remoteWorkflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
            //
            //                    updateDBVersionSourceFilesWithRemoteVersionSourceFiles(existingWorkflowVersion, remoteWorkflowVersion,
            //                            workflow.getDescriptorType());
            //                }
            //                updatedWorkflowVersion = existingWorkflowVersion;
            //            } else {
            //                if (checkUrlInterface != null) {
            //                    publicAccessibleUrls(remoteWorkflowVersion, checkUrlInterface, workflow.getDescriptorType());
            //                }
            workflow.addWorkflowVersion( remoteWorkflowVersion );
            WorkflowVersion updatedWorkflowVersion = remoteWorkflowVersion;
            //}

            //            if (workflow.getLastModified() == null || (updatedWorkflowVersion.getLastModified() != null && workflow.getLastModifiedDate().before(updatedWorkflowVersion.getLastModified()))) {
            //                workflow.setLastModified(updatedWorkflowVersion.getLastModified());
            //            }

            // Check the version to see if it exceeds any limits.
            //LimitHelper.checkVersion(updatedWorkflowVersion);

            // Update verification information.
            //updatedWorkflowVersion.updateVerified();

            // Update file formats for the version and then the entry.
            // TODO: We were not adding file formats to .dockstore.yml versions before, so this only handles new/updated versions. Need to add a way to update all .dockstore.yml versions in a workflow
            //???FileFormatHelper.updateFileFormats(workflow, Set.of(updatedWorkflowVersion), fileFormatDAO, false);

            // If this version corresponds to the latest tag, make it the default version, if appropriate.
            setDefaultVersionToLatestTagIfAppropriate( latestTagAsDefault, workflow, updatedWorkflowVersion );

            // If this version corresponds to the GitHub default branch, make it the default version, if appropriate.
            setDefaultVersionToGitHubDefaultIfAppropriate( latestTagAsDefault, workflow, updatedWorkflowVersion, repository );

            // Log that we've successfully added the version.
            LOG.info( "Version " + remoteWorkflowVersion.getName() + " has been added to workflow " + workflow.getWorkflowPath() + "." );

            // Update index if default version was updated
            // verified and verified platforms are the only versions-level properties unrelated to default version that affect the index but GitHub Apps do not update it
            //            if (workflow.getActualDefaultVersion() != null && updatedWorkflowVersion.getName() != null && workflow.getActualDefaultVersion().getName().equals(updatedWorkflowVersion.getName())) {
            //                workflow.syncMetadataWithDefault();
            //                PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
            //            }
            //
            //            Instant endTime = Instant.now();
            //            long timeElasped = Duration.between(startTime, endTime).toSeconds();
            //            if (LOG.isInfoEnabled()) {
            //                LOG.info(
            //                    "Processing .dockstore.yml workflow version {} for repo: {} took {} seconds", Utilities.cleanForLogging(gitReference), Utilities.cleanForLogging(repository), timeElasped);
            //            }

            return updatedWorkflowVersion;

        }
        catch (IOException ex)
        {
            final String message = "Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid tag.";
            LOG.error( message, ex );
            throw new CustomLoggedException( message );
        }
    }

    /**
     * Checks if the given workflow version is valid based on existing validations
     * 
     * @param version Version to check validation
     * @return True if valid workflow version, false otherwise
     */
    public boolean isValidVersion(WorkflowVersion version)
    {
        return version.getValidations().stream().filter( validation -> !Objects.equals( validation.getType(), DescriptorLanguage.FileType.DOCKSTORE_YML ) )
                .allMatch( Validation::isValid );
    }

    private void setDefaultVersionToLatestTagIfAppropriate(boolean latestTagAsDefault, Workflow workflow, WorkflowVersion version)
    {
        boolean addedVersionIsNewer = workflow.getActualDefaultVersion() == null || workflow.getActualDefaultVersion().getLastModified().before( version.getLastModified() );
        if( latestTagAsDefault && WorkflowVersion.ReferenceType.TAG.equals( version.getReferenceType() ) && addedVersionIsNewer )
        {
            LOG.info( "default version set to latest tag " + version.getName() );
            workflow.setActualDefaultVersion( version );
        }
    }

    private void setDefaultVersionToGitHubDefaultIfAppropriate(boolean latestTagAsDefault, Workflow workflow, WorkflowVersion version, String repositoryId)
    {
        // If the default version isn't set, the latest tag is not the default, the version is a branch,
        // and the version's name is the same as the GitHub repo's default branch name, use this version
        // as the workflow's default version.
        if( workflow.getActualDefaultVersion() == null && !latestTagAsDefault && Objects.equals( version.getReferenceType(), WorkflowVersion.ReferenceType.BRANCH )
                && Objects.equals( version.getName(), getDefaultBranch( repositoryId ) ) )
        {
            LOG.info( "default version set to GitHub default branch " + version.getName() );
            workflow.setActualDefaultVersion( version );
        }
    }

    public String getDefaultBranch(String repositoryId)
    {
        if( repositoryId != null )
        {
            try
            {
                GHRepository repository = github.getRepository( repositoryId );
                // Determine the default branch on GitHub
                return repository.getDefaultBranch();
            }
            catch (IOException e)
            {
                LOG.error( "Unable to retrieve default branch for repository " + repositoryId, e );
                return null;
            }
        }
        return null;
    }

    //TODO: commented all 
    public void updateVersionMetadata(String filePath, WorkflowVersion version, DescriptorLanguage type, String repositoryId)
    {
        Set<SourceFile> sourceFiles = version.getSourceFiles();
        String branch = version.getName();
        //        if (Strings.isNullOrEmpty(filePath) && LOG.isInfoEnabled()) {
        //            String message = String.format("%s : No descriptor found for %s.", Utilities.cleanForLogging(repositoryId), Utilities.cleanForLogging(branch));
        //            LOG.info(message);
        //        }
        if( sourceFiles == null || sourceFiles.isEmpty() )
        {
            //            if (LOG.isInfoEnabled()) {
            //                String message = String
            //                    .format("%s : Error getting descriptor for %s with path %s", Utilities.cleanForLogging(repositoryId), Utilities.cleanForLogging(branch), Utilities.cleanForLogging(filePath));
            //                LOG.info(message);
            //            }
            //            if (version.getReference() != null) {
            //                String readMeContent = getReadMeContent(repositoryId, version.getReference(), version.getReadMePath());
            //                if (StringUtils.isNotBlank(readMeContent)) {
            //                    version.setDescriptionAndDescriptionSource(readMeContent, DescriptionSource.README);
            //                }
            //            }
            return;
        }
        String fileContent;
        Optional<SourceFile> first = sourceFiles.stream().filter( file -> file.getPath().equals( filePath ) ).findFirst();
        if( first.isPresent() )
        {
            //            fileContent = first.get().getContent();
            //            LanguageHandlerInterface anInterface = LanguageHandlerFactory.getInterface(type);
            //            anInterface.parseWorkflowContent(filePath, fileContent, sourceFiles, version);
            //            boolean noDescription = (version.getDescription() == null || version.getDescription().isEmpty()) && version.getReference() != null;
            //            String readmeContent = getReadMeContent(repositoryId, version.getReference(), version.getReadMePath());
            //            if (!Strings.isNullOrEmpty(version.getReadMePath())) {
            //                // overwrite description from descriptor if there is a custom path specified in the .dockstore.yml
            //                version.setDescriptionAndDescriptionSource(readmeContent, DescriptionSource.CUSTOM_README);
            //            } else if (noDescription) {
            //                // use the root README as a fallback if there is no other description
            //                if (StringUtils.isNotBlank(readmeContent)) {
            //                    version.setDescriptionAndDescriptionSource(readmeContent, DescriptionSource.README);
            //                }
            //            }
        }
    }

    private WorkflowVersion.ReferenceType getReferenceTypeFromGitRef(String gitRef)
    {
        if( gitRef.startsWith( "refs/heads/" ) )
        {
            return WorkflowVersion.ReferenceType.BRANCH;
        }
        else if( gitRef.startsWith( "refs/tags/" ) )
        {
            return WorkflowVersion.ReferenceType.TAG;
        }
        else
        {
            return WorkflowVersion.ReferenceType.NOT_APPLICABLE;
        }
    }

    /**
     * Retrieves a tag/branch from GitHub and creates a version on Dockstore
     * 
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Branch/tag reference from GitHub (ex. refs/tags/1.0)
     * @param workflow Workflow to add version to
     * @param dockstoreYml Dockstore YML sourcefile
     * @return New or updated version
     * @throws IOException
     */
    public WorkflowVersion createVersionForWorkflow(String repository, String gitReference, Workflow workflow, SourceFile dockstoreYml) throws IOException
    {
        GHRepository ghRepository = getRepository( repository );

        // Match the GitHub reference (ex. refs/heads/feature/foobar or refs/tags/1.0)
        Matcher matcher = GIT_BRANCH_TAG_PATTERN.matcher( gitReference );

        if( !matcher.find() )
        {
            throw new CustomLoggedException( "Reference " + gitReference + " is not of the valid form" );
        }
        String gitBranchType = matcher.group( 1 );
        String gitBranchName = matcher.group( 2 );

        GHRef ghRef = ghRepository.getRef( gitBranchType + "/" + gitBranchName );

        GitReferenceInfo ref = getRef( ghRef, ghRepository );
        if( ref == null )
        {
            throw new CustomLoggedException( "Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid branch/tag." );
        }

        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();

        // Create version with sourcefiles and validate
        return setupWorkflowVersionsHelper( workflow, ref, Optional.of( workflow ), existingDefaults, ghRepository, dockstoreYml, Optional.empty() );
    }

    /**
     * Determines the visibility of a GitHub repo.
     * 
     * @param repositoryId
     * @return
     */
    public GitVisibility getGitVisibility(String repositoryId)
    {
        try
        {
            GHRepository repository = github.getRepository( repositoryId );
            return repository.isPrivate() ? GitVisibility.PRIVATE : GitVisibility.PUBLIC;
        }
        catch (GHFileNotFoundException e)
        {
            LOG.error( String.format( "Repository %s not found checking for visibility", repositoryId ), e );
            // We don't know if it's not found because it doesn't exist, or because it's private and we don't have access to it
            return GitVisibility.PRIVATE_OR_NON_EXISTENT;
        }
        catch (IOException e)
        {
            LOG.error( String.format( "Unknown error checking visibility for %s", repositoryId ), e );
            return GitVisibility.UNKNOWN;
        }
    }

    public GitVisibility getGitVisibility(GHRepository repository)
    {
        return repository.isPrivate() ? GitVisibility.PRIVATE : GitVisibility.PUBLIC;
    }

    public record GitReferenceInfo(String refName, Date branchDate, String sha)  {    }

    public enum GitVisibility
    {
        /**
         * There was a failed attempt to determine visibility
         */
        UNKNOWN,
        /**
         * A private repo
         */
        PRIVATE,
        /**
         * A public repo
         */
        PUBLIC,
        /**
         * The Git repo is either private or does not exist, but we cannot tell which.
         */
        PRIVATE_OR_NON_EXISTENT
    }

    public enum SourceControl
    {
        // Add new source control here
        DOCKSTORE("dockstore.org", "Dockstore"), GITHUB("github.com", "GitHub"), BITBUCKET("bitbucket.org", "BitBucket"), GITLAB("gitlab.com", "GitLab");

        /**
         * this name is used in the source control path
         */
        private final String sourceControlPath;

        /**
         * this name is what is displayed to users to name the source control
         */
        private final String friendlyName;

        SourceControl(final String sourceControlPath, final String friendlyName)
        {
            this.sourceControlPath = sourceControlPath;
            this.friendlyName = friendlyName;
        }

        @Override
        public String toString()
        {
            return sourceControlPath;
        }

        public String getFriendlyName()
        {
            return friendlyName;
        }

        /**
         * Expanded version for API list of source control
         */
        public static class SourceControlBean
        {

            public String value;

            public String friendlyName;

            public SourceControlBean(SourceControl sourceControl)
            {
                this.value = sourceControl.toString();
                this.friendlyName = sourceControl.getFriendlyName();
            }
        }
    }

}
