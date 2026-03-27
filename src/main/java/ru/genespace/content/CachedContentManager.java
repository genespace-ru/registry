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
    private Long ownerId;
    private String ownerType;
    private DbService db;

    public CachedContentManager(DbService db, Long ownerId, String ownerType)
    {
        this.ownerId = ownerId;
        this.ownerType = ownerType;
        this.db = db;
    }

    @Override
    public Object getFileContent(String fileName)
    {
        QRec dbRec = db.recordWithParams( "SELECT data, mimeType FROM attachments WHERE ownerId=? AND ownerType=? AND fileName=? AND isFetched='yes'", ownerId, ownerType,
                fileName );
        if( dbRec != null && !dbRec.isEmpty() )
        {
            try
            {
                InputStream is = dbRec.getBinaryStream( "data" );
                if( is == null )
                    return null;
                if( dbRec.getString( "mimeType" ).equals( "text/plain" ) )
                {
                    return new String( is.readAllBytes(), StandardCharsets.UTF_8 );
                }
                else if( dbRec.getString( "mimeType" ).equals( "image/png" ) )
                {
                    return ImageIO.read( is );
                }
                else
                {
                    return is;
                }
            }
            catch (SQLException | IOException e)
            {
            }
        }
        return null;
    }

    @Override
    public void setFileContent(String fileName, Object content)
    {
        InputStream is = getInputStream( content );
        InputStream is2 = getInputStream( content );
        String mimeType = getContentType( content );
        db.execute( conn -> {
            boolean oldAC = conn.getAutoCommit();
            conn.setAutoCommit( false );

            try (PreparedStatement ps = conn.prepareStatement( "INSERT INTO attachments (ownerId, ownerType, fileName, mimeType, data, isFetched) VALUES (?,?,?,?,?,'yes')"
                    + " ON CONFLICT (ownerId, ownerType, fileName) DO UPDATE SET mimeType=?, data=?, isFetched='yes'" ))
            {
                ps.setLong( 1, ownerId );
                ps.setString( 2, ownerType );
                ps.setString( 3, fileName );
                ps.setString( 4, mimeType );
                ps.setBinaryStream( 5, is );
                ps.setString( 6, mimeType );
                ps.setBinaryStream( 7, is2 );

                return ps.execute();
            }
            finally
            {
                conn.setAutoCommit( oldAC );
            }
        } );

    }

    private InputStream getInputStream(Object content)
    {
        if( content instanceof BufferedImage )
        {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try
            {
                ImageIO.write( (BufferedImage) content, "png", os );
            }
            catch (IOException e)
            {
            }
            return new ByteArrayInputStream( os.toByteArray() );
        }
        else if( content instanceof String )
        {
            return new ByteArrayInputStream( ((String) content).getBytes() );
        }
        else
        {
            return new ByteArrayInputStream( content.toString().getBytes() );
        }
    }

    private String getContentType(Object content)
    {
        if( content instanceof BufferedImage )
        {
            return "image/png";
        }
        else if( content instanceof String )
        {
            return "text/plain";
        }
        else
        {
            return "application/octet-stream";
        }
    }

    //    public String getFileContentText(String fileName)
    //    {
    //        QRec dbRec = db.recordWithParams( "SELECT data, mimeType FROM attachments WHERE ownerId=? AND ownerType=? AND fileName=? AND isFetched='yes'", ownerId, ownerType,
    //                fileName );
    //        if( dbRec != null && !dbRec.isEmpty() )
    //        {
    //            if( dbRec.getString( "mimeType" ).equals( "text/plain" ) )
    //            {
    //                try
    //                {
    //                    InputStream is = dbRec.getBinaryStream( "data" );
    //                    if( is != null )
    //                        return new String( is.readAllBytes(), StandardCharsets.UTF_8 );
    //                }
    //                catch (SQLException e)
    //                {
    //                }
    //                catch (IOException e)
    //                {
    //                }
    //            }
    //        }
    //        return null;
    //    }
    //
    //    public BufferedImage getFileContentImage(String fileName)
    //    {
    //        QRec dbRec = db.recordWithParams( "SELECT data, mimeType FROM attachments WHERE ownerId=? AND ownerType=? AND fileName=? AND isFetched='yes'", ownerId, ownerType,
    //                fileName );
    //        if( dbRec != null && !dbRec.isEmpty() )
    //        {
    //            if( dbRec.getString( "mimeType" ).equals( "image/png" ) )
    //            {
    //                try
    //                {
    //                    InputStream is = dbRec.getBinaryStream( "data" );
    //                    if( is != null )
    //                    return ImageIO.read( is );
    //                }
    //                catch (SQLException e)
    //                {
    //                }
    //                catch (IOException e)
    //                {
    //                }
    //            }
    //        }
    //        return null;
    //    }
    //
    //    public void setFileContentText(String fileName, String fileText)
    //    {
    //        InputStream is = new ByteArrayInputStream( fileText.getBytes() );
    //        InputStream is2 = new ByteArrayInputStream( fileText.getBytes() );
    //        db.execute( conn -> {
    //            boolean oldAC = conn.getAutoCommit();
    //            conn.setAutoCommit( false );
    //
    //            try (PreparedStatement ps = conn.prepareStatement( "INSERT INTO attachments (ownerId, ownerType, fileName, mimeType, data, isFetched) VALUES (?,?,?,?,?,'yes')"
    //                    + " ON CONFLICT (ownerId, ownerType, fileName) DO UPDATE SET mimeType=?, data=?, isFetched='yes'" ))
    //            {
    //                ps.setLong( 1, ownerId );
    //                ps.setString( 2, ownerType );
    //                ps.setString( 3, fileName );
    //                ps.setString( 4, "text/plain" );
    //                ps.setBinaryStream( 5, is );
    //                ps.setString( 6, "text/plain" );
    //                ps.setBinaryStream( 7, is2 );
    //
    //                return ps.execute();
    //            }
    //            finally
    //            {
    //                conn.setAutoCommit( oldAC );
    //            }
    //        } );
    //    }
    //
    //    public void setFileContentImage(String fileName, BufferedImage image)
    //    {
    //        ByteArrayOutputStream os = new ByteArrayOutputStream();
    //        try
    //        {
    //            ImageIO.write( image, "gif", os );
    //        }
    //        catch (IOException e)
    //        {
    //        }
    //        InputStream is = new ByteArrayInputStream( os.toByteArray() );
    //        db.execute( conn -> {
    //            boolean oldAC = conn.getAutoCommit();
    //            conn.setAutoCommit( false );
    //            try (PreparedStatement ps = conn.prepareStatement( "INSERT INTO attachments (ownerId, ownerType, fileName, mimeType, data, isFetched) VALUES (?,?,?,?,?,?)" ))
    //            {
    //                ps.setLong( 1, ownerId );
    //                ps.setString( 2, ownerType );
    //                ps.setString( 3, fileName );
    //                ps.setString( 4, "image/png" );
    //                ps.setBinaryStream( 5, is );
    //                ps.setString( 6, "yes" );
    //
    //                return ps.execute();
    //            }
    //            finally
    //            {
    //                conn.setAutoCommit( oldAC );
    //            }
    //        } );
    //    }


}
