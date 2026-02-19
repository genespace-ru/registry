package github;

import java.util.Map;
import java.util.Map.Entry;

import ru.genespace.dockstore.Workflow;
import ru.genespace.dockstore.WorkflowVersion;
import ru.genespace.github.GitHubManager;

public class TestGitHubManager
{
    public static void main(String... args) throws Exception
    {
        GitHubManager manager = new GitHubManager();
        Map<String, Workflow> workflows = manager.processRepository( "dockstore/bcc2020-training" );
        for ( Entry<String, Workflow> e : workflows.entrySet() )
        {
            System.out.println( e.getKey() + " " + toStringWorkflow( e.getValue() ) );
        }
    }

    private static String toStringWorkflow(Workflow workflow)
    {
        StringBuilder sb = new StringBuilder( "\nWorkflow: " );
        sb.append( workflow.getWorkflowName() );
        sb.append( "\nType: " );
        sb.append( workflow.getDescriptorType().toString() );
        sb.append( "\nEntryPath: " );
        sb.append( workflow.getEntryPath() );
        sb.append( "\nDefaultWorkflowPath: " );
        sb.append( workflow.getDefaultWorkflowPath() );
        sb.append( "\nVersions:\n" );
        for ( WorkflowVersion v : workflow.getWorkflowVersions() )
        {
            sb.append( v.getName() + "  " + v.getWorkflowPath() + " " + v.toString() + "\n" );
        }
        return sb.toString();
    }
}
