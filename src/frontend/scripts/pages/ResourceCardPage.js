import React from 'react';
import { be5, changeDocument, fetchTableByUrl, Navs, registerPage} from 'be5-react';
import {createPageValueLocal} from "../utils";

const ResourceCardPage = (props) => {
  const {id, title, version} = props.value.data;
  
  console.log("props:")
  console.log(props);
  
  be5.ui.setTitle(title);
  //console.log("something to mark");
  
  const steps = [
    { title: "Версии", url: "#!table/versions/ForResourceCard/___resID=" + id +"/___verID=" + version},
    { title: "Версии_old", url: "#!table/versions/ForResourceCard/___resID=" + id  +"/___verID=" + version},
    { title: "Сценарий",   url: "#!table/resources/ResourceTab/___resID=" + id },
    { title: "Запуск", url: "#!table/resources/ToDo/___resID=" + id },
    { title: "Файлы", url: "#!table/resources/ToDo/___resID=" + id },
    { title: "Инструменты", url: "#!table/docker/ForResourceCard/___resID=" + id + "/___verID=" + version },
    { title: "DAG", url: "#!table/resources/ToDo/___resID=" + id },
    { title: "Метрики", url: "#!table/resources/ToDo/___resID=" + id },
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
    changeDocument(frontendParams.documentName,
      createPageValueLocal('resourceCardPage', cells.PageTitle.value, cells.ID.value, cells.versionID.value, )
    );
  });
});
