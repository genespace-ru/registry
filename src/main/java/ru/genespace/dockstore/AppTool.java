/*
 *    Copyright 2022 OICR, UCSC
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


public class AppTool extends Workflow {


    public static final String APPTOOL_DESCRIPTION = "This describes one app tool in dockstore as a special degenerate case of a workflow";
    public static final String OPENAPI_NAME = "AppTool";


    public AppTool createEmptyEntry() {
        return new AppTool();
    }



    public boolean isIsChecker() {
        return false;
    }

    public void setIsChecker(boolean isChecker) {
        if (!isChecker) {
            return;
        }
        throw new UnsupportedOperationException("AppTool cannot be a checker workflow");
    }

}
