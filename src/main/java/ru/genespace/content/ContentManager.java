package ru.genespace.content;

public interface ContentManager
{
    public Object getFileContent(String fileName);

    public void setFileContent(String fileName, Object content);

}