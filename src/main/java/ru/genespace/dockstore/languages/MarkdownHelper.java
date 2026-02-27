package ru.genespace.dockstore.languages;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class MarkdownHelper
{
    /**
     * Convert relative paths from markdown to absolute URL
     * 
     * @param markdown input markdlown
     * @param baseUrl base server url (for example, "https://github.com/genespace-workflows/snv-calling")
     * @return processed Markdown
     */
    public static String resolveRelativeImages(String markdown, String baseUrl, String additionalURLParams)
    {
        if( markdown == null || baseUrl == null )
            return markdown;
        final String urlParams = additionalURLParams == null ? "" : additionalURLParams;

        // ![alt](path) or ![alt](path "title")
        Pattern pattern = Pattern.compile( "(!\\[[^\\]]*\\]\\()\\s*([^)\\s]+)(\\s+\"[^\"]*\")?\\s*(\\))" );

        return pattern.matcher( markdown ).replaceAll( match -> {
            String prefix = match.group( 1 );  // ![alt](
            String imagePath = match.group( 2 ); // image path
            String title = match.group( 3 ) != null ? match.group( 3 ) : ""; // optional title
            String suffix = match.group( 4 );  // )

            // Skip absolute URL and data: URI
            if( imagePath.startsWith( "http://" ) || imagePath.startsWith( "https://" ) || imagePath.startsWith( "data:" ) )
            {
                return match.group( 0 );
            }

            try
            {
                String resolved = resolvePath( baseUrl, imagePath );
                return prefix + resolved + urlParams + title + suffix;
            }
            catch (Exception e)
            {
                return match.group( 0 );
            }
        } );
    }

    private static String resolvePath(String baseUrl, String relativePath) throws URISyntaxException, MalformedURLException
    {

        URI base = new URI( baseUrl );
        URI resolved = base.resolve( relativePath );
        return resolved.toString();
    }
}
