/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ru.genespace.dockstore;

import com.google.common.primitives.Bytes;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build a SourceFile to which content limitations is applied. Require the file type, content, and paths.
 */
public class LimitedSourceFileBuilder {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final long MAXIMUM_FILE_SIZE = BYTES_PER_MEGABYTE;
    private static final long NOTEBOOK_MAXIMUM_FILE_SIZE = 3 * BYTES_PER_MEGABYTE;
    private static final Logger LOG = LoggerFactory.getLogger(LimitedSourceFileBuilder.class);

    private DescriptorLanguage.FileType type;
    private String content;
    private String path;
    private String absolutePath;

    public LimitedSourceFileBuilder() {
    }

    public FirstStep start() {
        return new FirstStep();
    }

    public class FirstStep extends TypeStep {
    }

    public class TypeStep {
        public ContentStep type(DescriptorLanguage.FileType newType) {
            type = newType;
            return new ContentStep();
        }
    }

    public class ContentStep {
        public PathStep content(String newContent) {
            content = newContent;
            return new PathStep();
        }
    }

    public class PathStep {
        public AbsolutePathStep path(String newPath) {
            path = newPath;
            return new AbsolutePathStep();
        }
        public BuildStep paths(String newPath) {
            return path(newPath).absolutePath(newPath);
        }
    }

    public class AbsolutePathStep {
        public BuildStep absolutePath(String newAbsolutePath) {
            absolutePath = newAbsolutePath;
            return new BuildStep();
        }
    }

    public class BuildStep {
        public SourceFile build() {
            SourceFile file = new SourceFile();
            file.setType(type);
            setContentWithLimits(file, content, absolutePath);
            file.setPath(path);
            file.setAbsolutePath(absolutePath);
            return file;
        }

        private void setContentWithLimits(SourceFile file, String content, String path)
        {
            // Set the properties to default values.
            file.setContent(content);
            file.setState(SourceFile.State.COMPLETE);
            // Check the content and override the above settings, if necessary.
            if (content == null) {
                file.setContent(null);
                file.setState(SourceFile.State.STUB);
                logContentAction(path, "stub");
                return;
            } else {
                byte[] bytes = content.getBytes( StandardCharsets.UTF_8 );
                //Binary file check, content is not stored by default
                if( Bytes.indexOf( bytes, Byte.decode( "0x00" ) ) != -1 )
                {
                    file.setContent( null );
                    file.setState( SourceFile.State.STUB );
                    logContentAction( path, "binary file (NUL bytes found)" );
                }
                long maximumSize = computeMaximumSize(path);
                if (bytes.length > maximumSize) {
                    // A large file is probably up to no good.
                    file.setContent( null );
                    file.setState(SourceFile.State.NOT_STORED);
                    logContentAction(path, "large file (%n bytes)".formatted(bytes.length));
                    return;
                }
            }
        }

        private void logContentAction(String path, String why)
        {
            LOG.info("incomplete content for file %s: %s".formatted(path, why));
        }

        private long computeMaximumSize(String path)
        {
            // Jupyter notebook files can contain embedded images, making them tend to be larger.
            if( path != null && path.endsWith( ".ipynb" ) )
            {
                return NOTEBOOK_MAXIMUM_FILE_SIZE;
            }
            return MAXIMUM_FILE_SIZE;
        }
    }
}
