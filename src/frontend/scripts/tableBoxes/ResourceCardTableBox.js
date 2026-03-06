import React, { useState } from 'react';
import { be5, registerTableBox, Navs } from 'be5-react';

const ResourceCardTableBox = ({ value }) => {
  const resource = value?.data?.attributes?.rows?.[0];
  
  if (!resource) {
    return <div className="repositoryInfo">Нет данных для отображения</div>;
  }

  const title = resource.PageTitle?.value || 'Без названия';
  be5.ui.setTitle(title);
  
  const resourceId = resource.ID?.value;
  const versionId = resource.versionID?.value;

  // 1. Состояние для хеша (триггер для обновления)
  const [currentHash, setCurrentHash] = useState(window.location.hash);

  // 2. Базовая конфигурация шагов
  const baseSteps = [
    { title: "Версии",     url: `#!table/versions/ForResourceCard/___resID=${resourceId}/___verID=${versionId}` },
    { title: "Версии_old", url: `#!table/versions/ForResourceCard/___resID=${resourceId}/___verID=${versionId}` },
    { title: "Сценарий",   url: `#!table/resources/ResourceTab/___resID=${resourceId}/___verID=${versionId}` },
    { title: "Запуск",     url: `#!table/resources/ToDo/___resID=${resourceId}/___verID=${versionId}` },
    { title: "Файлы",      url: `#!table/resources/ToDo/___resID=${resourceId}/___verID=${versionId}` },
    { title: "Инструменты",url: `#!table/docker/ForResourceCard/___resID=${resourceId}` },
    { title: "DAG",        url: `#!table/resources/ToDo/___resID=${resourceId}` },
    { title: "Метрики",    url: `#!table/resources/ToDo/___resID=${resourceId}` },
  ];

  return (
    <div className="repositoryInfo">
      <h1>{title}</h1>
      
      {/* Передаем steps и активный индекс */}
      <Navs steps={baseSteps} tabs startAtStep={0} />
      
    </div>
  );
};

registerTableBox('resourceCard', ResourceCardTableBox);

export default ResourceCardTableBox;