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


import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data about versions of a workflow/tool in Dockstore rather than about the original workflow.
 *
 * Stays modifiable even when the parent (version) becomes immutable via postgres security policies, allowing
 * us to modify things like verification status, DOIs, and whether a workflow version is hidden.
 *
 * Note that this entity is not directly serialized, instead individual fields are exposed in the Version
 * model.
 */
public class VersionMetadata {

    private static final String PUBLIC_ACCESSIBLE_DESCRIPTION = "Whether the version has everything needed to run without restricted access permissions";

    protected boolean verified;

    protected String verifiedSource;

    protected String verifiedPlatforms;

    protected String doiURL;

    //protected Map<DoiInitiator, Doi> dois = new HashMap<>();

    protected boolean hidden;

    // protected Version.DOIStatus doiStatus;

    protected  String description;

    protected DescriptionSource descriptionSource;

    protected WorkflowVersion parent;

    protected List<ParsedInformation> parsedInformationSet = new ArrayList<>();

    //protected Map<Long, OrcidPutCode> userIdToOrcidPutCode = new HashMap<>();

    private long id;

    private Timestamp dbCreateDate;

    private Timestamp dbUpdateDate;

    private Boolean publicAccessibleTestParameterFile;

    private List<String> descriptorTypeVersions = new ArrayList<>();

    private List<String> engineVersions = new ArrayList<>();

    private Timestamp latestMetricsSubmissionDate;

    private Timestamp latestMetricsAggregationDate;

    private boolean aiTopicProcessed = false;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public List<ParsedInformation> getParsedInformationSet() {
        return parsedInformationSet;
    }

    public void setParsedInformationSet(List<ParsedInformation> parsedInformationSet) {
        this.parsedInformationSet.clear();

        // Deserializer can call this method while parsedInformationSet is null, which causes a Null Pointer Exception
        // Adding a checker here to avoid a Null Pointer Exception caused by the deserializer
        if (parsedInformationSet != null) {
            this.parsedInformationSet.addAll(parsedInformationSet);
        }
    }

    //    public Map<Long, OrcidPutCode> getUserIdToOrcidPutCode() {
    //        return userIdToOrcidPutCode;
    //    }
    //
    //    public void setUserIdToOrcidPutCode(Map<Long, OrcidPutCode> userIdToOrcidPutCode) {
    //        this.userIdToOrcidPutCode = userIdToOrcidPutCode;
    //    }

    public String getDescription() {
        return description;
    }

    public Boolean getPublicAccessibleTestParameterFile() {
        return publicAccessibleTestParameterFile;
    }

    public void setPublicAccessibleTestParameterFile(Boolean publicAccessibleTestParameterFile) {
        this.publicAccessibleTestParameterFile = publicAccessibleTestParameterFile;
    }

    public List<String> getDescriptorTypeVersions() {
        return descriptorTypeVersions;
    }

    public void setDescriptorTypeVersions(final List<String> descriptorTypeVersions) {
        this.descriptorTypeVersions = descriptorTypeVersions;
    }

    public List<String> getEngineVersions() {
        return engineVersions;
    }

    public void setEngineVersions(final List<String> engineVersions) {
        this.engineVersions = engineVersions;
    }

    //    public Map<DoiInitiator, Doi> getDois() {
    //        return dois;
    //    }
    //
    //    public void setDois(Map<DoiInitiator, Doi> dois) {
    //        this.dois = dois;
    //    }

    public Timestamp getLatestMetricsSubmissionDate() {
        return latestMetricsSubmissionDate;
    }

    public void setLatestMetricsSubmissionDate(Timestamp latestMetricsSubmissionDate) {
        this.latestMetricsSubmissionDate = latestMetricsSubmissionDate;
    }

    public Timestamp getLatestMetricsAggregationDate() {
        return latestMetricsAggregationDate;
    }

    public void setLatestMetricsAggregationDate(Timestamp latestMetricsAggregationDate) {
        this.latestMetricsAggregationDate = latestMetricsAggregationDate;
    }

    public boolean isAiTopicProcessed() {
        return aiTopicProcessed;
    }

    public void setAiTopicProcessed(boolean aiTopicProcessed) {
        this.aiTopicProcessed = aiTopicProcessed;
    }
}
