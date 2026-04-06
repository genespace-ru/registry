/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ru.genespace.dockstore;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.Checksum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ru.genespace.misc.CustomLoggedException;

/**
 * This describes a cached copy of a remotely accessible file. Implementation specific.
 *
 * @author xliu
 */
public class SourceFile implements Comparable<SourceFile> {

    public static final EnumSet<DescriptorLanguage.FileType> TEST_FILE_TYPES = EnumSet.of(DescriptorLanguage.FileType.CWL_TEST_JSON, DescriptorLanguage.FileType.WDL_TEST_JSON, DescriptorLanguage.FileType.NEXTFLOW_TEST_PARAMS);
    public static final String SHA_TYPE = "SHA-256";
    private static Pattern pathRegex = null;
    private static String pathViolationMessage = null;

    private static final String PARENT_FIELD = "parent";
    /**
     * When serializing a SourceFile, don't serialize SourceFile.SourceFileMetadata.parent,
     * because the circular reference causes a StackOverflowError.
     */
    private static final ExclusionStrategy PARENT_FIELD_EXCLUSION_STRATEGY = new ExclusionStrategy()
    {
        @Override
        public boolean shouldSkipField(final FieldAttributes f)
        {
            return f.getName().equals( PARENT_FIELD );
        }

        @Override
        public boolean shouldSkipClass(final Class<?> clazz)
        {
            return false;
        }
    };
    /**
     * One Gson instance to rule them all. Thread-safe.
     */
    private static final Gson GSON = new GsonBuilder().setExclusionStrategies( PARENT_FIELD_EXCLUSION_STRATEGY ).create();

    private static final Logger LOG = LoggerFactory.getLogger(SourceFile.class);

    //Enumerates the type of file
    private DescriptorLanguage.FileType type;
    //
    private String content;
    //Path to sourcefile relative to its parent
    private String path;
    //
    //Absolute path of sourcefile in git repo
    private String absolutePath;

    //???When true, this version cannot be affected by refreshes to the content or updates to its metadata
    private boolean frozen = false;

    //The checksum(s) of the sourcefile's content
    private List<Checksum> checksums = new ArrayList<>();

    //Enumerates the file state
    private State state = State.COMPLETE;
    //
    private SourceFileMetadata metadata = new SourceFileMetadata();

    public SourceFile() {
        metadata.setParent(this);
    }

    /**
     * Creates a copy of the SourceFile. Not implemented as a copy constructor because you can't
     * ensure at compile time that all fields are copied unless they're all final.
     *
     * @param otherSourceFile
     */
    public static SourceFile copy(final SourceFile otherSourceFile) {
        //TODO: fix to remove gson dependency, (may be manual)
        final String json = GSON.toJson(otherSourceFile);
        final SourceFile sourceFile = GSON.fromJson(json, SourceFile.class);
        // Parent was not serialized, need to explicitly set it. See PARENT_FIELD_EXCLUSION_STRATEGY, above.
        sourceFile.getMetadata().setParent(sourceFile);
        return sourceFile;
    }

    public DescriptorLanguage.FileType getType() {
        return type;
    }

    public void setType(DescriptorLanguage.FileType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        checkPath(path);
        this.path = path;
    }

    public String getAbsolutePath() {
        if (absolutePath == null) {
            return null;
        }
        return Paths.get(absolutePath).normalize().toString();
    }

    public List<Checksum> getChecksums() {
        return checksums;
    }

    public void setChecksums(final List<Checksum> checksums) {
        this.checksums = checksums;
    }

    public void setAbsolutePath(String absolutePath) {
        // TODO: Figure out the actual absolute path before this workaround
        // FIXME: it looks like dockstore tool test_parameter --add and a number of other CLI commands depend on this now
        String modifiedPath = ZipSourceFileHelper.addLeadingSlashIfNecessary(absolutePath);
        checkPath(modifiedPath);
        this.absolutePath = modifiedPath;
        if (!this.absolutePath.equals(absolutePath)) {
            LOG.warn("Absolute path workaround used, this should be fixed at some point");
        }
    }

    /**
     * Determine if the specified path matches the sourcefile path.
     * The paths will match if, after adding a leading slash when it
     * is missing, the resulting strings are equal, case insensitive.
     * Thus, an absolute path and an absolute path missing the leading
     * slash will match, as will paths that are the same string,
     * whether absolute or relative.
     * Relates to https://ucsc-cgl.atlassian.net/browse/SEAB-5945
     */
    public boolean isSamePath(String otherPath) {
        return otherPath != null && addLeadingSlash(getPath()).equalsIgnoreCase(addLeadingSlash(otherPath));
    }

    private static String addLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    // removed overridden hashcode and equals, resulted in issue due to https://hibernate.atlassian.net/browse/HHH-3799

    @Override
    public int compareTo(SourceFile that)
    {
        if (this.absolutePath == null || that.absolutePath == null) {
            return ComparisonChain.start().compare(this.path, that.path).result();
        } else {
            return ComparisonChain.start().compare(this.absolutePath, that.absolutePath).result();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper( this ).add( "type", type ).add( "path", path ).add( "absolutePath", absolutePath ).add( "state", state ).toString();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public SourceFileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(final SourceFileMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Copy content/attributes from the specified SourceFile to this SourceFile,
     * except for "Hibernate-managed" fields such as the ID and creation/update dates.
     */
    public void updateFrom(SourceFile src) {
        setType(src.getType());
        setContent(src.getContent());
        setPath(src.getPath());
        setAbsolutePath(src.getAbsolutePath());
        setState(src.getState());
        getMetadata().setTypeVersion(src.getMetadata().getTypeVersion());
    }

    public SourceFile duplicate() {
        SourceFile file = new SourceFile();
        file.updateFrom(this);
        return file;
    }

    public static LimitedSourceFileBuilder.FirstStep limitedBuilder()
    {
        return new LimitedSourceFileBuilder().start();
    }

    private static synchronized void checkPath(String path)
    {
        if (path != null && pathRegex != null && !pathRegex.matcher(path).matches()) {
            throw new CustomLoggedException( pathViolationMessage );
        }
    }

    public static synchronized void restrictPaths(Pattern newPathRegex, String newPathViolationMessage) {
        pathRegex = newPathRegex;
        pathViolationMessage = newPathViolationMessage;
    }

    public static synchronized void unrestrictPaths() {
        pathRegex = null;
        pathViolationMessage = null;
    }

    public enum State {
        /**
         * The full file body is stored in the SourceFile's content field.
         */
        COMPLETE,
        /**
         * The file body is not stored.
         * The content field contains a message describing why the file's body is not stored.
         */
        NOT_STORED,
        /**
         * The file represents a stub.  The content field is null.
         */
        STUB
    }

    public class SourceFileMetadata
    {
        private String typeVersion;
        private SourceFile parent;

        public String getTypeVersion()
        {
            return typeVersion;
        }

        public void setTypeVersion(final String typeVersion)
        {
            this.typeVersion = typeVersion;
        }

        void setParent(final SourceFile parent)
        {
            this.parent = parent;
        }

    }
}
