import React, { useState, useEffect } from 'react';
import { registerPage, changeDocument, be5, fetchTableByUrl } from 'be5-react';
import { Field, FieldNotEmpty, createPageValueLocal } from '../utils';
import ReactMarkdown from 'react-markdown';

const ResourceTabPage = (props) => {
  const [markdown, setMarkdown] = useState("default content");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Извлекаем данные из props.value (структура page-компонента)
  const data = props.value?.data?.attributes || props.value?.data || {};
  
  console.log("data: ", data);
  
  const resource2versions = data.resource2versions;
  const filepath = data.readme;
  const title = data.PageTitle || "Resource";
  
  // Устанавливаем заголовок страницы
  useEffect(() => {
    be5.ui.setTitle(title);
  }, [title]);

  // Формируем URL для запроса контента
  const urlParams = `webserver/web/content?resource2versions=${resource2versions}&filepath=${encodeURIComponent(filepath || '')}&content=markdown`;

  useEffect(() => {
    let isMounted = true;

    const fetchContent = async () => {
      // Не загружаем, если нет обязательных параметров
      if (!resource2versions || !filepath) {
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        
        // Используем be5.be5ServerUrl для корректной работы в dev/prod
        const response = await fetch(`${urlParams}`);
        
        if (!response.ok) {
          throw new Error(`Ошибка HTTP: ${response.status}`);
        }

        const text = await response.text();
        
        if (isMounted) {
          setMarkdown(text);
          setError(null);
        }
      } catch (err) {
        if (isMounted) {
          setError(err.message);
          console.error('Ошибка загрузки markdown:', err);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    fetchContent();

    // Cleanup-функция для предотвращения утечек
    return () => {
      isMounted = false;
    };
  }, [urlParams]);

  return (
    <div className="container">
      <Field title='Название' value={data.Name} />
      <br />
      <Field title='Файл' value={data.filepath} />
      <br />
      <Field title='Описание' value={data.topic} />
      <br />
      <Field title='Создано' value={data.creationdate} />
      <Field title='Создал' value={data.whoinserted} />
      <FieldNotEmpty title='Изменено' value={data.whomodified} />
      <FieldNotEmpty title='Изменил' value={data.modificationdate} />
      
      <br />
      
      {loading && <div className="alert alert-info">Загрузка...</div>}
      
      {error && (
        <div className="alert alert-danger" role="alert">
          Ошибка: {error}
        </div>
      )}
      
      {!loading && !error && markdown && (
        <div className="border p-3 rounded bg-light">
          <ReactMarkdown>{markdown}</ReactMarkdown>
        </div>
      )}
    </div>
  );
};

// Route handler: вызывается при навигации по маршруту
registerPage('resourceTabPage', ResourceTabPage, (frontendParams, params) => {
    fetchTableByUrl("table/resources/ResourceTab/resID=" + params.resourceID + "/verID=" + params.versionID, json => {
        const cells = json.data.attributes.rows[0];
        //console.log("resourceTab params:", params);
  
          const data = {
            resource2versions: cells.resource2versions___.value,
            filepath: cells.filepath___.value,
            PageTitle: cells.PageTitle.value,
            Name: cells.Name.value,
            topic: cells.topic___.value,
            readme: cells.readme___.value,
            creationdate: cells.creationdate___.value,
            whoinserted: cells.whoinserted___.value,
            whomodified: cells.whomodified___.value,
            modificationdate: cells.modificationdate___.value
          };
          
          // Обновляем документ с новыми данными
          changeDocument(
            frontendParams.documentName,
            createPageValueLocal('resourceTabPage', data)
          );
  });
});

export default ResourceTabPage;