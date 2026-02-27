package operations

import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Map.Entry

import javax.json.JsonObject
import javax.json.JsonString

import com.developmentontheedge.be5.databasemodel.util.DpsUtils
import com.developmentontheedge.be5.server.model.Base64File
import com.developmentontheedge.be5.server.operations.support.GOperationSupport

import com.developmentontheedge.be5.operation.OperationResult

import ru.genespace.dockstore.SourceFile
import ru.genespace.dockstore.Workflow
import ru.genespace.dockstore.WorkflowVersion
import ru.genespace.dockstore.yaml.DockstoreYaml12
import ru.genespace.dockstore.yaml.DockstoreYamlHelper
import ru.genespace.dockstore.yaml.YamlWorkflow
import ru.genespace.github.GitHubManager
import ru.genespace.github.GitHubRepository
import ru.genespace.dockstore.DescriptorLanguage

class AddRepository extends GOperationSupport {
    Map<String, Object> presets

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
            def wflID = database.resources << [repository: repoID, name: wflName, type: "workflow", language: lang, topic: workflow.getTopic()]
            for(WorkflowVersion version: versions) {
                /* Versions
                 repository: repositories.ID
                 name: branch or tag name
                 commit: Git commit
                 dateModified: date of last update to Git reference
                 type: (branch, tag) default "'branch'"
                 doi: DOI for this version of Git repository 
                 */
                //TODO: doi is not supported now
                def versionDB = database.versions.getBy( [repository: repoID, name: version.getName(), commit: version.getCommitID(),
                    type: version.getReferenceType().toString()])
                def versionID = versionDB ? versionDB.$ID : null
                if(versionID == null) {
                    def versionName = version.getName()!= null ? version.getName() : "name is unset";
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
                database.resource2versions << [resource: wflID, version:versionID, valid: version.isValid() ? 'yes' : 'no', primaryDescriptorPath: primaryDescriptorPath, readMePath: readmePath ]
            }
        }
        setResult(OperationResult.finished())
    }

    private Timestamp getDateTime(Date date) {

        Instant instant = date.toInstant();
        return Timestamp.from( instant );
    }
}
