package ru.genespace.dockstore;

import java.util.Objects;

//This describes an ORCID-author of a version 
public class OrcidAuthor extends Author
{
    //ORCID iD of the author
    private String orcid;

    public OrcidAuthor() {}

    public OrcidAuthor(String orcid) {
        this.orcid = orcid;
    }

    public String getOrcid() {
        return orcid;
    }

    @Override
    public boolean equals(Object o)
    {
        if( this == o )
        {
            return true;
        }
        if( !(o instanceof OrcidAuthor that) )
        {
            return false;
        }

        return Objects.equals( orcid, that.orcid );
    }
}
