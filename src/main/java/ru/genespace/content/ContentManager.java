package ru.genespace.content;

import java.awt.image.BufferedImage;

public interface ContentManager
{
    public String getFileContentText(String fileName);

    public BufferedImage getFileContentImage(String fileName);

    public void setFileContentText(String fileName, String fileText);

    public void setFileContentImage(String fileName, BufferedImage image);
}