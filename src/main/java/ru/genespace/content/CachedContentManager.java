package ru.genespace.content;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.imageio.ImageIO;

import com.developmentontheedge.be5.database.DbService;
import com.developmentontheedge.be5.database.QRec;

public class CachedContentManager implements ContentManager
{
    private Long repositoryId;
    private Long resourceId;
    private Long versionId;
    private DbService db;

    public CachedContentManager(DbService db, Long repositoryId, Long resourceId, Long versionId)
    {
        this.repositoryId = repositoryId;
        this.resourceId = resourceId;
        this.versionId = versionId;
        this.db = db;
    }


    public String getFileContentText(String fileName)
    {
        QRec dbRec = db.recordWithParams( "SELECT data, mimeType FROM attachments WHERE resource=? AND version=? AND fileName=?", resourceId, versionId, fileName );
        if( dbRec != null && !dbRec.isEmpty() )
        {
            if( dbRec.getString( "mimeType" ).equals( "text/plain" ) )
            {
                try
                {
                    InputStream is = dbRec.getBinaryStream( "data" );
                    return new String( is.readAllBytes(), StandardCharsets.UTF_8 );
                }
                catch (SQLException e)
                {
                }
                catch (IOException e)
                {
                }
            }
        }
        return null;
    }

    public BufferedImage getFileContentImage(String fileName)
    {
        QRec dbRec = db.recordWithParams( "SELECT data, mimeType FROM attachments WHERE resource=? AND version=? AND fileName=?", resourceId, versionId, fileName );
        if( dbRec != null && !dbRec.isEmpty() )
        {
            if( dbRec.getString( "mimeType" ).equals( "image/png" ) )
            {
                try
                {
                    InputStream is = dbRec.getBinaryStream( "data" );
                    return ImageIO.read( is );
                }
                catch (SQLException e)
                {
                }
                catch (IOException e)
                {
                }
            }
        }
        return null;
    }

    public void setFileContentText(String fileName, String fileText)
    {
        InputStream is = new ByteArrayInputStream( fileText.getBytes() );
        db.execute( conn -> {
            boolean oldAC = conn.getAutoCommit();
            conn.setAutoCommit( false );

            try (PreparedStatement ps = conn.prepareStatement( "INSERT INTO attachments (repository, resource, version, fileName, mimeType, data) VALUES (?,?,?,?,?,?)" ))
            {
                ps.setLong( 1, repositoryId );
                ps.setLong( 2, resourceId );
                ps.setLong( 3, versionId );
                ps.setString( 4, fileName );
                ps.setString( 5, "text/plain" );
                ps.setBinaryStream( 6, is );

                return ps.execute();
            }
            finally
            {
                conn.setAutoCommit( oldAC );
            }
        } );
    }

    public void setFileContentImage(String fileName, BufferedImage image)
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try
        {
            ImageIO.write( image, "gif", os );
        }
        catch (IOException e)
        {
        }
        InputStream is = new ByteArrayInputStream( os.toByteArray() );
        db.execute( conn -> {
            boolean oldAC = conn.getAutoCommit();
            conn.setAutoCommit( false );
            try (PreparedStatement ps = conn.prepareStatement( "INSERT INTO attachments (repository, resource, version, fileName, mimeType, data) VALUES (?,?,?,?,?,?)" ))
            {
                ps.setLong( 1, repositoryId );
                ps.setLong( 2, resourceId );
                ps.setLong( 3, versionId );
                ps.setString( 4, fileName );
                ps.setString( 5, "image/png" );
                ps.setBinaryStream( 6, is );

                return ps.execute();
            }
            finally
            {
                conn.setAutoCommit( oldAC );
            }
        } );
    }

}
