package ru.genespace.dockstore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

import ru.genespace.dockstore.SourceFile.SourceFileMetadata;


public class WorkflowVersion implements Comparable<WorkflowVersion>
{
    //Implementation specific, can be a quay.io or docker hub tag name
    private String name;
    //git commit/tag/branch
    private String reference;
    private String workflowPath;
    //Remote: Last time version on GitHub repo was changed. Hosted: time version created.
    private Date lastModified;
    //Whether or not the version was added using the legacy refresh process.
    private boolean isLegacyVersion = true;
    //Whether or not the version has been refreshed since its last edit on Dockstore.
    private boolean synced = false;
    //User-specified notebook kernel image reference")
    private String kernelImagePath;
    //This is the commit id for the source control that the files belong to
    private String commitID;

    //Cached files for each version. Includes Dockerfile and Descriptor files
    private final SortedSet<SourceFile> sourceFiles;
    //The user-specified files for the version.
    private List<String> userFiles = new ArrayList<>();

    //Implementation specific, whether this tag has valid files from source code repo
    private boolean valid;
    //True if user has altered the tag
    private boolean dirtyBit = false;
    private ReferenceType referenceType = ReferenceType.UNSET;
    private boolean frozen = false;
    //A custom readme for the version, if applicable.
    private String readMePath;

    public WorkflowVersion()
    {
        sourceFiles = new TreeSet<>();
    }

    public int hashCode()
    {
        return Objects.hash( this.getName(), this.getReference() );
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getReference()
    {
        return reference;
    }

    public void setReference(String reference)
    {
        this.reference = reference;
    }

    public SortedSet<SourceFile> getSourceFiles()
    {
        return sourceFiles;
    }

    public void addSourceFile(SourceFile file)
    {
        sourceFiles.add( file );
    }

    public WorkflowVersion createEmptyVersion()
    {
        return new WorkflowVersion();
    }

    public Date getDate()
    {
        return this.getLastModified();
    }

    public String getWorkingDirectory()
    {
        if( workflowPath != null && !workflowPath.isEmpty() )
        {
            return FilenameUtils.getPathNoEndSeparator( workflowPath );
        }
        return "";
    }

    public boolean isSynced()
    {
        return synced;
    }

    public void setSynced(boolean synced)
    {
        this.synced = synced;
    }

    public String getWorkflowPath()
    {
        return workflowPath;
    }

    public void setWorkflowPath(String workflowPath)
    {
        this.workflowPath = workflowPath;
    }

    public String toString()
    {
        return MoreObjects.toStringHelper( this ).add( "name", this.getName() ).add( "reference", this.getReference() ).toString();
    }

    public Date getLastModified()
    {
        return lastModified;
    }

    public void setLastModified(Date lastModified)
    {
        this.lastModified = lastModified;
    }

    public boolean isLegacyVersion()
    {
        return isLegacyVersion;
    }

    public void setLegacyVersion(boolean legacyVersion)
    {
        isLegacyVersion = legacyVersion;
    }

    public String getCommitID()
    {
        return commitID;
    }

    public void setCommitID(String commitID)
    {
        this.commitID = commitID;
    }

    public String getKernelImagePath()
    {
        return kernelImagePath;
    }

    public void setKernelImagePath(String kernelImagePath)
    {
        this.kernelImagePath = kernelImagePath;
    }

    public List<String> getUserFiles()
    {
        return userFiles;
    }

    public void setUserFiles(List<String> userFiles)
    {
        this.userFiles = userFiles;
    }

    public boolean isValid()
    {
        return valid;
    }

    public void setValid(boolean valid)
    {
        this.valid = valid;
    }

    public void setDescriptorTypeVersionsFromSourceFiles(Set<SourceFile> sourceFilesWithDescriptorTypeVersions)
    {
        List<String> languageVersions = sourceFilesWithDescriptorTypeVersions.stream().map( SourceFile::getMetadata ).map( SourceFileMetadata::getTypeVersion )
                .filter( Objects::nonNull ).distinct().collect( Collectors.toList() );
        setDescriptorTypeVersions( languageVersions );
    }

    //The language versions for the version's descriptor files")
    private List<String> descriptorTypeVersions = new ArrayList<>();

    public List<String> getDescriptorTypeVersions()
    {
        return descriptorTypeVersions;
    }

    public void setDescriptorTypeVersions(final List<String> descriptorTypeVersions)
    {
        this.descriptorTypeVersions = descriptorTypeVersions;
    }

    @Override
    public int compareTo(WorkflowVersion that)
    {
        return ComparisonChain.start().compare( this.getLastModified(), that.getLastModified(), Ordering.natural().reverse().nullsLast() )
                .compare( this.getName(), that.getName(), Ordering.natural().nullsLast() ).compare( this.getReference(), that.getReference(), Ordering.natural().nullsLast() )
                .result();
    }

    public boolean isDirtyBit()
    {
        return dirtyBit;
    }

    public void setDirtyBit(boolean dirtyBit)
    {
        if( !this.isFrozen() )
        {
            this.dirtyBit = dirtyBit;
        }
    }

    public boolean isFrozen()
    {
        return frozen;
    }

    public ReferenceType getReferenceType()
    {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType)
    {
        this.referenceType = referenceType;
    }

    public String getReadMePath()
    {
        return readMePath;
    }

    public void setReadMePath(String readMePath)
    {
        this.readMePath = readMePath;
    }

    public enum ReferenceType
    {
        COMMIT ("commit"), 
        TAG("tag"), 
        BRANCH ("branch"), 
        NOT_APPLICABLE ("not applicable"), 
        UNSET("unset");
        
        private final String name;       

        private ReferenceType(String s) {
            name = s;
        }

        public boolean equalsName(String otherName) {
            // (otherName == null) check is not needed because name.equals(null) returns false 
            return name.equals(otherName);
        }

        public String toString() {
           return this.name;
        }
    }
}
