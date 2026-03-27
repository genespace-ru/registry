package operations

import com.developmentontheedge.be5.database.QRec
import com.developmentontheedge.be5.databasemodel.util.DpsUtils
import com.developmentontheedge.be5.operation.OperationResult
import com.developmentontheedge.be5.server.operations.support.GOperationSupport
import com.developmentontheedge.beans.DynamicPropertySet as DPS
import com.developmentontheedge.beans.DynamicPropertySetSupport

public class RemoveRepository extends GOperationSupport {
    Map<String, Object> presets

    @Override
    public void invoke(Object parameters) throws Exception {
        DPS params = parameters as DPS ?: new DynamicPropertySetSupport()

        for ( int i=0 ; i < context.records.length ; ++i ) {
            def repo = database.getEntity( getInfo().getEntity().name ).get( context.records[i] )
            def reID = repo.$ID
            database.repositories.removeBy([ID: repo.$ID])

            def versions = db.list("SELECT ID FROM versions WHERE repository=${repo.$ID}" )
            for(def ver: versions) {
                def res2versions = db.list("SELECT ID FROM resource2versions WHERE version=${ver.$ID}" )
                removeAttachments(res2versions, "resource2versions");
                database.resource2versions.removeBy([version:ver.$ID])
            }
            removeAttachments(versions, "versions")

            def resources = db.list("SELECT ID FROM resources WHERE repository=${repo.$ID}" )
            for(def res: resources) {
                database.resource2docker.removeBy([resource:res.$ID])
            }
            removeAttachments(resources, "resources")


            database.resources.removeBy([repository: repo.$ID])
            database.versions.removeBy([repository: repo.$ID])
            database.attachments.removeBy([ownerID: repo.$ID, ownerType: "repositories"])
        }
        setResult(OperationResult.finished())
    }

    private void removeAttachments(List<QRec> ids, String type) {
        for(def id: ids)
            database.attachments.removeBy([ownerID: id.$ID, ownerType: type])
    }
}
