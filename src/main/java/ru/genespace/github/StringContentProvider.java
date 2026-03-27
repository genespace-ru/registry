package ru.genespace.github;

public interface StringContentProvider
{
    public String getFileContent(String repositoryId, String reference, String fileName);
}
