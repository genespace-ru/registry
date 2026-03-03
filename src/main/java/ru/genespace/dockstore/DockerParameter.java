package ru.genespace.dockstore;

import ru.genespace.dockstore.languages.LanguageHandlerInterface.DockerImageReference;

public class DockerParameter
{
    public DockerParameter(String imageName, DockerImageReference imageReference)
    {
        this.imageName = imageName;
        this.imageReference = imageReference;
    }

    private String imageName;
    private DockerImageReference imageReference;

    public String imageName()
    {
        // TODO Auto-generated method stub
        return imageName;
    }

    public DockerImageReference imageReference()
    {
        return imageReference;
    }

}
