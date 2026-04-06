package ru.genespace.dockstore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ORCIDHelper
{
    private static final String ORCID_BASE_URL = "https://api.orcid.org/v3.0/";
    private static final String ORCID_BASE_PUB_URL = "https://pub.orcid.org/v3.0/";
    private final HttpClient httpClient;
    private final Gson gson;
    private final String accessToken;

    public ORCIDHelper(String accessToken)
    {
        this.accessToken = accessToken;
        this.httpClient = HttpClient.newBuilder().connectTimeout( java.time.Duration.ofSeconds( 10 ) ).build();
        this.gson = new Gson();
    }

    public void fillOrcidInfo(OrcidAuthor author)
    {
        String orcidId = author.getOrcid();

        try
        {
            //            String personJson = fetchEndpoint( orcidId, "person" );
            //            String emailJson = fetchEndpoint( orcidId, "email" );
            //            String employmentsJson = fetchEndpoint( orcidId, "employments" );
            //
            //            JsonObject personNode = gson.fromJson( personJson, JsonObject.class );
            //            JsonObject emailNode = gson.fromJson( emailJson, JsonObject.class );
            //            JsonObject employmentsNode = gson.fromJson( employmentsJson, JsonObject.class );
            //            author.setName( extractName( personNode ) );
            //            author.setEmail( extractEmail( emailNode ) );
            //            author.setAffiliation( extractAffiliation( employmentsNode ) );
            //            author.setRole( extractRole( employmentsNode ) );

            String fullInfo = fetchEndpoint( orcidId, "" );
            JsonObject info = gson.fromJson( fullInfo, JsonObject.class );
            author.setName( extractName( info.getAsJsonObject( "person" ) ) );
            author.setEmail( extractEmail( info ) );

            JsonArray affiliationArray = info.getAsJsonObject( "activities-summary" ).getAsJsonObject( "employments" ).getAsJsonArray( "affiliation-group" );
            for(JsonElement ae: affiliationArray)
            {
                JsonObject affiliationObj = ae.getAsJsonObject();
                if( affiliationObj == null || !affiliationObj.has( "summaries" ) )
                    continue;
                JsonElement summaryElement = affiliationObj.get( "summaries" );
                if( summaryElement == null || summaryElement.isJsonNull() || !summaryElement.isJsonArray() )
                    continue;
                JsonArray summaries = summaryElement.getAsJsonArray();
                if( summaries.isEmpty() )
                    continue;
                JsonElement firstSummary = summaries.get( 0 );
                if( firstSummary == null || firstSummary.isJsonNull() || !firstSummary.isJsonObject() )
                    continue;
                JsonObject summaryObj = firstSummary.getAsJsonObject().getAsJsonObject( "employment-summary" );
                if( summaryObj == null || summaryObj.isJsonNull() )
                    continue;
                if( isPublicEmployment( summaryObj ) )
                {
                    author.setAffiliation( extractAffiliation( summaryObj ) );
                    author.setRole( extractRole( summaryObj ) );
                }
            }
                
        }
        catch (IOException | InterruptedException e)
        {
            author.setName( null );
        }

    }

    private String fetchEndpoint(String orcidId, String endpoint) throws IOException, InterruptedException
    {
        //String url = ORCID_BASE_URL + orcidId + "/" + endpoint;
        String url = ORCID_BASE_PUB_URL + orcidId;
        HttpRequest request = HttpRequest.newBuilder().uri( URI.create( url ) ).header( "Authorization", "Bearer " + accessToken ).header( "Accept", "application/json" ).GET()
                .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() != 200 )
        {
            throw new IOException( "Failed to fetch " + endpoint + ": " + response.statusCode() );
        }
        return response.body();
    }

    private String extractName(JsonObject personNode)
    {
        if( personNode == null || !personNode.has( "name" ) )
        {
            return "N/A";
        }
        JsonElement nameElement = personNode.get( "name" );
        if( nameElement == null || nameElement.isJsonNull() )
        {
            return "N/A";
        }
        JsonObject nameNode = nameElement.getAsJsonObject();
        String given = "";
        String family = "";

        if( nameNode.has( "given-names" ) )
        {
            JsonElement givenElement = nameNode.get( "given-names" );
            if( givenElement != null && !givenElement.isJsonNull() )
            {
                JsonObject givenNames = givenElement.getAsJsonObject();
                if( givenNames.has( "value" ) )
                {
                    JsonElement value = givenNames.get( "value" );
                    if( value != null && value.isJsonPrimitive() )
                    {
                        given = value.getAsString();
                    }
                }
            }
        }
        if( nameNode.has( "family-name" ) )
        {
            JsonElement familyElement = nameNode.get( "family-name" );
            if( familyElement != null && !familyElement.isJsonNull() )
            {
                JsonObject familyName = familyElement.getAsJsonObject();
                if( familyName.has( "value" ) )
                {
                    JsonElement value = familyName.get( "value" );
                    if( value != null && value.isJsonPrimitive() )
                    {
                        family = value.getAsString();
                    }
                }
            }
        }
        return (given + " " + family).trim();
    }

    private String extractEmail(JsonObject emailNode)
    {
        if( emailNode == null || !emailNode.has( "email" ) )
        {
            return "N/A";
        }
        JsonElement emailElement = emailNode.get( "email" );
        if( emailElement == null || emailElement.isJsonNull() || !emailElement.isJsonArray() )
        {
            return "N/A";
        }
        JsonArray emails = emailElement.getAsJsonArray();
        if( emails.size() > 0 )
        {
            JsonElement firstElement = emails.get( 0 );
            if( firstElement != null && !firstElement.isJsonNull() )
            {
                JsonObject firstEmail = firstElement.getAsJsonObject();
                if( firstEmail.has( "email" ) )
                {
                    JsonElement value = firstEmail.get( "email" );
                    if( value != null && value.isJsonPrimitive() )
                    {
                        return value.getAsString();
                    }
                }
            }
        }
        return "N/A";
    }

    private String extractAffiliation(JsonObject employmentsNode)
    {

        if( employmentsNode.has( "organization" ) )
        {
            JsonElement orgElement = employmentsNode.get( "organization" );
            if( orgElement != null && !orgElement.isJsonNull() )
            {
                JsonObject org = orgElement.getAsJsonObject();
                if( org.has( "name" ) )
                {
                    JsonElement nameElement = org.get( "name" );
                    if( nameElement != null && !nameElement.isJsonNull() )
                    {
                        if( nameElement.isJsonPrimitive() )
                        {
                            return nameElement.getAsString();
                        }
                        else
                        {
                            JsonObject name = nameElement.getAsJsonObject();
                            if( name.has( "value" ) )
                            {
                                JsonElement value = name.get( "value" );
                                if( value != null && value.isJsonPrimitive() )
                                {
                                    return value.getAsString();
                                }
                            }
                        }
                    }
                }
            }
        }
        return "N/A";
    }

    private String extractRole(JsonObject employmentsNode)
    {
        if( employmentsNode.has( "role-title" ) )
        {
            JsonElement value = employmentsNode.get( "role-title" );
            if( value != null && value.isJsonPrimitive() )
            {
                return value.getAsString();
            }
        }
        return "N/A";
    }

    private boolean isPublicEmployment(JsonObject employmentsNode)
    {
        return employmentsNode.has( "visibility" ) && employmentsNode.get( "visibility" ).getAsString().equals( "public" );
    }

}
