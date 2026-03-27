package ru.genespace.webserver;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import com.developmentontheedge.be5.database.DbService;
import com.developmentontheedge.be5.database.QRec;
import com.developmentontheedge.be5.server.servlet.support.BaseControllerSupport;
import com.developmentontheedge.be5.web.Request;
import com.developmentontheedge.be5.web.Response;

import biouml.model.Diagram;
import biouml.model.util.DiagramImageGenerator;
import biouml.plugins.wdl.diagram.WDLImporter;
import biouml.plugins.wdl.diagram.WDLLayouter;
import biouml.plugins.wdl.nextflow.NextFlowImporter;
import ru.biosoft.graphics.ImageGenerator;
import ru.biosoft.server.servlets.webservices.BiosoftWebResponse;
import ru.biosoft.server.servlets.webservices.JSONResponse;
import ru.biosoft.util.ApplicationUtils;
import ru.biosoft.util.TempFile;
import ru.biosoft.util.TempFiles;
import ru.biosoft.util.TextUtil2;
import ru.genespace.content.CachedContentManager;
import ru.genespace.dockstore.languages.MarkdownHelper;
import ru.genespace.github.GitHubManager;

public class WebserverController extends BaseControllerSupport
{
    private static final Logger log = Logger.getLogger( WebserverController.class.getName() );

    @Inject
    private DbService db;

    @Override
    public void generate(Request request, Response response)
    {
        log.info( "WebServer request: " + request.getRawRequest().getMethod() + " " + request.getRequestUri() );
        try
        {
            String method = request.getRawRequest().getMethod();
            if( !method.equals( "GET" ) && !method.equals( "HEAD" ) && !method.equals( "POST" ) )
            {
                throw new IllegalArgumentException( method + " method not supported" );
            }

            String target = request.getRequestUri();
            Map<String, String[]> uriParameters = request.getParameters();

            final Map<String, Object> arguments = new HashMap<>();
            response.setStatus( HttpServletResponse.SC_OK );

            final OutputStream out = response.getOutputStream();
            try
            {
                for ( String uriParameter : uriParameters.keySet() )
                {

                    arguments.put( TextUtil2.decodeURL( uriParameter ), uriParameters.get( uriParameter ) );
                }

                final String localAddress = target.substring( 1 );
                int subServletIndex = localAddress.lastIndexOf( "web/" );
                String subServlet = subServletIndex == -1 ? localAddress : localAddress.substring( subServletIndex + 4 );

                BiosoftWebResponse resp = new BiosoftWebResponse( response.getRawResponse(), out );
                response.setStatus( HttpServletResponse.SC_OK );

                if( subServlet.equals( "content" ) )
                {
                    processContent( convertParams( arguments ), resp );
                }
                else if( subServlet.equals( "dag" ) )
                {
                    processDiagramImage( convertParams( arguments ), resp );
                }

            }
            catch (Throwable t)
            {
                new JSONResponse( out ).error( t );
            }

        }
        catch (IOException e)
        {
            log.severe( "Error processing request " + request.getRawRequest().getMethod() + " " + request.getRequestUri() + " Error: " + e.getMessage() );
        }
    }


    private void processContent(Map<String, String> arguments, BiosoftWebResponse resp) throws Exception
    {
        OutputStream out = resp.getOutputStream();
        resp.setContentType( "text/html" );
        try
        {
            String contentType = arguments.get( "content" );
            String content = null;
            //Get file content from git or from cache

            String filepath = arguments.get( "filepath" );
            String owner = arguments.get("ownerId");
            String ownertype = arguments.get("ownerType");

            if( owner == null )
            {
                String[] possibleOwners = new String[] { "resource2versions", "resources", "versions", "repositories" };
                for ( String po : possibleOwners )
                {
                    if( arguments.containsKey( po ) )
                    {
                        owner = arguments.get( po );
                        ownertype = po;
                        break;
                    }
                }
            }

            if( owner == null || ownertype == null || filepath == null )
            {
                log.log( Level.WARNING,
                        "Can not load file content. File name, owner type and id of either repository, resource, version or resource2version should be specified." );
                return;
            }
            
            Long ownerId = Long.parseLong( owner );
            
            CachedContentManager cache = new CachedContentManager( db,ownerId , ownertype );
            Object contentObj = cache.getFileContent( filepath );
            if( contentObj == null )
            {
                QRec info = getGithubParams( ownerId, ownertype );
                String repositoryName = info.getString( "repository" );
                String reference = info.getString( "reference" );

                GitHubManager gitHubManager = getGithubManager();
                content = gitHubManager.getFileContent( repositoryName, reference, filepath, null );
                if( filepath.endsWith( "md" ) || "markdown".equals( contentType ) )
                {
                    URL repoURL = gitHubManager.getRepositoryURL( repositoryName );
                    //Dirty: make absolute links to files from github 
                    //variant 1: https://github.com/genespace-workflows/snv-calling/blob/main/***RELATIVE PATH TO IMAGE IN ORIGINAL MARKDOWN***?raw=true
                    //variant 2: https://raw.githubusercontent.com/genespace-workflows/snv-calling/main/***RELATIVE PATH TO IMAGE IN ORIGINAL MARKDOWN***
                    String repoUrlStr = repoURL.toString() + "/blob/" + reference + "/";
                    content = MarkdownHelper.resolveRelativeImages( content, repoUrlStr, "?raw=true" );
                }
                if( content != null )
                    cache.setFileContent( filepath, content );
            }
            else if( contentObj instanceof String )
            {
                content = (String) contentObj;
            }
            if(content != null)
            {
                resp.setContentType( "text/html" );
                out.write( content.getBytes( "utf-8" ) );
            }
            else
            {
                //Error with file, send empty
                resp.setContentType( "text/html" );
                String noFileContent = "Can not read " + filepath;
                out.write( noFileContent.getBytes( "utf-8" ) );
            }

        }
        catch (Exception e)
        {
            log.log( Level.SEVERE, "Can not load file content. " + e.getMessage() );
        }
        finally
        {
            out.close();
        }
    }

