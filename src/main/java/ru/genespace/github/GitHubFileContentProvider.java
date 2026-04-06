package ru.genespace.github;

public interface GitHubFileContentProvider
{
    public String getFileContent(String repositoryId, String reference, String fileName);
}
