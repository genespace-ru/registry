import React from 'react';
import { be5, changeDocument, registerPage} from 'be5-react';
import {createPageValueLocal} from "../utils";

const ResourceDAGPage = (props) => {
  const data = props.value.data;
  const id = data.id;
  const version = data.version;
  const isValid = data.valid;
  const imageUrl = `webserver/web/dag?resource=${id}&version=${version}`;
    if(isValid == 'yes')
        return (
          <div className="container">
                <img src={imageUrl}/>
          </div>
        );
    else
        return (
            <div className="container">
                Resource is not valid, can not generate DAG.
          </div>
        );
};


registerPage('resourceDAGPage', ResourceDAGPage, (frontendParams, params) => {
    console.log("params");
    console.log(params);
    const data = {
        id: params.ID,
        version: params.versionID,
        valid: params.isValid
      };
    changeDocument(frontendParams.documentName,
        createPageValueLocal('resourceDAGPage', data )
    );
});
