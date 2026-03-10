package ru.genespace.webserver;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import com.developmentontheedge.be5.database.DbService;
import com.developmentontheedge.be5.database.QRec;
import com.developmentontheedge.be5.database.Transactional;
import com.developmentontheedge.be5.server.servlet.support.BaseControllerSupport;
import com.developmentontheedge.be5.web.Request;
import com.developmentontheedge.be5.web.Response;

import biouml.model.Diagram;
import biouml.model.util.DiagramImageGenerator;
import biouml.plugins.wdl.diagram.WDLImporter;
import biouml.plugins.wdl.nextflow.NextFlowImporter;
import ru.biosoft.graphics.ImageGenerator;
import ru.biosoft.server.servlets.webservices.BiosoftWebResponse;
import ru.biosoft.server.servlets.webservices.JSONResponse;
import ru.biosoft.util.ApplicationUtils;
import ru.biosoft.util.TempFile;
import ru.biosoft.util.TempFiles;
import ru.biosoft.util.TextUtil2;
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
                else if( subServlet.equals( "image" ) )
                {
                    processImage( convertParams( arguments ), resp );
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
            String contentType = arguments.get( "content" ).toString();
            String content = null;
            //Get file content from git
            String filepath = arguments.get( "filepath" ).toString();
            String repositoryId = arguments.get( "repository" ).toString();
            String version = arguments.get( "version" ).toString();
            if(repositoryId == null || version == null)
            {
                log.log( Level.WARNING, "Can not load file content. Github repository name and reference (branch or tag) should be specified." );
                return;
            }
            GitHubManager gitHubManager = getGithubManager();
            content = gitHubManager.getFileContent( repositoryId, version, filepath );
            if( filepath.endsWith( "md" ) || "markdown".equals( contentType ) )
            {
                URL repoURL = gitHubManager.getRepositoryURL( repositoryId );
                //Dirty: make absolute links to files from github 
                //variant 1: https://github.com/genespace-workflows/snv-calling/blob/main/***RELATIVE PATH TO IMAGE IN ORIGINAL MARKDOWN***?raw=true
                //variant 2: https://raw.githubusercontent.com/genespace-workflows/snv-calling/main/***RELATIVE PATH TO IMAGE IN ORIGINAL MARKDOWN***
                String repoUrlStr = repoURL.toString() + "/blob/" + version + "/";
                content = MarkdownHelper.resolveRelativeImages( content, repoUrlStr, "?raw=true" );
            }
            if(content != null)
            {
                resp.setContentType( "text/html" );
                out.write( content.getBytes( "utf-8" ) );
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

    private GitHubManager getGithubManager()
    {
        String githubUser = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_user'" );
        String githubToken = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_token'" );
        GitHubManager gitHubManager = new GitHubManager( githubUser, githubToken );
        return gitHubManager;
    }

    private void processImage(Map<String, String> arguments, BiosoftWebResponse resp) throws Exception
    {
        OutputStream out = resp.getOutputStream();
        try
        {


            Long version = Long.parseLong( arguments.get( "version" ).toString() );
            Long resourceId = Long.parseLong( arguments.get( "resource" ).toString() );
            QRec info = db.recordWithParams(
                    "SELECT r2v.primaryDescriptorPath AS primaryDescriptorPath, res.language AS language, repo.url AS repository, ver.name as reference FROM resource2versions r2v "
                            + "JOIN resources res ON res.ID=r2v.resource JOIN repositories repo ON repo.ID=res.repository JOIN versions ver ON ver.ID=r2v.version"
                            + " WHERE r2v.resource=? AND r2v.version=? ",
                    resourceId, version );
            String primaryDescriptorPath = info.getString( "primaryDescriptorPath" );
            String shortType = info.getString( "language" );
            String repositoryId = info.getString( "repository" );
            String reference = info.getString( "reference" );
            if( reference == null )
                reference = "main";
            QRec imageRec = db.recordWithParams( "SELECT data FROM attachments WHERE resource=? AND version=? AND fileName=?", resourceId, version, primaryDescriptorPath );
            if( imageRec != null && !imageRec.isEmpty() )
            {
                InputStream imageStream = imageRec.getBinaryStream( "data" );
                BufferedImage imageObj = ImageIO.read( imageStream );
                if( imageObj != null )
                {
                    resp.setContentType( "image/png" );
                    ImageGenerator.encodeImage( (BufferedImage) imageObj, "PNG", out );
                    return;
                }
            }
            
            GitHubManager gitHubManager = getGithubManager();
            String workflowContent = gitHubManager.getWorkflowContent( repositoryId, reference, primaryDescriptorPath, shortType );
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
                resp.setContentType( "image/png" );
                BufferedImage image = DiagramImageGenerator.generateDiagramImage( diagram );
                ImageGenerator.encodeImage( image, "PNG", out );
                
                TempFile diagramImageFile = TempFiles.file( "diagramImage" + reference );
                ImageGenerator.encodeImage( image, "PNG", new FileOutputStream( diagramImageFile ) );
                FileInputStream inputStream = new FileInputStream( diagramImageFile );
                addImageToDB( resourceId, version, primaryDescriptorPath, inputStream );

                //                db.insertRaw( "INSERT INTO attachments (resource, version, fileName, data) VALUES (?,?,?, ?)", resourceId, version,
                //                        primaryDescriptorPath, inputStream );



                //                statement.setBlob(1, inputStream);
                //                
                //                db.insertRaw( "INSERT INTO attachments (resource, version, fileName, data) VALUES (?,?,?, ?)", resourceId, version, primaryDescriptorPath, image );
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

    @Transactional
    private void addImageToDB(Long resourceId, Long version, String primaryDescriptorPath, FileInputStream inputStream)
    {
        db.execute( conn -> {
            boolean oldAC = conn.getAutoCommit();
            conn.setAutoCommit( false );
            try (PreparedStatement ps = conn.prepareStatement( "INSERT INTO attachments (resource, version, fileName, data) VALUES (?,?,?, ?)" ))
            {
                ps.setLong( 1, resourceId );
                ps.setLong( 2, version );
                ps.setString( 3, primaryDescriptorPath );
                ps.setBinaryStream( 4, inputStream );

                return ps.execute();
            }
            finally
            {
                conn.setAutoCommit( oldAC );
            }
        } );
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
