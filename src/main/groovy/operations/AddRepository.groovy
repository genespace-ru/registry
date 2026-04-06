package operations

import java.awt.image.BufferedImage
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Map.Entry
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Matcher
import java.util.regex.Pattern

import javax.imageio.ImageIO

import javax.json.JsonObject
import javax.json.JsonString
import javax.security.sasl.AuthorizeCallback

import org.json.JSONObject
import com.developmentontheedge.be5.databasemodel.util.DpsUtils
import com.developmentontheedge.be5.server.operations.support.GOperationSupport

import com.developmentontheedge.be5.operation.OperationResult

import ru.genespace.dockstore.ORCIDHelper
import ru.genespace.dockstore.SourceFile
import ru.genespace.dockstore.Validation
import ru.genespace.dockstore.Workflow
import ru.genespace.dockstore.WorkflowVersion
import ru.genespace.dockstore.yaml.DockstoreYaml12
import ru.genespace.dockstore.yaml.DockstoreYamlHelper
import ru.genespace.dockstore.yaml.YamlWorkflow
import ru.genespace.github.GitHubManager
import ru.genespace.github.GitHubRepository
import ru.genespace.webserver.WebserverController
import ru.genespace.dockstore.Author
import ru.genespace.dockstore.DescriptorLanguage
import ru.genespace.dockstore.Image
import ru.genespace.dockstore.OrcidAuthor

import static ru.genespace.dockstore.Constants.DOCKSTORE_YML_PATHS_SET

class AddRepository extends GOperationSupport {
    Map<String, Object> presets

    private static final Logger log = Logger.getLogger( AddRepository.class.getName() )

    @Override
    Object getParameters(Map<String, Object> presetValues) throws Exception {
        presets = presetValues
        params.repositoryPath = [value: presetValues.get("repositoryPath" ),
            DISPLAY_NAME: "Repository name (must be in format 'owner/repo' or 'https://github.com/owner/repo')", TYPE: String]
        params.doi = [value: presetValues.get("doi" ),
            DISPLAY_NAME: "DOI", TYPE: String, CAN_BE_NULL: true]

        return params
    }

    @Override
    void invoke(Object parameters) throws Exception {

        def repositoryPath = params.$repositoryPath
        def repositoryId = repositoryPath
        def httpsGitHubPath = "https://github.com/"
        if(repositoryPath.startsWith("github://"))
            repositoryId = repositoryPath.substring(9)
        else if(repositoryPath.startsWith(httpsGitHubPath))
            repositoryId = repositoryPath.substring(httpsGitHubPath.length())

        def doi = params.$doi
        def repoID = database.repositories << [
            url : repositoryId,
            doi : doi
        ]

        //TODO: possibility to change token/user by interface
        //def user = database.users.getBy([ user_name: userInfo.userName ])
        def githubUser = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_user'" )
        def githubToken = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_token'" )


        GitHubManager gitHubManager = new GitHubManager(githubUser, githubToken)
        Map<String, Workflow> workflows = gitHubManager.processRepository(repositoryId )
        for ( Entry<String, Workflow> e : workflows.entrySet() ) {
            /*Resources: 
             repository: repositories.ID
             name:
             type: ENUM(tool, workflow, notebook)
             language: ENUM(CWL, WDL, NFL)
             readMePath: An optional path to a resource-specific readme in the Git repository.
             topic: An optional short text description of the resource.
             info: JSONB Other structured information from dockstore.yml*/
            Workflow workflow = e.getValue()
            String lang = workflow.getDescriptorType().getShortName()

            Set<WorkflowVersion> versions = workflow.getWorkflowVersions()
            //TODO: info
            def wflName = workflow.getWorkflowName()
            if(wflName == null && workflow.getDescriptorType().equals(DescriptorLanguage.NEXTFLOW ))
                wflName = "main.nf"
            else if(wflName == null)
                wflName = "undefined"
            def topic = workflow.getTopic()
            if(topic != null && topic.length() > 200)
                topic = topic.substring(0,200 )
            def wflID = database.resources << [repository: repoID, name: wflName, type: "workflow", language: lang, topic: topic]
            for(WorkflowVersion version: versions) {
                def versionName = version.getName()!= null ? version.getName() : "name is unset"
                /* Versions
                 repository: repositories.ID
                 name: branch or tag name
                 commit: Git commit
                 dateModified: date of last update to Git reference
                 type: (branch, tag) default "'branch'"
                 doi: DOI for this version of Git repository 
                 */
                //TODO: doi is not supported now
                def versionDB = database.versions.getBy( [repository: repoID, name: versionName, commit: version.getCommitID(),
                    type: version.getReferenceType().toString()])
                def versionID = versionDB ? versionDB.$ID : null
                if(versionID == null) {
                    versionID = database.versions << [repository: repoID, name: versionName, commit: version.getCommitID(),
                        dateModified: getDateTime(version.getLastModified()), type: version.getReferenceType().toString()]
                }
                /* ResourceToVersion
                 resource: resources.ID
                 version: versions.ID
                 language: language version used by corresponding resource
                 primaryDescriptorPath: The absolute path to the primary descriptor file in the Git repository. 
                 - For CWL, the primary descriptor is a .cwl file.
                 - For WDL, the primary descriptor is a .wdl file.
                 - Nextflow differs from these as the primary descriptor is a nextflow.config file.
                 readMePath: An optional path to a resource-specific readme in the Git repository. If not specified, gneeric github repo README.md will be used
                 valid: ENUM(yes, no)  a version is valid if the descriptor file(s) have been successfully validated         
                 doi: DOI for this resource of this version of Git repository
                 snapshot: ENUM(yes, no) indicates that this version of the resource is snapshot     
                 */
                //TODO: doi, language, snapshot
                def primaryDescriptorPath = version.getWorkflowPath()
                def readmePath = version.getReadMePath()
                def valid = version.isValid() ? 'yes' : 'no'
                if(!version.isValid()) {
                    log.log(Level.WARNING, createValidationMessages(version))
                }
                //set first version as default since it is necessary
                def isDefaultVersion = (workflow.getActualDefaultVersion() == null || versionName.equals(workflow.getActualDefaultVersion().getName())) ? 'yes' : 'no'
                def res2ver = database.resource2versions << [resource: wflID, version:versionID, valid: version.isValid() ? 'yes' : 'no', primaryDescriptorPath: primaryDescriptorPath,
                    readMePath: readmePath, defaultVersion: isDefaultVersion]

                //docker images
                for(Image image: version.getImages()) {
                    def dockerDB = database.docker.getBy( [image: image.getImageID()])
                    def dockerID = dockerDB ? dockerDB.$ID : null
                    if(dockerID == null) {
                        String url = image.getImageRegistry().getUrl()
                        dockerID = database.docker << [image: image.getImageID(), url: image.getImageURL()]
                    }
                    def res2docker = database.resource2docker.getBy( [docker: dockerID, resource: wflID, version: versionID])
                    if(res2docker == null)
                        database.resource2docker << [docker: dockerID, resource: wflID, version: versionID ]
                }

                //attachments
                for(SourceFile sf: version.getSourceFiles()) {
                    if(DOCKSTORE_YML_PATHS_SET.contains(sf.getPath()))
                        continue;
                    if( sf.getPath().equals(primaryDescriptorPath ) ) {
                        def content = sf.getContent()
                        if(content != null) {
                            byte[] data = content.getBytes("UTF-8")
                            database.attachments << [ownerID: res2ver, ownerType: "resource2versions", fileName: sf.getAbsolutePath(), mimeType:"text/plain", isFetched:'yes', data:data, description:"workflow file"]
                        }
                        else {
                            def mimeType = getMimeType(sf)
                            database.attachments << [ownerID: res2ver, ownerType: "resource2versions", fileName: sf.getAbsolutePath(), mimeType:mimeType, isFetched:'no', description:"workflow file"]
                        }
                    }
                    else {
                        def mimeType = getMimeType(sf)
                        database.attachments << [ownerID: res2ver, ownerType: "resource2versions", fileName:  sf.getAbsolutePath(), mimeType:mimeType, isFetched:'no', description:"workflow file"]
                    }
                }

                //authors
                fillAuthors(version.getAuthors(), wflID, versionID, false)
                fillAuthors(version.getOrcidAuthors(), wflID, versionID, true)
            }
        }
        setResult(OperationResult.finished())
    }

