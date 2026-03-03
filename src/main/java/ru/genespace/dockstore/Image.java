/*
 *    Copyright 2019 OICR
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

import java.util.ArrayList;
import java.util.List;

import ru.genespace.dockstore.languages.LanguageHandlerInterface.DockerSpecifier;

//Image(s) associated with tags and workflow versions

public class Image {
    //Implementation specific ID for the image in this webservice
    private long id;

    //Checksum(s) associated with this image
    private List<Checksum> checksums = new ArrayList<>();

    //Repository image belongs to
    private String repository;

    //Git tag
    private String tag;

    //Docker ID of the image
    private String imageID;

    //Registry the image belongs to
    private Registry imageRegistry;

    //Stores the architecture and, if available, the variant of an image. Separated by a / and only applicable to Docker Hub
    private String architecture;

    //"Stores the OS and, if available the OS version. Separated by a / and only applicable to Docker Hub
    private String os;

    //How the image is specified
    private DockerSpecifier specifier;

    //The size of the image in bytes
    private Long size;

    //The date the image was updated in the Docker repository
    private String imageUpdateDate;

    private String imageURL;

    public Image() {

    }

    public Image(List<Checksum> checksums, String repository, String tag, String imageID, Registry imageRegistry, Long size, String imageUpdateDate) {
        this.checksums = checksums;
        this.repository = repository;
        this.tag = tag;
        this.imageID = imageID;
        this.imageRegistry = imageRegistry;
        this.size = size;
        this.imageUpdateDate = imageUpdateDate;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getImageID() {
        return imageID;
    }

    public void setImageID(String imageID) {
        this.imageID = imageID;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getRepository() {
        return this.repository;
    }

    public void setChecksums(List<Checksum> checksums) {
        this.checksums = checksums;
    }

    public List<Checksum> getChecksums() {
        return this.checksums;
    }

    public Registry getImageRegistry() {
        return imageRegistry;
    }

    public void setImageRegistry(final Registry imageRegistry) {
        this.imageRegistry = imageRegistry;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(final String architecture) {
        this.architecture = architecture;
    }

    public String getOs() {
        return os;
    }

    public void setOs(final String os) {
        this.os = os;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getImageUpdateDate() {
        return imageUpdateDate;
    }

    public DockerSpecifier getSpecifier() {
        return specifier;
    }

    public void setSpecifier(DockerSpecifier specifier) {
        this.specifier = specifier;
    }

    public String getImageURL()
    {
        return imageURL;
    }

    public void setImageURL(String url)
    {
        imageURL = url;
    }
}