    private QRec getGithubParams(Long ownerId, String ownerType)
    {
        switch (ownerType)
        {
        case "repositories":
            return db.recordWithParams( "SELECT repo.url AS repository, ver.name as reference FROM versions ver JOIN repositories repo ON repo.ID=ver.repository WHERE repo.ID=? ",
                    ownerId );
        case "versions":
            return db.recordWithParams( "SELECT repo.url AS repository, ver.name as reference FROM versions ver JOIN repositories repo ON repo.ID=ver.repository WHERE ver.ID=? ",
                    ownerId );
        case "resources":
            return db.recordWithParams(
                    "SELECT repo.url AS repository, ver.name as reference FROM resources res JOIN repositories repo ON repo.ID=res.repository JOIN resource2versions ON res.ID=r2v.resource JOIN versions ver ON r2v.version=ver.ID WHERE res.ID=? ",
                    ownerId );
        case "resource2versions":
            return db.recordWithParams(
                    "SELECT repo.url AS repository, ver.name as reference FROM resource2versions r2v JOIN versions ver ON r2v.version=ver.ID JOIN repositories repo ON ver.repository=repo.ID WHERE r2v.ID=? ",
                    ownerId );
        default:
            return null;
        }
    }

    private GitHubManager getGithubManager()
    {
        String githubUser = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_user'" );
        String githubToken = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_token'" );
        GitHubManager gitHubManager = new GitHubManager( githubUser, githubToken );
        return gitHubManager;
    }

    private void processDiagramImage(Map<String, String> arguments, BiosoftWebResponse resp) throws Exception
    {
        OutputStream out = resp.getOutputStream();
        try
        {
            Long versionId = Long.parseLong( arguments.get( "version" ) );
            Long resourceId = Long.parseLong( arguments.get( "resource" ) );
            QRec info = db.recordWithParams(
                    "SELECT r2v.primaryDescriptorPath AS primaryDescriptorPath, res.language AS language, r2v.ID as ownerId FROM resource2versions r2v "
                            + "JOIN resources res ON res.ID=r2v.resource JOIN repositories repo ON repo.ID=res.repository JOIN versions ver ON ver.ID=r2v.version"
                            + " WHERE r2v.resource=? AND r2v.version=? ",
                    resourceId, versionId );
            String primaryDescriptorPath = info.getString( "primaryDescriptorPath" );
            String shortType = info.getString( "language" );
            Long ownerId = info.getLong( "ownerId" );

            CachedContentManager cache = new CachedContentManager( db, ownerId, "resource2versions" );
            String imageFilePath = "DAG";
            Object imageObj = cache.getFileContent( imageFilePath );
            if( imageObj != null && imageObj instanceof BufferedImage )
            {
                resp.setContentType( "image/png" );
                ImageGenerator.encodeImage( (BufferedImage) imageObj, "PNG", out );
                return;
            }
            
            QRec info2 = getGithubParams( ownerId, "resource2versions" );
            String repositoryName = info2.getString( "repository" );
            String reference = info2.getString( "reference" );
            if( reference == null )
                reference = "main";
            GitHubManager gitHubManager = getGithubManager();

            String workflowContent = gitHubManager.getWorkflowContent( repositoryName, reference, primaryDescriptorPath, shortType, cache );
            if( workflowContent == null || workflowContent.isEmpty() )
            {
                return;
            }
            TempFile file = TempFiles.file( reference );
            ApplicationUtils.writeString( file, workflowContent );
            Diagram diagram = null;

            switch (shortType)
            {
            case "WDL":
                WDLImporter importerWDL = new WDLImporter();
                diagram = importerWDL.generateDiagram( file, reference, null );
                break;
            case "CWL":

            case "NFL":
                NextFlowImporter importerNFL = new NextFlowImporter();
                diagram = importerNFL.importNextflow( workflowContent );
                break;
            }
            if( diagram != null )
            {
                new WDLLayouter().layout( diagram );
                resp.setContentType( "image/png" );
                BufferedImage image = DiagramImageGenerator.generateDiagramImage( diagram );
                ImageGenerator.encodeImage( image, "PNG", out );
                
                cache.setFileContent( imageFilePath, image );
            }

        }
        catch (Exception e)
        {
            log.log( Level.SEVERE, "Can not load file content. " + e.getMessage() );
        }
        finally
        {
            out.close();
        }
    }

    protected Map<String, String> convertParams(Map<?, ?> params)
    {
        Map<String, String> result = new HashMap<>();
        for ( Entry<?, ?> entry : params.entrySet() )
        {
            String value = null;
            try
            {
                value = Array.get( entry.getValue(), 0 ).toString();
            }
            catch (Exception e)
            {
            }
            result.put( entry.getKey().toString(), value );
        }
        return result;
    }
}