    private void fillAuthors(Set<Author> authors, long wflID, long versionID, boolean isOrcid) {
        ORCIDHelper helper = isOrcid ? getORCIDHelper():null
        for(Author author: authors) {

            def authorDB ;
            if(isOrcid)
                authorDB = database.authors.getBy( [orcid: ((OrcidAuthor)author).getOrcid()])
            else
                authorDB = database.authors.getBy( [name: author.getName(), email: author.getEmail()])

            def authorID = authorDB ? authorDB.$ID : null
            if(authorID == null) {
                def params = [:]
                if(isOrcid)
                    helper.fillOrcidInfo(((OrcidAuthor)author))
                if(author.getName() == null)
                    continue
                params = [name: author.getName()]
                if(author.getEmail())
                    params.email= author.getEmail()
                if(author.getRole())
                    params.role = author.getRole()
                if(author.getAffiliation())
                    params.affiliation = author.getAffiliation()
                if(isOrcid)
                    params.orcid=((OrcidAuthor)author).getOrcid()
                authorID = database.authors << params
            }
            def res2author = database.resource2author.getBy( [author: authorID, resource: wflID, version: versionID])
            if(res2author == null)
                database.resource2author << [author: authorID, resource: wflID, version: versionID ]
        }
    }

    private static final Pattern IMAGE_PATTERN = Pattern.compile(/[^\\s]+(\\.(?i)(jpg|png|gif|bmp))$/)

    def isImageFile(filename) {
        def imageRegex = ~/([^\\s]+(\.(?i)(jpe?g|png|gif|bmp|webp|tiff?)))$/
        // The find operator returns a Matcher object. Calling asBoolean() checks for a match.
        return (filename =~ imageRegex).asBoolean()
    }

    private String getMimeType(SourceFile sf) {

        if( sf.getType().equals(DescriptorLanguage.FileType.DOCKERFILE) )
            return  "application/octet-stream"
        if(isImageFile(sf.getAbsolutePath()))
            return "image/png"
        return "text/plain"
    }

    private ORCIDHelper getORCIDHelper() {
        def token = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='orcid_token'" )
        if(token == null)
            return null
        return new ORCIDHelper(token)
    }

    /**
     * Prints out all of the invalid validations
     * Used for returning error messages on attempting to save
     * @param version version of interest
     * @return String containing all invalid validation messages
     */
    private String createValidationMessages(WorkflowVersion version) {
        StringBuilder result = new StringBuilder();
        result.append("Version was not validates due to the following error(s): ");

        for (Validation versionValidation : version.getValidations()) {
            if (!versionValidation.isValid() && versionValidation.getMessage() != null) {
                JSONObject obj = new JSONObject(versionValidation.getMessage())
                Iterator<?> keys = obj.keys();
                while(keys.hasNext()) {
                    String name = keys.next().toString()
                    String value = obj.getString(name )
                    result.append(name + ": " + value + " ")
                }
            }
        }
        return result.toString()
    }

    private Timestamp getDateTime(Date date) {

        Instant instant = date.toInstant();
        return Timestamp.from( instant );
    }
}
