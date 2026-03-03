package ru.genespace.dockstore;

//This describes an ORCID-author of a version 
public class OrcidAuthor {

    //Implementation specific ID for the author in this web service
    private long id;

    //ORCID iD of the author
    private String orcid;

    public OrcidAuthor() {}

    public OrcidAuthor(String orcid) {
        this.orcid = orcid;
    }

    public long getId() {
        return this.id;
    }

    public String getOrcid() {
        return orcid;
    }
}
