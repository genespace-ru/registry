package operations

import com.developmentontheedge.be5.databasemodel.util.DpsUtils
import com.developmentontheedge.be5.operation.OperationResult
import com.developmentontheedge.be5.server.operations.support.GOperationSupport
import com.developmentontheedge.beans.DynamicPropertySet as DPS
import com.developmentontheedge.beans.DynamicPropertySetSupport

public class RemoveRepository extends GOperationSupport {
    Map<String, Object> presets

    //    @Inject
    //    private RepositoryManager repo;

    @Override
    public void invoke(Object parameters) throws Exception {
        DPS params = parameters as DPS ?: new DynamicPropertySetSupport()

        //String repoPath = repo.getRepositoryPath();
        for ( int i=0 ; i < context.records.length ; ++i ) {
            def repo = database.getEntity( getInfo().getEntity().name ).get( context.records[i] )
            //TODO: delete repository
            database.repositories.removeBy([ID: repo.$ID])
        }
        setResult(OperationResult.finished())
    }
}
