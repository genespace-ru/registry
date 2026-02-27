import React, { useEffect } from 'react';
import { registerTableBox, processHashUrl, Navs } from 'be5-react';

const ResourceCardTableBox = ({ value }) => {
  const resource = value.data.attributes.rows[0];
  const title = resource.PageTitle.value;

  useEffect(() => {
    be5.ui.setTitle(title);
  }, [title]);

  const steps = [
    { title: "Версии", url: "#!table/versions/ForResourceCard/___resID=" + resource.ID.value  +"/___verID=" + resource.verver.value},
    { title: "Сценарий",   url: "#!table/resources/ResourceTab/___resID=" + resource.ID.value },
    { title: "Запуск", url: "#!table/resources/ToDo/___resID=" + resource.ID.value },
    { title: "Файлы", url: "#!table/resources/ToDo/___resID=" + resource.ID.value },
    { title: "Инструменты", url: "#!table/resources/ToDo/___resID=" + resource.ID.value },
    { title: "DAG", url: "#!table/resources/ToDo/___resID=" + resource.ID.value },
    { title: "Метрики", url: "#!table/resources/ToDo/___resID=" + resource.ID.value },
  ];

  return (
    <div className="repositoryInfo">
      <h1>{title}</h1>
      <Navs steps={steps} tabs startAtStep={0} />
    </div>
  );
};

registerTableBox('resourceCard', ResourceCardTableBox);

export default ResourceCardTableBox;
