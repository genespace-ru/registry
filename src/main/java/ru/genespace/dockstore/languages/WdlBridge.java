package ru.genespace.dockstore.languages;

import java.io.File;
import java.io.StringReader;
import java.util.Map;

import biouml.plugins.wdl.diagram.WDLImporter;
import biouml.plugins.wdl.parser.AstStart;
import biouml.plugins.wdl.parser.WDLParser;
import ru.biosoft.util.ApplicationUtils;

//This is STUB class for WDL validation. 
//TODO: Use biouml.plugins.wdl reading and validation in auto-generated methods

public class WdlBridge
{

    //Map of path - content of secondary files, may be needed when parsing WDL
    private Map<String, String> secondaryFiles;
    private String version = null;

    public WdlBridge()
    {

    }
    public void setSecondaryFiles(Map<String, String> secondaryFiles)
    {
        this.secondaryFiles = secondaryFiles;
    }

    public void validateTool(String absolutePath, String primaryDescriptorFilePath)
    {
        // TODO Auto-generated method stub

    }

    public void validateWorkflow(String absolutePath, String absolutePath2) throws Exception
    {
        File wdlFile = new File( absolutePath );
        String content = WDLImporter.processContent( ApplicationUtils.readAsString( wdlFile ) );
        WDLParser parser = new WDLParser();
        AstStart start = parser.parse( new StringReader( content ) );
        version = parser.getVersion();
    }

    //    public boolean isVersionFieldValid(String absolutePath, String primaryDescriptorPath)
    //    {
    //        return true;
    //    }

    //    public Optional<String> getFirstCodeLine(String descriptorContent)
    //    {
    //
    //        return null;
    //    }

}
