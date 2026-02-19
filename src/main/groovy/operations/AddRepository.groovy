package operations

import java.util.Map.Entry

import com.developmentontheedge.be5.databasemodel.util.DpsUtils
import com.developmentontheedge.be5.server.model.Base64File
import com.developmentontheedge.be5.server.operations.support.GOperationSupport
import ru.genespace.dockstore.SourceFile
import ru.genespace.dockstore.Workflow
import ru.genespace.dockstore.WorkflowVersion
import ru.genespace.dockstore.yaml.DockstoreYaml12
import ru.genespace.dockstore.yaml.DockstoreYamlHelper
import ru.genespace.dockstore.yaml.YamlWorkflow
import ru.genespace.github.GitHubManager
import ru.genespace.github.GitHubRepository

class AddRepository extends GOperationSupport {
    Map<String, Object> presets

    @Override
    Object getParameters(Map<String, Object> presetValues) throws Exception {
        presets = presetValues
        params.repositoryPath = [value: presetValues.get("repositoryPath" ),
            DISPLAY_NAME: "Repository URL", TYPE: String]
        params.doi = [value: presetValues.get("doi" ),
            DISPLAY_NAME: "DOI", TYPE: String, CAN_BE_NULL: true]
        return params
    }

    @Override
    void invoke(Object parameters) throws Exception {
        def repositoryPath = params.$repositoryPath
        def doi = params.$doi
        def repoID = database.repositories << [
            url  : repositoryPath,
            doi : doi
        ]
        GitHubManagee gitHubManager = new GitHubManager("ryabova.anna@gmail.com", "token_here", null)
        Map<String, Workflow> workflows = gitHubManager.processRepository(repositoryPath )
        for ( Entry<String, Workflow> e : workflows.entrySet() ) {
            //TODO: insert into database
            /*Resources: 
             repository: repositories.ID
             name:
             type: ENUM(tool, workflow, notebook)
             language: ENUM(CWL, WDL, NFL)
             primaryDescriptorPath: The absolute path to the primary descriptor file in the Git repository. 
             - For CWL, the primary descriptor is a .cwl file.
             - For WDL, the primary descriptor is a .wdl file.
             - Nextflow differs from these as the primary descriptor is a nextflow.config file.
             readMePath: An optional path to a resource-specific readme in the Git repository.
             topic: An optional short text description of the resource.
             info: JSONB Other structured information from dockstore.yml*/
            Workflow workflow = e.getValue()
            String lang = workflow.getDescriptorType().getShortName()
            def primaryDescriptorPath = ""
            Set<WorkflowVersion> versions = workflow.getWorkflowVersions()
            if(versions.isEmpty()) {
            }
            else {
                WorkflowVersion version = versions.iterator().next()
                //TODO: propertry primaryDescriptorPath is not correct: workflow path is version-specific, may be changed in different versions
                //Now correct path is stores only inside workflow version! The workflow itself does not contain full path
                //TODO: readMePath is also version-specific
                primaryDescriptorPath = version.getWorkflowPath()
            }
            //TODO: topic, info
            def wflID = database.resources << [repository: repoID, name: workflow.getWorkflowName(), type: "workflow", language: lang, primaryDescriptorPath: primaryDescriptorPath ]
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
                def versionID = database.versions.getBy( [repository: repoID, name: version.getName(), commit: version.getCommitID(),
                    type: version.getReferenceType().toString()])
                if(versionID == null)
                    versionID = database.versions << [repository: repoID, name: version.getName(), commit: version.getCommitID(),
                        dateModified: version.getLastModified(), type: version.getReferenceType().toString()]
                /* ResourceToVersion
                 resource: resources.ID
                 version: versions.ID
                 language: language version used by corresponding resource
                 valid: a version is valid if the descriptor file(s) have been successfully validated         
                 doi: DOI for this resource of this version of Git repository     
                 */
                //TODO: doi and language
                database.resource2version << [resource: wflID, version:versionID, valid: version.isValid() ]
            }
        }
    }
}
