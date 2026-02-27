package ru.genespace.webserver;

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
import com.developmentontheedge.be5.server.servlet.support.BaseControllerSupport;
import com.developmentontheedge.be5.web.Request;
import com.developmentontheedge.be5.web.Response;

import ru.biosoft.server.servlets.webservices.BiosoftWebResponse;
import ru.biosoft.server.servlets.webservices.JSONResponse;
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
            String githubUser = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_user'" );
            String githubToken = db.getString( "SELECT setting_value FROM systemsettings WHERE section_name='registry' AND setting_name='github_token'" );
            GitHubManager gitHubManager = new GitHubManager( githubUser, githubToken );
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
