import React from 'react';
import { be5, changeDocument, fetchTableByUrl, Navs, registerPage} from 'be5-react';
import {createPageValueLocal} from "../utils";

const ResourceCardPage = (props) => {
  const {id, title, version, isValid, resource2versions, fileName} = props.value.data;
  const filepath = encodeURIComponent(fileName)
  
  console.log("props:")
  console.log(props);
  
  be5.ui.setTitle(title);
  //console.log("something to mark");
  
  const steps = [
    { title: "Версии", url: "#!table/versions/ForResourceCard/___resID=" + id +"/___verID=" + version},
    { title: "Сценарий",   url: "#!resourceTabPage/resourceID=" + id +"/versionID=" + version},
    //{ title: "Запуск", url: "#!table/resources/ToDo/___resID=" + id },
    { title: "Файлы", url: "#!resourceFilePage/ID=" + id + "/resource2versions=" + resource2versions + "/filepath=" + filepath },
    { title: "Инструменты", url: "#!table/docker/ForResourceCard/___resID=" + id + "/___verID=" + version },
    { title: "DAG", url: "#!resourceDAGPage/ID=" + id + "/versionID=" + version + "/isValid=" + isValid},
    //{ title: "Метрики", url: "#!table/resources/ToDo/___resID=" + id },
  ];

  return (
    <div className="repositoryInfo">
      <h1>{title}</h1>
      <Navs steps={steps} tabs startAtStep={0} key={title}/>
    </div>
  );
};


registerPage('resourceCardPage', ResourceCardPage, (frontendParams, params) => {
  fetchTableByUrl("table/resources/ResourceCardInfo/ID=" + params.ID + "/versionID=" + params.versionID, json => {
    const cells = json.data.attributes.rows[0];
    const data = {
        id: cells.ID.value,
        title: cells.PageTitle.value,
        version: cells.versionID.value,
        isValid: cells.isValid.value,
        resource2versions: cells.resource2versions.value,
        fileName: cells.fileName.value
      };
    changeDocument(frontendParams.documentName,
      createPageValueLocal('resourceCardPage', data )
    );
  });
});
