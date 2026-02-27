import React, { useState, useEffect } from 'react';
import { registerTableBox, processHashUrl, Navs } from 'be5-react';
import { Field, FieldNotEmpty } from './utils';
import ReactMarkdown from 'react-markdown';

const ResourceTabTableBox = ({ value }) => {
  const [markdown, setMarkdown] = useState("default content");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Безопасное извлечение данных
  const resource = value?.data?.attributes?.rows?.[0];
  
  if (!resource) {
    return <div className="container">Нет данных для отображения</div>;
  }

  const title = resource.PageTitle?.value || "AAA";
  be5.ui.setTitle(title);

  const urlParams = `webserver/web/content?repository=${resource.repository___.value}&version=${resource.version___.value}&filepath=${resource.readme___.value}&content=markdown`;

  useEffect(() => {
    let isMounted = true; // защита от обновления состояния после размонтирования

    const fetchContent = async () => {
      try {
        setLoading(true);
        const response = await fetch(urlParams);
        
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

    // Cleanup-функция
    return () => {
      isMounted = false;
    };
  }, [urlParams]); // Зависимость от urlParams для перезапроса при изменении

  return (
    <div className="container">
      <Field title='Название' value={resource.Name?.value} />
      <br />
      <Field title='Файл' value={resource.filepath___?.value} />
      <br />
      <Field title='Описание' value={resource.topic___?.value} />
      <br />
      <Field title='Создано' value={resource.creationdate___?.value} />
      <Field title='Создал' value={resource.whoinserted___?.value} />
      <FieldNotEmpty title='Изменено' value={resource.whomodified___?.value} />
      <FieldNotEmpty title='Изменил' value={resource.modificationdate___?.value} />

      {/* Опционально: iframe можно убрать, если дублирует контент */}
      {/* <iframe src={urlParams} id={resource.Name?.value} title="preview" /> */}
      
      <br />
      
      {loading && <div>Загрузка...</div>}
      
      {error && (
        <div style={{ color: 'red', margin: '10px 0' }}>
          Ошибка: {error}
        </div>
      )}
      
      {!loading && !error && (
        <ReactMarkdown>{markdown}</ReactMarkdown>
      )}
    </div>
  );
};

registerTableBox('resourceTab', ResourceTabTableBox);
export default ResourceTabTableBox;