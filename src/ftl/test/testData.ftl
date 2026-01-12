DELETE FROM repositories;
ALTER SEQUENCE repositories_id_seq RESTART WITH 1;
<#macro REPOSITORY url>
  INSERT INTO repositories(url) VALUES(${url?str});
</#macro>

DELETE FROM versions;
ALTER SEQUENCE versions_id_seq RESTART WITH 1;
<#macro VERSION repositoryId, name, commit, date, type> 
  INSERT INTO versions(repository, name, commit, dateModified, type) 
      VALUES( ${repositoryId}, ${name?str}, ${commit?str}, ${date?str}, ${type?str} );
</#macro>

DELETE FROM resources;
ALTER SEQUENCE resources_id_seq RESTART WITH 1;
<#macro RESOURCE repositoryId, name, type, language, descrPath, readMePath, topic, info>
  INSERT INTO resources(repository, name, type, language, primaryDescriptorPath, readMePath, topic, info) 
      VALUES( ${repositoryId}, ${name?str}, ${type?str}, ${language?str}, ${descrPath?str},
        <#if readMePath != null>${readMePath?str}<#else>null</#if>,
        <#if topic != null>${topic?str}<#else>null</#if>,
        <#if info != null>${info?str}<#else>null</#if>
      );
</#macro>


DELETE FROM resource2versions;
ALTER SEQUENCE resource2versions_id_seq RESTART WITH 1;
<#macro R2V resource, version, language, valid, doi, snapshot>
  INSERT INTO resource2versions(resource, version, language, valid, doi, snapshot) 
      VALUES( ${resource}, ${version}, ${language?str}, 
        <#if valid != null>${valid?str}<#else>null</#if>,
        <#if doi != null>${doi?str}<#else>null</#if>,
        <#if snapshot != null>${snapshot?str}<#else>null</#if>
      );
</#macro>


<@REPOSITORY "https://github.com/dockstore/bcc2020-training" /> 
<@VERSION 1, 'master', '81526c069d76fccb06202140512011481232c999', '2023-10-31 22:14', 'branch' /> 
<@RESOURCE 1, 'HelloWorld', 'workflow', 'WDL', 
     '/wdl-training/exercise1/HelloWorld.wdl', null, null, 
"{
  \"subclass\": \"WDL\",
  \"primaryDescriptorPath\": \"/wdl-training/exercise1/HelloWorld.wdl\",
  \"testParameterFiles\":
  [\"/wdl-training/exercise1/hello.json\"],
  \"name\": \"HelloWorld\",
  \"publish\": \"true\"
}" />
<@R2V 1, 1, 'WDL 1.0', 'yes', null, 'no' />


<@REPOSITORY "github.com/dockstore-testing/wes-testing" /> 
<@VERSION 2, 'v1.12', 'b4aa666d61d1e2bda4f79aca32d9e90737d77eff', '2022-03-12 01:19', 'tag' /> 
<@VERSION 2, 'main',  'ed90a626a8e7dc20fce400251a4e74a3c2c9d86a', '2022-03-14 18:05', 'branch' /> 
<@RESOURCE 2, 'agc-fastq-read-counts', 'workflow', 'WDL', 
     '/agc-examples/fastq/Dockstore.wdl', null, null, 
"{
  \"subclass\": \"WDL\",
  \"primaryDescriptorPath\": \"/agc-examples/fastq/Dockstore.wdl\",
  \"testParameterFiles\":
  [\"/agc-examples/fastq/input.json\"],
  \"name\": \"agc-fastq-read-counts\",
  \"publish\": \"true\"
}" />

<@R2V 2, 2, 'WDL 1.0', 'yes', '10.5281/zenodo.15605094', 'no' />
<@R2V 2, 3, 'WDL 1.0', 'yes', null, 'no' />
