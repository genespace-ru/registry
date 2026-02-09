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


<@REPOSITORY "https://github.com/genespace-workflows/general" /> 
<@VERSION 1, 'master', '557f1fa338dd8e8d69ca85ced7e10a226a5dccec', '2026-02-10', 'branch' /> 
<@RESOURCE 1, 'Generic quality control', 'workflow', 'NFL', 
     '/fastqc.nf', null, 
     'Общий сценарий контроля качества',
"{
  \"subclass\": \"NFL\",
  \"primaryDescriptorPath\": \"/fastqc.nf\",
  \"name\": \"Generic quality control\",
  \"publish\": \"true\"
}" />
<@R2V 1, 1, 'Nextflow 23.0', 'yes', null, 'no' />

<@REPOSITORY "https://github.com/genespace-workflows/snv-calling" /> 
<@VERSION 2, 'master', 'b25f98660a672a4479174617b0a1887b1d3b5346', '2026-01-21', 'branch' /> 
<@RESOURCE 2, 'SNV and SV calling Pipeline', 'workflow', 'NFL', 
     '/main.nf', null, 
     'A comprehensive Nextflow-based workflow for processing and analyzing Whole Exome Sequencing (WES) data from NGS platforms.',
"{
  \"subclass\": \"NFL\",
  \"primaryDescriptorPath\": \"/main.nf\",
  \"name\": \"SNV and SV calling Pipeline\",
  \"publish\": \"true\"
}" />
<@R2V 2, 2, 'Nextflow 23.0', 'yes', null, 'no' />

<@REPOSITORY "https://github.com/genespace-workflows/metagenomics" /> 
<@VERSION 3, 'master', '126ad9cb9efd02b3923b57708575b0e5075a0629', '2026-01-22', 'branch' /> 
<@RESOURCE 3,'Metagenome Analysis Pipeline', 'workflow', 'NFL', 
     '/metagenomics_pipeline/main.nf', null, 
     'This workflow integrates taxonomic classification, antimicrobial resistance (AMR) detection, and toxin profiling.',
"{
  \"subclass\": \"NFL\",
  \"primaryDescriptorPath\": \"/metagenomics_pipeline/main.nf\",
  \"name\": \"Metagenome Analysis Pipeline\",
  \"publish\": \"true\"
}" />
<@R2V 3, 3, 'Nextflow 23.0', 'yes', null, 'no' />

<@REPOSITORY "https://github.com/genespace-workflows/sc_analysis" /> 
<@VERSION 4, 'master', '165df6c55a0279aa7b8e1b73d17f506d16abc246', '2026-01-24', 'branch' /> 
<@RESOURCE 4,'Single-Cell RNA-seq Analysis Pipeline', 'workflow', 'NFL', 
     '/main.nf', null, 
     'A Nextflow-based workflow for processing and analysis of 10x Genomics single-cell RNA-seq data.',
"{
  \"subclass\": \"NFL\",
  \"primaryDescriptorPath\": \"/main.nf\",
  \"name\": \"Single-Cell RNA-seq Analysis Pipeline\",
  \"publish\": \"true\"
}" />
<@R2V 4, 4, 'Nextflow 23.0', 'yes', null, 'no' />

<@REPOSITORY "https://github.com/genespace-workflows/chip-seq" /> 
<@VERSION 5, 'master', '8fbb666c46e60040f9864abc7619414e5038a577', '2026-02-09', 'branch' /> 
<@RESOURCE 5,'ChIP-seq Analysis Pipeline', 'workflow', 'NFL', 
     '/main.nf', null, 
     'A workflow for preprocessing, quality control, single-nucleosome peak calling, and differential enrichment analysis of single-end ChIP-seq data.',
"{
  \"subclass\": \"NFL\",
  \"primaryDescriptorPath\": \"/main.nf\",
  \"name\": \"ChIP-seq Analysis Pipeline\",
  \"publish\": \"true\"
}" />
<@R2V 5, 5, 'Nextflow 23.0', 'yes', null, 'no' />
